package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.DuplicateCapacityPolicyException;
import com.thinklab.domain.exception.InvalidCapacityPolicyStatusException;
import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.CapacityPolicy.Metric;
import com.thinklab.domain.model.CapacityPolicy.PolicyStatus;
import com.thinklab.domain.model.CapacitySample;
import com.thinklab.infrastructure.adapter.out.persistence.entity.CapacityPolicyDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.CapacityPolicyDocument.CapacityPolicyPersistenceMapper;
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
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CapacityPolicyMongoAdapterTest {

    private static final String URI = "mongodb://localhost:27017/cap_test";

    private final UUID org = UUID.randomUUID();
    private MongoClient client;
    private MongoCollection<CapacityPolicyDocument> collection;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("cap_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_capacity_registry_db")).thenReturn(database);
        when(database.getCollection("capacity_policies", CapacityPolicyDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
    }

    private CapacityPolicy policy() {
        CapacityPolicy policy = CapacityPolicy.createNew(UUID.randomUUID(), org, "Orders disk", UUID.randomUUID(), Metric.DISK, 100, 70, 90, 30, 30, 24, "op");
        policy.pause("op");
        return policy;
    }

    private CapacitySample sample(Instant measuredAt) {
        return CapacitySample.createNew(UUID.randomUUID(), org, UUID.randomUUID(), 61.5, measuredAt, "tool", measuredAt);
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private void finds(CapacityPolicy found) {
        FindPublisher<CapacityPolicyDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<CapacityPolicyDocument> subscriber = invocation.getArgument(0);
            Flux.just(CapacityPolicyPersistenceMapper.toDocument(found)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    @Test
    @DisplayName("a policy survives the round trip, with its promise, its latest sample and its audit trail")
    void roundTrip() {
        CapacityPolicy policy = policy();
        Instant at = Instant.parse("2026-10-18T10:00:00Z");
        CapacityPolicy withSample = CapacityPolicy.reconstitute(policy.getId(), org, policy.getName(), policy.getAssetId(), Metric.MEMORY, 64.5, 50, 80, 14, 60, 12, PolicyStatus.PAUSED,
                at, at, 33.25, at, policy.getAuditTrail());

        CapacityPolicyDocument document = CapacityPolicyPersistenceMapper.toDocument(withSample);
        CapacityPolicy back = CapacityPolicyPersistenceMapper.toDomain(document);

        assertEquals(PolicyStatus.PAUSED, back.getStatus());
        assertEquals(Metric.MEMORY, back.getMetric());
        assertEquals(64.5, back.getLimit());
        assertEquals(50, back.getWarnPercent());
        assertEquals(80, back.getCriticalPercent());
        assertEquals(14, back.getWindowDays());
        assertEquals(60, back.getHorizonDays());
        assertEquals(12, back.getReportEveryHours());
        assertEquals(33.25, back.getLastValue());
        assertEquals(at, back.getLastMeasuredAt());
        assertEquals(2, back.getAuditTrail().size());
        assertNull(back.getAuditTrail().get(0).fromStatus());
        assertEquals(PolicyStatus.ACTIVE, back.getAuditTrail().get(1).fromStatus());
        assertEquals("Orders disk", document.getName());
        assertEquals("PAUSED", document.getStatus());
        assertEquals("MEMORY", document.getMetric());
        assertEquals(org, document.getOrganisationId());
        assertEquals(policy.getId(), document.getId());
        assertEquals(policy.getAssetId(), document.getAssetId());
        assertEquals(at, document.getCreatedAt());
        assertEquals(at, document.getUpdatedAt());
        assertEquals(2, document.getAuditTrail().size());
    }

    @Test
    @DisplayName("the document setters are plain, and the persistence mapper is a utility class")
    void plainDocument() throws ReflectiveOperationException {
        CapacityPolicyDocument document = new CapacityPolicyDocument();
        document.setLastValue(null);
        document.setLastMeasuredAt(null);
        document.setAuditTrail(new ArrayList<>());
        assertNull(document.getLastValue());
        assertNull(document.getLastMeasuredAt());
        assertTrue(document.getAuditTrail().isEmpty());

        Constructor<CapacityPolicyPersistenceMapper> constructor = CapacityPolicyPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("create and the lookups are scoped to the organisation, the list oldest first")
    void lookups() {
        CapacityPolicy policy = policy();
        when(collection.insertOne(any(CapacityPolicyDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(policy);
        CapacityPolicyMongoRepositoryAdapter adapter = new CapacityPolicyMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(policy)).expectNext(policy).verifyComplete();
        StepVerifier.create(adapter.findById(policy.getId(), org)).assertNext(found -> assertEquals(policy.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findAll(org)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId"));
    }

    @Test
    @DisplayName("a duplicate name is a 409 of the domain, on create or on a rename; any other write error passes through; the URI may omit the database")
    void duplicates() {
        CapacityPolicy policy = policy();
        when(collection.insertOne(any(CapacityPolicyDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)));
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(2)));
        CapacityPolicyMongoRepositoryAdapter adapter = new CapacityPolicyMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.create(policy)).expectError(DuplicateCapacityPolicyException.class).verify();
        StepVerifier.create(adapter.create(policy)).expectError(MongoWriteException.class).verify();
        StepVerifier.create(adapter.save(policy, PolicyStatus.ACTIVE, policy.getAuditTrail().get(1))).expectError(DuplicateCapacityPolicyException.class).verify();
        StepVerifier.create(adapter.save(policy, PolicyStatus.ACTIVE, policy.getAuditTrail().get(1))).expectError(MongoWriteException.class).verify();
        assertThrows(NullPointerException.class, () -> new CapacityPolicyMongoRepositoryAdapter(client, null));
    }

    @Test
    @DisplayName("save is guarded by the status loaded and pushes the audit entry; a lost race is a conflict")
    void save() {
        CapacityPolicy policy = policy();
        when(collection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        CapacityPolicyMongoRepositoryAdapter adapter = new CapacityPolicyMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.save(policy, PolicyStatus.ACTIVE, policy.getAuditTrail().get(1))).verifyComplete();
        StepVerifier.create(adapter.save(policy, PolicyStatus.ACTIVE, policy.getAuditTrail().get(1))).expectError(InvalidCapacityPolicyStatusException.class).verify();

        ArgumentCaptor<Bson> guard = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).updateOne(guard.capture(), update.capture());
        assertTrue(guard.getAllValues().get(0).toString().contains("ACTIVE") && guard.getAllValues().get(0).toString().contains("organisationId"));
        assertTrue(update.getAllValues().get(0).toString().contains("auditTrail"));
    }

    @Test
    @DisplayName("save writes the whole definition next to the status and the audit entry")
    void saveWritesTheDefinition() {
        CapacityPolicy policy = policy();
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        CapacityPolicyMongoRepositoryAdapter adapter = new CapacityPolicyMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.save(policy, PolicyStatus.ACTIVE, policy.getAuditTrail().get(1))).verifyComplete();

        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(any(Bson.class), update.capture());
        String text = update.getValue().toString();
        for (String field : new String[]{"metric", "limit", "warnPercent", "criticalPercent", "windowDays", "horizonDays", "reportEveryHours", "assetId", "name"}) {
            assertTrue(text.contains(field), field);
        }
        assertFalse(text.contains("lastValue"));
    }

    @Test
    @DisplayName("a sample moves the latest one forward only when it is newer than the one kept (or none is kept)")
    void recordSample() {
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        CapacityPolicyMongoRepositoryAdapter adapter = new CapacityPolicyMongoRepositoryAdapter(client, URI);
        UUID policyId = UUID.randomUUID();
        CapacitySample sample = sample(Instant.parse("2026-10-18T10:00:00Z"));

        StepVerifier.create(adapter.recordSample(policyId, org, sample)).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection).updateOne(filter.capture(), update.capture());
        String guard = filter.getValue().toString();
        assertTrue(guard.contains("organisationId") && guard.contains("lastMeasuredAt") && guard.contains("$lt"));
        String set = update.getValue().toString();
        assertTrue(set.contains("lastMeasuredAt") && set.contains("lastValue") && set.contains("61.5"));
    }
}
