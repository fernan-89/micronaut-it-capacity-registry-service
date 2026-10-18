package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.model.CapacitySample;
import com.thinklab.domain.repository.CapacitySampleRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.CapacitySampleDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.CapacitySampleDocument.CapacitySamplePersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the CapacitySample aggregate, raw reactive-streams driver. Samples are only ever inserted. The unique
 * {@code (organisationId, policyId, measuredAt)} index is what makes a repeated report the same sample: the loser of an insert reads the
 * stored one (ADR-030).
 */
@Singleton
public class CapacitySampleMongoRepositoryAdapter implements CapacitySampleRepository {

    static final String COLLECTION_NAME = "capacity_samples";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";
    private static final String FIELD_POLICY = "policyId";
    private static final String FIELD_MEASURED_AT = "measuredAt";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public CapacitySampleMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : CapacityPolicyMongoRepositoryAdapter.DEFAULT_DATABASE;
    }

    private MongoCollection<CapacitySampleDocument> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME, CapacitySampleDocument.class).withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Stored> create(CapacitySample sample) {
        return Mono.from(getCollection().insertOne(CapacitySamplePersistenceMapper.toDocument(sample)))
                .map(result -> new Stored(sample, true))
                .onErrorResume(MongoWriteException.class, error -> error.getError().getCode() == CapacityPolicyMongoRepositoryAdapter.DUPLICATE_KEY
                        ? findByNaturalKey(sample.getOrganisationId(), sample.getPolicyId(), sample.getMeasuredAt()).map(existing -> new Stored(existing, false))
                        : Mono.error(error));
    }

    @Override
    public Mono<CapacitySample> findByNaturalKey(UUID organisationId, UUID policyId, Instant measuredAt) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ORGANISATION, organisationId), Filters.eq(FIELD_POLICY, policyId),
                        Filters.eq(FIELD_MEASURED_AT, measuredAt))).first())
                .map(CapacitySamplePersistenceMapper::toDomain);
    }

    @Override
    public Mono<CapacitySample> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(CapacitySamplePersistenceMapper::toDomain);
    }

    @Override
    public Flux<CapacitySample> findAll(UUID organisationId, UUID policyId, int limit) {
        Bson filter = policyId == null ? Filters.eq(FIELD_ORGANISATION, organisationId)
                : Filters.and(Filters.eq(FIELD_ORGANISATION, organisationId), Filters.eq(FIELD_POLICY, policyId));
        return Flux.from(getCollection().find(filter).sort(Sorts.descending(FIELD_MEASURED_AT)).limit(limit)).map(CapacitySamplePersistenceMapper::toDomain);
    }

    @Override
    public Flux<CapacitySample> findSince(UUID organisationId, UUID policyId, Instant since) {
        Bson filter = Filters.and(Filters.eq(FIELD_ORGANISATION, organisationId), Filters.eq(FIELD_POLICY, policyId), Filters.gte(FIELD_MEASURED_AT, since));
        return Flux.from(getCollection().find(filter).sort(Sorts.descending(FIELD_MEASURED_AT)).limit(MAX_TREND_POINTS))
                .map(CapacitySamplePersistenceMapper::toDomain)
                .collectList()
                .flatMapMany(newestFirst -> {
                    List<CapacitySample> oldestFirst = newestFirst.reversed();
                    return Flux.fromIterable(oldestFirst);
                });
    }
}
