package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.DuplicateCapacityPolicyException;
import com.thinklab.domain.exception.InvalidCapacityPolicyStatusException;
import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.CapacityPolicy.Metric;
import com.thinklab.domain.model.CapacityPolicy.PolicyStatus;
import com.thinklab.domain.model.CapacitySample;
import com.thinklab.domain.repository.CapacityPolicyRepository;
import com.thinklab.domain.repository.CapacitySampleRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The capacity registry persistence against a real MongoDB: the unique policy name, the unique sample key that makes a repeated or simultaneous
 * report the same sample, the latest sample that only moves forward in time whatever the order of the reports, the trend window read oldest
 * first, the status-guarded save, and the indexes created at startup (the TTL one included).
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CapacityPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "capacity_registry_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject CapacityPolicyRepository policies;
    @Inject CapacitySampleRepository samples;
    @Inject MongoClient mongoClient;

    private CapacityPolicy newPolicy(UUID organisation, String name) {
        return CapacityPolicy.createNew(UUID.randomUUID(), organisation, name, UUID.randomUUID(), Metric.DISK, 500, 70, 90, 30, 60, 24, "op-1");
    }

    private CapacitySample newSample(UUID organisation, UUID policy, double value, Instant measuredAt) {
        return CapacitySample.createNew(UUID.randomUUID(), organisation, policy, value, measuredAt, "tool", measuredAt);
    }

    @Test
    @DisplayName("a policy is read back whole, only for its own organisation, oldest first; the name is unique per organisation")
    void policyRoundTrip() {
        UUID organisation = UUID.randomUUID();
        CapacityPolicy first = policies.create(newPolicy(organisation, "First")).block();
        policies.create(newPolicy(organisation, "Second")).block();

        CapacityPolicy found = policies.findById(first.getId(), organisation).block();

        assertEquals("First", found.getName());
        assertEquals(Metric.DISK, found.getMetric());
        assertEquals(500.0, found.getLimit());
        assertEquals(60, found.getHorizonDays());
        assertNull(found.getLastValue());
        assertEquals(1, found.getAuditTrail().size());
        assertNull(policies.findById(first.getId(), UUID.randomUUID()).block());
        assertEquals(List.of("First", "Second"), policies.findAll(organisation).map(CapacityPolicy::getName).collectList().block());
        assertThrows(DuplicateCapacityPolicyException.class, () -> policies.create(newPolicy(organisation, "First")).block());
        assertEquals("First", policies.create(newPolicy(UUID.randomUUID(), "First")).block().getName());
    }

    @Test
    @DisplayName("a change is guarded by the status loaded: the second writer from the same state loses, and the audit entry is kept; the whole definition is saved")
    void guardedSave() {
        UUID organisation = UUID.randomUUID();
        CapacityPolicy created = policies.create(newPolicy(organisation, "Guarded")).block();
        CapacityPolicy writerOne = policies.findById(created.getId(), organisation).block();
        CapacityPolicy writerTwo = policies.findById(created.getId(), organisation).block();

        var entry = writerOne.update("Guarded 2", writerOne.getAssetId(), Metric.MEMORY, 64, 50, 80, 14, 30, 12, "op-1");
        policies.save(writerOne, PolicyStatus.ACTIVE, entry).block();
        var other = writerTwo.pause("op-2");

        assertThrows(InvalidCapacityPolicyStatusException.class, () -> policies.save(writerTwo, PolicyStatus.PAUSED, other).block());
        CapacityPolicy stored = policies.findById(created.getId(), organisation).block();
        assertEquals("Guarded 2", stored.getName());
        assertEquals(Metric.MEMORY, stored.getMetric());
        assertEquals(64.0, stored.getLimit());
        assertEquals(14, stored.getWindowDays());
        assertEquals(PolicyStatus.ACTIVE, stored.getStatus());
        assertEquals(2, stored.getAuditTrail().size());
    }

    @Test
    @DisplayName("a rename to a name already in use is a duplicate")
    void renameDuplicate() {
        UUID organisation = UUID.randomUUID();
        policies.create(newPolicy(organisation, "Taken")).block();
        CapacityPolicy other = policies.create(newPolicy(organisation, "Free")).block();
        var entry = other.update("Taken", other.getAssetId(), Metric.DISK, 500, 70, 90, 30, 60, 24, "op-1");

        assertThrows(DuplicateCapacityPolicyException.class, () -> policies.save(other, PolicyStatus.ACTIVE, entry).block());
    }

    @Test
    @DisplayName("the same sample reported twice, even at once, is stored once and the others get the stored one back")
    void sameSample() {
        UUID organisation = UUID.randomUUID();
        UUID policy = policies.create(newPolicy(organisation, "Samples")).block().getId();
        Instant moment = Instant.now().minusSeconds(3600);

        List<CapacitySampleRepository.Stored> results = Flux.range(0, 20).flatMap(i -> samples.create(newSample(organisation, policy, 10 + i, moment))).collectList().block();

        assertEquals(1, results.stream().filter(CapacitySampleRepository.Stored::created).count());
        assertEquals(1, results.stream().map(stored -> stored.sample().getId()).distinct().count());
        assertEquals(1, samples.findAll(organisation, policy, 50).count().block());
        assertTrue(samples.findByNaturalKey(organisation, policy, CapacitySample.truncate(moment)).block() != null);
        assertNull(samples.findByNaturalKey(organisation, UUID.randomUUID(), CapacitySample.truncate(moment)).block());
    }

    @Test
    @DisplayName("samples are listed newest first, by policy or for the tenant, tenant-scoped and limited")
    void listing() {
        UUID organisation = UUID.randomUUID();
        UUID policy = policies.create(newPolicy(organisation, "Listing")).block().getId();
        UUID otherPolicy = policies.create(newPolicy(organisation, "Other")).block().getId();
        Instant base = Instant.now().minus(Duration.ofDays(1));
        CapacitySample old = samples.create(newSample(organisation, policy, 1, base)).block().sample();
        CapacitySample middle = samples.create(newSample(organisation, policy, 2, base.plusSeconds(3600))).block().sample();
        CapacitySample elsewhere = samples.create(newSample(organisation, otherPolicy, 3, base.plusSeconds(7200))).block().sample();

        assertEquals(List.of(elsewhere.getId(), middle.getId(), old.getId()), samples.findAll(organisation, null, 50).map(CapacitySample::getId).collectList().block());
        assertEquals(List.of(middle.getId(), old.getId()), samples.findAll(organisation, policy, 50).map(CapacitySample::getId).collectList().block());
        assertEquals(2, samples.findAll(organisation, null, 2).count().block());
        assertEquals(0, samples.findAll(UUID.randomUUID(), null, 50).count().block());
        assertEquals(2.0, samples.findById(middle.getId(), organisation).block().getValue());
        assertNull(samples.findById(middle.getId(), UUID.randomUUID()).block());
    }

    @Test
    @DisplayName("the trend window answers the samples since a moment, oldest first, and only that policy's")
    void trendWindow() {
        UUID organisation = UUID.randomUUID();
        UUID policy = policies.create(newPolicy(organisation, "Window")).block().getId();
        UUID other = policies.create(newPolicy(organisation, "Elsewhere")).block().getId();
        Instant base = Instant.now().minus(Duration.ofDays(10));
        for (int day = 0; day < 6; day++) {
            samples.create(newSample(organisation, policy, 100 + day, base.plus(Duration.ofDays(day)))).block();
        }
        samples.create(newSample(organisation, other, 999, base.plus(Duration.ofDays(1)))).block();

        List<Double> window = samples.findSince(organisation, policy, base.plus(Duration.ofDays(2)).minusSeconds(1)).map(CapacitySample::getValue).collectList().block();

        assertEquals(List.of(102.0, 103.0, 104.0, 105.0), window);
        assertEquals(0, samples.findSince(UUID.randomUUID(), policy, base).count().block());
    }

    @Test
    @DisplayName("the latest sample only moves forward: a late report changes nothing")
    void latestMovesForward() {
        UUID organisation = UUID.randomUUID();
        CapacityPolicy policy = policies.create(newPolicy(organisation, "Latest")).block();
        Instant t1 = Instant.now().minusSeconds(7200);
        Instant t2 = t1.plusSeconds(3600);

        policies.recordSample(policy.getId(), organisation, newSample(organisation, policy.getId(), 20, t1)).block();
        assertEquals(20.0, policies.findById(policy.getId(), organisation).block().getLastValue());
        policies.recordSample(policy.getId(), organisation, newSample(organisation, policy.getId(), 30, t2)).block();
        policies.recordSample(policy.getId(), organisation, newSample(organisation, policy.getId(), 5, t1.minusSeconds(600))).block();
        policies.recordSample(policy.getId(), organisation, newSample(organisation, policy.getId(), 99, t2)).block();

        CapacityPolicy after = policies.findById(policy.getId(), organisation).block();
        assertEquals(30.0, after.getLastValue());
        assertEquals(CapacitySample.truncate(t2), after.getLastMeasuredAt());
    }

    @Test
    @DisplayName("startup created the unique policy name, the unique sample key, the list index and the expiry (TTL) index")
    void indexesExist() {
        List<Document> policyIndexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("capacity_policies").listIndexes()).collectList().block();
        List<Document> sampleIndexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("capacity_samples").listIndexes()).collectList().block();

        assertTrue(policyIndexes.stream().anyMatch(index -> "organisationId_1_name_1".equals(index.getString("name")) && Boolean.TRUE.equals(index.getBoolean("unique"))));
        assertTrue(sampleIndexes.stream().anyMatch(index -> "organisationId_1_policyId_1_measuredAt_1".equals(index.getString("name")) && Boolean.TRUE.equals(index.getBoolean("unique"))));
        assertTrue(sampleIndexes.stream().anyMatch(index -> "organisationId_1_policyId_1_measuredAt_-1".equals(index.getString("name"))));
        assertTrue(sampleIndexes.stream().anyMatch(index -> "measuredAt_1".equals(index.getString("name")) && index.get("expireAfterSeconds") != null
                && ((Number) index.get("expireAfterSeconds")).longValue() == 400L * 24 * 3600));
    }
}
