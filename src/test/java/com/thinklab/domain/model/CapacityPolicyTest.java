package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidCapacityPolicyStatusException;
import com.thinklab.domain.model.CapacityPolicy.Assessment;
import com.thinklab.domain.model.CapacityPolicy.Forecast;
import com.thinklab.domain.model.CapacityPolicy.Level;
import com.thinklab.domain.model.CapacityPolicy.Metric;
import com.thinklab.domain.model.CapacityPolicy.Point;
import com.thinklab.domain.model.CapacityPolicy.PolicyAuditEntry;
import com.thinklab.domain.model.CapacityPolicy.PolicyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapacityPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    private final UUID org = UUID.randomUUID();
    private final UUID asset = UUID.randomUUID();

    private CapacityPolicy created() {
        return CapacityPolicy.createNew(UUID.randomUUID(), org, "Orders disk", asset, Metric.DISK, 100, 70, 90, 30, 30, 24, "op");
    }

    /** A policy with limit 100, warning at 70 percent, critical at 90, a 30 day window and horizon, expecting a report every 24 hours. */
    private CapacityPolicy stored(PolicyStatus status, Double lastValue, Instant lastMeasuredAt, int horizonDays) {
        return CapacityPolicy.reconstitute(UUID.randomUUID(), org, "Orders disk", asset, Metric.DISK, 100, 70, 90, 30, horizonDays, 24, status, NOW.minus(Duration.ofDays(40)),
                NOW.minus(Duration.ofDays(40)), lastValue, lastMeasuredAt, List.of());
    }

    private static Point day(int daysAgo, double value) {
        return new Point(NOW.minus(Duration.ofDays(daysAgo)), value);
    }

    private static void rejects(Executable action) {
        assertThrows(IllegalArgumentException.class, action);
    }

    // ------------------------------------------------------------------ Creation

    @Test
    @DisplayName("a policy is created ACTIVE with its promise, no sample yet and an INITIATED audit entry")
    void createNew() {
        CapacityPolicy policy = created();

        assertEquals(PolicyStatus.ACTIVE, policy.getStatus());
        assertEquals(Metric.DISK, policy.getMetric());
        assertEquals(100, policy.getLimit());
        assertEquals(70, policy.getWarnPercent());
        assertEquals(90, policy.getCriticalPercent());
        assertEquals(30, policy.getWindowDays());
        assertEquals(30, policy.getHorizonDays());
        assertEquals(24, policy.getReportEveryHours());
        assertNull(policy.getLastValue());
        assertNull(policy.getLastMeasuredAt());
        assertEquals(1, policy.getAuditTrail().size());
        assertEquals("INITIATED", policy.getAuditTrail().get(0).action());
        assertNull(policy.getAuditTrail().get(0).fromStatus());
    }

    @Test
    @DisplayName("every field is checked: identity, name, asset, metric, limit, utilisation levels, window, horizon, interval and executor")
    void validation() {
        UUID id = UUID.randomUUID();
        rejects(() -> CapacityPolicy.createNew(null, org, "n", asset, Metric.DISK, 100, 70, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, null, "n", asset, Metric.DISK, 100, 70, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, null, asset, Metric.DISK, 100, 70, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, " ", asset, Metric.DISK, 100, 70, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "x".repeat(81), asset, Metric.DISK, 100, 70, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", null, Metric.DISK, 100, 70, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, null, 100, 70, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 0, 70, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, Double.NaN, 70, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, CapacityPolicy.MAX_LIMIT * 2, 70, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 0, 90, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 100, 100, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 70, 70, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 70, 101, 30, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 70, 90, 0, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 70, 90, 91, 30, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 70, 90, 30, 0, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 70, 90, 30, 366, 24, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 70, 90, 30, 30, 0, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 70, 90, 30, 30, 721, "op"));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 70, 90, 30, 30, 24, null));
        rejects(() -> CapacityPolicy.createNew(id, org, "n", asset, Metric.DISK, 100, 70, 90, 30, 30, 24, " "));
        CapacityPolicy edges = CapacityPolicy.createNew(id, org, "x".repeat(80), asset, Metric.OTHER, CapacityPolicy.MAX_LIMIT, 1, 2, 90, 365, 720, "op");
        assertEquals(80, edges.getName().length());
    }

    @Test
    @DisplayName("reconstitute keeps what was stored, defaults what is missing, and refuses a missing identity")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-01T10:00:00Z");
        Instant measured = Instant.parse("2026-10-17T10:00:00Z");
        CapacityPolicy full = CapacityPolicy.reconstitute(id, org, "n", asset, Metric.CPU, 8, 60, 80, 7, 14, 6, PolicyStatus.PAUSED, created, created, 3.5, measured,
                List.of(new PolicyAuditEntry(created, "INITIATED", "op", null, PolicyStatus.ACTIVE, "d")));
        assertEquals(PolicyStatus.PAUSED, full.getStatus());
        assertEquals(3.5, full.getLastValue());
        assertEquals(measured, full.getLastMeasuredAt());
        assertEquals(1, full.getAuditTrail().size());

        CapacityPolicy bare = CapacityPolicy.reconstitute(id, org, "n", asset, Metric.CPU, 8, 60, 80, 7, 14, 6, null, null, null, null, null, null);
        assertEquals(PolicyStatus.ACTIVE, bare.getStatus());
        assertTrue(bare.getAuditTrail().isEmpty());
        assertEquals(bare.getCreatedAt(), bare.getUpdatedAt());

        rejects(() -> CapacityPolicy.reconstitute(null, org, "n", asset, Metric.CPU, 8, 60, 80, 7, 14, 6, null, null, null, null, null, null));
        rejects(() -> CapacityPolicy.reconstitute(id, null, "n", asset, Metric.CPU, 8, 60, 80, 7, 14, 6, null, null, null, null, null, null));
        rejects(() -> CapacityPolicy.reconstitute(id, org, null, asset, Metric.CPU, 8, 60, 80, 7, 14, 6, null, null, null, null, null, null));
        rejects(() -> CapacityPolicy.reconstitute(id, org, "n", null, Metric.CPU, 8, 60, 80, 7, 14, 6, null, null, null, null, null, null));
        rejects(() -> CapacityPolicy.reconstitute(id, org, "n", asset, null, 8, 60, 80, 7, 14, 6, null, null, null, null, null, null));
    }

    // ------------------------------------------------------------------ Behaviours

    @Test
    @DisplayName("update changes the whole definition in any status, keeps the latest sample and is audited; it validates like creation")
    void update() {
        CapacityPolicy policy = CapacityPolicy.reconstitute(UUID.randomUUID(), org, "Old", asset, Metric.DISK, 100, 70, 90, 30, 30, 24, PolicyStatus.PAUSED, NOW, NOW, 55.0, NOW, List.of());
        UUID newAsset = UUID.randomUUID();

        PolicyAuditEntry entry = policy.update("New", newAsset, Metric.MEMORY, 64, 50, 80, 14, 60, 12, "op-2");

        assertEquals("UPDATED", entry.action());
        assertEquals(PolicyStatus.PAUSED, entry.fromStatus());
        assertEquals(PolicyStatus.PAUSED, entry.toStatus());
        assertEquals("New", policy.getName());
        assertEquals(newAsset, policy.getAssetId());
        assertEquals(Metric.MEMORY, policy.getMetric());
        assertEquals(64, policy.getLimit());
        assertEquals(50, policy.getWarnPercent());
        assertEquals(80, policy.getCriticalPercent());
        assertEquals(14, policy.getWindowDays());
        assertEquals(60, policy.getHorizonDays());
        assertEquals(12, policy.getReportEveryHours());
        assertEquals(55.0, policy.getLastValue());
        rejects(() -> policy.update(" ", newAsset, Metric.MEMORY, 64, 50, 80, 14, 60, 12, "op"));
        rejects(() -> policy.update("New", newAsset, Metric.MEMORY, 64, 50, 80, 14, 60, 12, " "));
        assertEquals("New", policy.getName());
    }

    @Test
    @DisplayName("pause and resume follow ACTIVE <-> PAUSED; anything else is a 409; both need an executor")
    void pauseAndResume() {
        CapacityPolicy policy = created();

        rejects(() -> policy.pause(" "));
        assertThrows(InvalidCapacityPolicyStatusException.class, () -> policy.resume("op"));
        PolicyAuditEntry paused = policy.pause("op");
        assertEquals(PolicyStatus.PAUSED, policy.getStatus());
        assertEquals(PolicyStatus.ACTIVE, paused.fromStatus());
        assertThrows(InvalidCapacityPolicyStatusException.class, () -> policy.pause("op"));
        rejects(() -> policy.resume(null));
        PolicyAuditEntry resumed = policy.resume("op");
        assertEquals(PolicyStatus.ACTIVE, policy.getStatus());
        assertEquals(PolicyStatus.PAUSED, resumed.fromStatus());
        assertEquals(List.of("INITIATED", "PAUSED", "RESUMED"), policy.getAuditTrail().stream().map(PolicyAuditEntry::action).toList());
    }

    // ------------------------------------------------------------------ Judgment

    @Test
    @DisplayName("a paused policy is not judged at all")
    void paused() {
        Assessment judged = stored(PolicyStatus.PAUSED, 95.0, NOW, 30).assess(NOW, List.of(day(2, 40), day(0, 95)));

        assertEquals(Level.PAUSED, judged.level());
        assertNull(judged.utilisationPercent());
        assertNull(judged.forecast());
        assertFalse(judged.stale());
    }

    @Test
    @DisplayName("without any sample there is no data and no forecast")
    void noData() {
        Assessment judged = stored(PolicyStatus.ACTIVE, null, null, 30).assess(NOW, List.of());

        assertEquals(Level.NO_DATA, judged.level());
        assertEquals(Forecast.NOT_ENOUGH_DATA, judged.forecast());
        assertNull(judged.utilisationPercent());
        assertFalse(judged.stale());
    }

    @Test
    @DisplayName("the level is the latest sample against the warning and critical utilisation, each starting at its own percentage")
    void levels() {
        assertEquals(Level.OK, stored(PolicyStatus.ACTIVE, 69.9, NOW, 30).assess(NOW, List.of()).level());
        assertEquals(Level.WARNING, stored(PolicyStatus.ACTIVE, 70.0, NOW, 30).assess(NOW, List.of()).level());
        assertEquals(Level.WARNING, stored(PolicyStatus.ACTIVE, 89.9, NOW, 30).assess(NOW, List.of()).level());
        assertEquals(Level.CRITICAL, stored(PolicyStatus.ACTIVE, 90.0, NOW, 30).assess(NOW, List.of()).level());
        Assessment over = stored(PolicyStatus.ACTIVE, 120.0, NOW, 30).assess(NOW, List.of());
        assertEquals(Level.CRITICAL, over.level());
        assertEquals(120.0, over.utilisationPercent());
    }

    private Level levelAfter(double latest, double... daysAgoOldestFirstValues) {
        java.util.ArrayList<Point> points = new java.util.ArrayList<>();
        for (int i = 0; i < daysAgoOldestFirstValues.length; i++) {
            points.add(day(daysAgoOldestFirstValues.length - i, daysAgoOldestFirstValues[i]));
        }
        points.add(day(0, latest));
        return stored(PolicyStatus.ACTIVE, latest, NOW, 30).assess(NOW, points).level();
    }

    @Test
    @DisplayName("a level is entered at its percentage but left only five points below it, so a value hovering at a threshold does not flip it (ADR-034)")
    void hysteresis() {
        // warning at 70, critical at 90
        assertEquals(Level.WARNING, levelAfter(68, 72));       // 72 entered warning; 68 is still within 5 points of 70
        assertEquals(Level.OK, levelAfter(64.9, 72));          // 64.9 is below 65: warning is left
        assertEquals(Level.WARNING, levelAfter(65, 72));       // exactly 5 below stays
        assertEquals(Level.CRITICAL, levelAfter(86, 92));      // 92 entered critical; 86 is within 5 points of 90
        assertEquals(Level.WARNING, levelAfter(84.9, 92));     // critical is left, still above 65: warning
        assertEquals(Level.OK, levelAfter(60, 92));            // a drop below both ends at OK
        assertEquals(Level.WARNING, levelAfter(88, 92, 66));   // critical left, warning kept: 88 is plainly warning after 66 held warning
        assertEquals(Level.OK, levelAfter(68, 50, 69));        // never reached warning: 69 and 68 are plainly OK
        assertEquals(Level.WARNING, levelAfter(70, 69));       // rising is immediate
        assertEquals(Level.CRITICAL, levelAfter(90, 72));      // and can skip a level
        assertEquals(Level.WARNING, levelAfter(71, 69.9, 72, 68, 72, 68)); // hovering: stays warning, never flipping back
    }

    @Test
    @DisplayName("the latest sample counts even when the window does not hold it, and a window without it replays from nothing")
    void hysteresisLatestOutsideWindow() {
        CapacityPolicy policy = stored(PolicyStatus.ACTIVE, 68.0, NOW, 30);
        assertEquals(Level.WARNING, policy.assess(NOW, List.of(day(3, 50), day(2, 72))).level());
        assertEquals(Level.OK, policy.assess(NOW, List.of(day(3, 50), day(2, 60))).level());
        assertEquals(Level.OK, policy.assess(NOW, List.of()).level());
    }

    @Test
    @DisplayName("the margin never exceeds half the warning percentage, so a very low warning can still be left")
    void hysteresisLowWarning() {
        CapacityPolicy policy = CapacityPolicy.reconstitute(UUID.randomUUID(), org, "Tiny", asset, Metric.OTHER, 100, 4, 90, 30, 30, 24, PolicyStatus.ACTIVE,
                NOW.minus(Duration.ofDays(40)), NOW.minus(Duration.ofDays(40)), 1.0, NOW, List.of());
        assertEquals(Level.OK, policy.assess(NOW, List.of(day(1, 5), day(0, 1))).level());
        assertEquals(Level.WARNING, policy.assess(NOW, List.of(day(1, 5), day(0, 2.1))).level());
    }

    @Test
    @DisplayName("a sample is stale after twice the expected reporting interval, and not before")
    void stale() {
        assertFalse(stored(PolicyStatus.ACTIVE, 10.0, NOW.minus(Duration.ofHours(48)), 30).assess(NOW, List.of()).stale());
        assertTrue(stored(PolicyStatus.ACTIVE, 10.0, NOW.minus(Duration.ofHours(48)).minusSeconds(1), 30).assess(NOW, List.of()).stale());
    }

    @Test
    @DisplayName("fewer than two samples, or two less than an hour apart, say nothing about the trend")
    void notEnoughData() {
        CapacityPolicy policy = stored(PolicyStatus.ACTIVE, 50.0, NOW, 30);

        assertEquals(Forecast.NOT_ENOUGH_DATA, policy.assess(NOW, List.of()).forecast());
        assertEquals(Forecast.NOT_ENOUGH_DATA, policy.assess(NOW, List.of(day(0, 50))).forecast());
        assertEquals(Forecast.NOT_ENOUGH_DATA, policy.assess(NOW, List.of(new Point(NOW.minusSeconds(3599), 40), new Point(NOW, 50))).forecast());
        assertEquals(Forecast.WITHIN_HORIZON, policy.assess(NOW, List.of(new Point(NOW.minusSeconds(3600), 40), new Point(NOW, 50))).forecast());
    }

    @Test
    @DisplayName("a flat or falling line is stable: no days to the limit")
    void stableTrend() {
        CapacityPolicy policy = stored(PolicyStatus.ACTIVE, 50.0, NOW, 30);

        Assessment flat = policy.assess(NOW, List.of(day(3, 50), day(2, 50), day(1, 50), day(0, 50)));
        Assessment falling = policy.assess(NOW, List.of(day(2, 70), day(1, 60), day(0, 50)));

        assertEquals(Forecast.STABLE, flat.forecast());
        assertNull(flat.daysToLimit());
        assertNull(flat.projectedExhaustionAt());
        assertEquals(Forecast.STABLE, falling.forecast());
    }

    @Test
    @DisplayName("a rising line gives the days from the latest sample to the limit at its pace, and the date it would be reached")
    void risingTrend() {
        CapacityPolicy policy = stored(PolicyStatus.ACTIVE, 60.0, NOW, 30);

        Assessment judged = policy.assess(NOW, List.of(day(2, 40), day(1, 50), day(0, 60)));

        assertEquals(Forecast.WITHIN_HORIZON, judged.forecast());
        assertEquals(4.0, judged.daysToLimit(), 1e-9);
        assertEquals(NOW.plus(Duration.ofDays(4)), judged.projectedExhaustionAt());
        assertEquals(60.0, judged.utilisationPercent());
        assertEquals(Level.OK, judged.level());
    }

    @Test
    @DisplayName("running out later than the horizon is beyond it; exactly at the horizon is within it")
    void horizon() {
        List<Point> tenPerDay = List.of(day(2, 40), day(1, 50), day(0, 60));

        assertEquals(Forecast.BEYOND_HORIZON, stored(PolicyStatus.ACTIVE, 60.0, NOW, 3).assess(NOW, tenPerDay).forecast());
        assertEquals(Forecast.WITHIN_HORIZON, stored(PolicyStatus.ACTIVE, 60.0, NOW, 4).assess(NOW, tenPerDay).forecast());
    }

    @Test
    @DisplayName("a policy already at or over its limit and still rising is out of room now")
    void alreadyFull() {
        Assessment judged = stored(PolicyStatus.ACTIVE, 100.0, NOW, 30).assess(NOW, List.of(day(2, 80), day(1, 90), day(0, 100)));

        assertEquals(0.0, judged.daysToLimit());
        assertEquals(NOW, judged.projectedExhaustionAt());
        assertEquals(Forecast.WITHIN_HORIZON, judged.forecast());
        assertEquals(Level.CRITICAL, judged.level());
    }

    @Test
    @DisplayName("the line is least squares over all the points, so one noisy sample does not decide it")
    void leastSquares() {
        CapacityPolicy policy = stored(PolicyStatus.ACTIVE, 62.0, NOW, 365);

        Assessment judged = policy.assess(NOW, List.of(day(4, 40), day(3, 52), day(2, 55), day(1, 59), day(0, 62)));

        // x counts days forward from the first sample (0..4, mean 2), the mean of y is 53.6: slope = (-2*-13.6 + -1*-1.6 + 0*1.4 + 1*5.4 + 2*8.4) / 10 = 51.0 / 10 = 5.1 per day
        assertEquals((100.0 - 62.0) / 5.1, judged.daysToLimit(), 1e-9);
    }
}
