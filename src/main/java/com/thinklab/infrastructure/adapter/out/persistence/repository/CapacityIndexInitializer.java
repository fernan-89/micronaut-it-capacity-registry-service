package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Creates the indexes at startup. The UNIQUE ones are the atomic backstops (ADR-033): a policy name per organisation, and one sample per
 * {@code (organisation, policy, moment)} (ADR-030: the same report twice is the same sample). The sample list index serves the real query, the
 * newest samples of a policy, and a TTL index expires samples after {@code thinklab.capacity-registry.sample-retention-days} (default 400, ADR-033). Fail-open: {@code createIndex} is idempotent, a failure is logged and the application still starts. Turn it off with
 * {@code thinklab.mongo.create-indexes=false}.
 */
@Singleton
@Requires(property = "thinklab.mongo.create-indexes", notEquals = "false")
public class CapacityIndexInitializer implements ApplicationEventListener<StartupEvent> {

    static final String POLICY_NAME_INDEX = "organisationId_1_name_1";
    static final String SAMPLE_KEY_INDEX = "organisationId_1_policyId_1_measuredAt_1";
    static final String SAMPLE_LIST_INDEX = "organisationId_1_policyId_1_measuredAt_-1";
    static final String SAMPLE_EXPIRY_INDEX = "measuredAt_1";

    private static final Logger log = LoggerFactory.getLogger(CapacityIndexInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MongoClient mongoClient;
    private final String database;
    private final Duration timeout;
    private final long retentionDays;

    @Inject
    public CapacityIndexInitializer(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri,
                                    @Property(name = "thinklab.capacity-registry.sample-retention-days", defaultValue = "400") long retentionDays) {
        this(mongoClient, mongoUri, TIMEOUT, retentionDays);
    }

    CapacityIndexInitializer(MongoClient mongoClient, String mongoUri, Duration timeout, long retentionDays) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "Infrastructure constraint violated: MongoClient cannot be null.");
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : CapacityPolicyMongoRepositoryAdapter.DEFAULT_DATABASE;
        this.timeout = timeout;
        this.retentionDays = retentionDays;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        ensureIndex(CapacityPolicyMongoRepositoryAdapter.COLLECTION_NAME, POLICY_NAME_INDEX, new Document("organisationId", 1).append("name", 1), new IndexOptions().unique(true));
        String samples = CapacitySampleMongoRepositoryAdapter.COLLECTION_NAME;
        ensureIndex(samples, SAMPLE_KEY_INDEX, new Document("organisationId", 1).append("policyId", 1).append("measuredAt", 1), new IndexOptions().unique(true));
        ensureIndex(samples, SAMPLE_LIST_INDEX, new Document("organisationId", 1).append("policyId", 1).append("measuredAt", -1), new IndexOptions());
        ensureIndex(samples, SAMPLE_EXPIRY_INDEX, new Document("measuredAt", 1), new IndexOptions().expireAfter(retentionDays, TimeUnit.DAYS));
    }

    private void ensureIndex(String collection, String indexName, Document keys, IndexOptions options) {
        try {
            Mono.from(mongoClient.getDatabase(database).getCollection(collection).createIndex(keys, options.name(indexName))).block(timeout);
            log.info("[MONGO_INDEXES] Ensured index [{}] on [{}.{}]", indexName, database, collection);
        } catch (MongoTimeoutException e) {
            log.error("[MONGO_INDEXES] MongoDB unreachable; index [{}] was not created. Reason: {}", indexName, e.getMessage());
        } catch (RuntimeException e) {
            log.error("[MONGO_INDEXES] Could not create index [{}] on [{}.{}]: {}", indexName, database, collection, e.getMessage());
        }
    }
}
