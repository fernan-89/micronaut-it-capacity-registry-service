package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.model.CapacitySample;
import com.thinklab.domain.repository.CapacitySampleRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.CapacitySampleDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.CapacitySampleDocument.CapacitySamplePersistenceMapper;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CapacitySampleMongoAdapterTest {

    private static final String URI = "mongodb://localhost:27017/cap_test";

    private final UUID org = UUID.randomUUID();
    private MongoClient client;
    private MongoCollection<CapacitySampleDocument> collection;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("cap_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_capacity_registry_db")).thenReturn(database);
        when(database.getCollection("capacity_samples", CapacitySampleDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
    }

    private CapacitySample sample(double value, Instant measuredAt) {
        return CapacitySample.createNew(UUID.randomUUID(), org, UUID.randomUUID(), value, measuredAt, "tool", measuredAt);
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private FindPublisher<CapacitySampleDocument> finds(CapacitySample... found) {
        FindPublisher<CapacitySampleDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        when(publisher.limit(anyInt())).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<CapacitySampleDocument> subscriber = invocation.getArgument(0);
            Flux.fromArray(found).map(CapacitySamplePersistenceMapper::toDocument).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
        return publisher;
    }

    @Test
    @DisplayName("a sample survives the round trip, with its value, its moment and who reported it")
    void roundTrip() {
        CapacitySample sample = sample(42.5, Instant.now());
        CapacitySampleDocument document = CapacitySamplePersistenceMapper.toDocument(sample);
        CapacitySample back = CapacitySamplePersistenceMapper.toDomain(document);

        assertEquals(42.5, back.getValue());
        assertEquals(sample.getMeasuredAt(), back.getMeasuredAt());
        assertEquals("tool", back.getReportedBy());
        assertEquals(sample.getReportedAt(), back.getReportedAt());
        assertEquals(org, document.getOrganisationId());
        assertEquals(sample.getPolicyId(), document.getPolicyId());
        assertEquals(sample.getId(), document.getId());
        assertEquals(42.5, document.getValue());
    }

    @Test
    @DisplayName("the persistence mapper is a utility class")
    void mapperIsUtility() throws ReflectiveOperationException {
        Constructor<CapacitySamplePersistenceMapper> constructor = CapacitySamplePersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("create answers the sample as created; the lookups are scoped to the organisation; the URI may omit the database and must not be null")
    void lookups() {
        CapacitySample sample = sample(1, Instant.now());
        when(collection.insertOne(any(CapacitySampleDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(sample);
        CapacitySampleMongoRepositoryAdapter adapter = new CapacitySampleMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.create(sample)).assertNext(stored -> assertTrue(stored.created())).verifyComplete();
        StepVerifier.create(adapter.findById(sample.getId(), org)).assertNext(found -> assertEquals(sample.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findByNaturalKey(org, sample.getPolicyId(), sample.getMeasuredAt())).expectNextCount(1).verifyComplete();
        assertThrows(NullPointerException.class, () -> new CapacitySampleMongoRepositoryAdapter(client, null));
    }

    @Test
    @DisplayName("a duplicate key means the sample was already reported: the stored one comes back, not created; any other error passes through")
    void duplicate() {
        CapacitySample sample = sample(1, Instant.now());
        when(collection.insertOne(any(CapacitySampleDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)));
        finds(sample);
        CapacitySampleMongoRepositoryAdapter adapter = new CapacitySampleMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(sample)).assertNext(stored -> {
            assertFalse(stored.created());
            assertEquals(sample.getId(), stored.sample().getId());
        }).verifyComplete();
        StepVerifier.create(adapter.create(sample)).expectError(MongoWriteException.class).verify();
    }

    @Test
    @DisplayName("the list is scoped to the organisation, to the policy when one is given, newest first and limited")
    void list() {
        CapacitySample sample = sample(1, Instant.now());
        FindPublisher<CapacitySampleDocument> publisher = finds(sample);
        CapacitySampleMongoRepositoryAdapter adapter = new CapacitySampleMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.findAll(org, null, 5)).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAll(org, sample.getPolicyId(), 7)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId") && !filter.getAllValues().get(0).toString().contains("policyId"));
        assertTrue(filter.getAllValues().get(1).toString().contains("policyId"));
        verify(publisher).limit(5);
        verify(publisher).limit(7);
    }

    @Test
    @DisplayName("the trend window reads the newest samples since a moment, at most the trend maximum, and answers them oldest first")
    void since() {
        Instant base = Instant.parse("2026-10-18T10:00:00Z");
        CapacitySample newer = sample(2, base.plusSeconds(60));
        CapacitySample older = sample(1, base);
        FindPublisher<CapacitySampleDocument> publisher = finds(newer, older);
        CapacitySampleMongoRepositoryAdapter adapter = new CapacitySampleMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.findSince(org, newer.getPolicyId(), base.minusSeconds(10)).map(CapacitySample::getValue)).expectNext(1.0, 2.0).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection).find(filter.capture());
        String text = filter.getValue().toString();
        assertTrue(text.contains("policyId") && text.contains("measuredAt") && text.contains("organisationId"));
        verify(publisher).limit(CapacitySampleRepository.MAX_TREND_POINTS);
    }
}
