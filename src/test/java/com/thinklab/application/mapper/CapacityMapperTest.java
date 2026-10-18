package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.CapacityPolicyResponse;
import com.thinklab.application.dto.response.CapacitySampleResponse;
import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.CapacityPolicy.Metric;
import com.thinklab.domain.model.CapacityPolicy.Point;
import com.thinklab.domain.model.CapacitySample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapacityMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @Test
    @DisplayName("the mapper is a utility class")
    void utility() throws ReflectiveOperationException {
        Constructor<CapacityMapper> constructor = CapacityMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("a policy answers its promise, the latest sample and how it was judged, forecast included")
    void policy() {
        UUID asset = UUID.randomUUID();
        CapacityPolicy policy = CapacityPolicy.reconstitute(UUID.randomUUID(), UUID.randomUUID(), "Orders disk", asset, Metric.DISK, 100, 70, 90, 30, 30, 24,
                CapacityPolicy.PolicyStatus.ACTIVE, NOW, NOW, 60.0, NOW, List.of());
        var judged = policy.assess(NOW, List.of(new Point(NOW.minus(Duration.ofDays(2)), 40), new Point(NOW.minus(Duration.ofDays(1)), 50), new Point(NOW, 60)));

        CapacityPolicyResponse response = CapacityMapper.toResponse(policy, judged);

        assertEquals("DISK", response.metric());
        assertEquals(asset, response.assetId());
        assertEquals(100.0, response.limit());
        assertEquals("ACTIVE", response.status());
        assertEquals("OK", response.level());
        assertEquals(60.0, response.utilisationPercent());
        assertEquals("WITHIN_HORIZON", response.forecast());
        assertEquals(4.0, response.daysToLimit(), 1e-9);
        assertEquals(NOW.plus(Duration.ofDays(4)), response.projectedExhaustionAt());
        assertEquals(60.0, response.lastValue());
        assertEquals(NOW, response.lastMeasuredAt());
    }

    @Test
    @DisplayName("a paused policy answers no forecast at all")
    void pausedPolicy() {
        CapacityPolicy policy = CapacityPolicy.reconstitute(UUID.randomUUID(), UUID.randomUUID(), "n", UUID.randomUUID(), Metric.CPU, 8, 60, 80, 7, 14, 6,
                CapacityPolicy.PolicyStatus.PAUSED, NOW, NOW, null, null, List.of());

        CapacityPolicyResponse response = CapacityMapper.toResponse(policy, policy.assess(NOW, List.of()));

        assertEquals("PAUSED", response.level());
        assertNull(response.forecast());
        assertNull(response.lastValue());
    }

    @Test
    @DisplayName("a sample answers what was measured and who reported it")
    void sample() {
        CapacitySample sample = CapacitySample.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 12.5, NOW, "tool", NOW);

        CapacitySampleResponse response = CapacityMapper.toResponse(sample);

        assertEquals(12.5, response.value());
        assertEquals(NOW, response.measuredAt());
        assertEquals("tool", response.reportedBy());
        assertEquals(sample.getPolicyId(), response.policyId());
    }

    @Test
    @DisplayName("an audit entry keeps the previous status when there is one")
    void audit() {
        CapacityPolicy policy = CapacityPolicy.createNew(UUID.randomUUID(), UUID.randomUUID(), "n", UUID.randomUUID(), Metric.CPU, 8, 60, 80, 7, 14, 6, "op");
        policy.pause("op");

        List<AuditEntryResponse> trail = policy.getAuditTrail().stream().map(CapacityMapper::toResponse).toList();

        assertNull(trail.get(0).fromStatus());
        assertEquals("ACTIVE", trail.get(1).fromStatus());
        assertEquals("PAUSED", trail.get(1).toStatus());
    }
}
