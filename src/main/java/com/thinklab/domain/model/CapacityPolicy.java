package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidCapacityPolicyStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root (BIAN Control Record) of the IT Capacity Registry Service Domain: how much of one thing an asset may use, and when to worry.
 * A policy names the asset and the {@link Metric} (disk, memory, CPU...), the {@code limit} (the most the asset can hold, in whatever unit the
 * tool reports in), the utilisation at which to warn and at which it is critical, how many days of samples the trend is drawn from, how far
 * ahead running out is worth a warning, and how often the tool is expected to report. The tool that measures reports samples to the registry
 * (ADR-030); the policy keeps only the latest one and judges itself when read (ADR-031): nothing here measures anything.
 */
public class CapacityPolicy {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_WINDOW_DAYS = 90;
    public static final int MAX_HORIZON_DAYS = 365;
    public static final int MAX_REPORT_EVERY_HOURS = 720;
    public static final double MAX_LIMIT = 1.0e15;
    /** A trend needs at least this much time between its first and last sample, or two samples a second apart would draw a line to infinity. */
    public static final Duration MIN_TREND_SPAN = Duration.ofHours(1);

    /** A level, once reached, is only left when the utilisation falls this many points below the percentage that entered it (ADR-034). */
    public static final double HYSTERESIS_POINTS = 5.0;

    private static final double SECONDS_PER_DAY = 86400.0;

    private final UUID id;
    private final UUID organisationId;
    private String name;
    private UUID assetId;
    private Metric metric;
    private double limit;
    private int warnPercent;
    private int criticalPercent;
    private int windowDays;
    private int horizonDays;
    private int reportEveryHours;
    private PolicyStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Double lastValue;
    private Instant lastMeasuredAt;
    private final List<PolicyAuditEntry> auditTrail;

    private CapacityPolicy(UUID id, UUID organisationId, String name, UUID assetId, Metric metric, double limit, int warnPercent, int criticalPercent, int windowDays,
                           int horizonDays, int reportEveryHours, String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.assetId = assetId;
        this.metric = metric;
        this.limit = limit;
        this.warnPercent = warnPercent;
        this.criticalPercent = criticalPercent;
        this.windowDays = windowDays;
        this.horizonDays = horizonDays;
        this.reportEveryHours = reportEveryHours;
        this.status = PolicyStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new PolicyAuditEntry(this.createdAt, "INITIATED", executor, null, PolicyStatus.ACTIVE, "Capacity policy for " + metric + " of asset " + assetId + "."));
    }

    private CapacityPolicy(UUID id, UUID organisationId, String name, UUID assetId, Metric metric, double limit, int warnPercent, int criticalPercent, int windowDays,
                           int horizonDays, int reportEveryHours, PolicyStatus status, Instant createdAt, Instant updatedAt, Double lastValue, Instant lastMeasuredAt,
                           List<PolicyAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.assetId = assetId;
        this.metric = metric;
        this.limit = limit;
        this.warnPercent = warnPercent;
        this.criticalPercent = criticalPercent;
        this.windowDays = windowDays;
        this.horizonDays = horizonDays;
        this.reportEveryHours = reportEveryHours;
        this.status = status != null ? status : PolicyStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.lastValue = lastValue;
        this.lastMeasuredAt = lastMeasuredAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static CapacityPolicy createNew(UUID id, UUID organisationId, String name, UUID assetId, Metric metric, double limit, int warnPercent, int criticalPercent,
                                           int windowDays, int horizonDays, int reportEveryHours, String executor) {
        if (id == null || organisationId == null) {
            throw new IllegalArgumentException("ID and Organisation ID are mandatory for CapacityPolicy creation.");
        }
        validate(name, assetId, metric, limit, warnPercent, criticalPercent, windowDays, horizonDays, reportEveryHours);
        requireExecutor(executor);
        return new CapacityPolicy(id, organisationId, name, assetId, metric, limit, warnPercent, criticalPercent, windowDays, horizonDays, reportEveryHours, executor);
    }

    public static CapacityPolicy reconstitute(UUID id, UUID organisationId, String name, UUID assetId, Metric metric, double limit, int warnPercent, int criticalPercent,
                                              int windowDays, int horizonDays, int reportEveryHours, PolicyStatus status, Instant createdAt, Instant updatedAt,
                                              Double lastValue, Instant lastMeasuredAt, List<PolicyAuditEntry> auditTrail) {
        if (id == null || organisationId == null || name == null || assetId == null || metric == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Name, Asset ID and Metric are mandatory to reconstitute a CapacityPolicy.");
        }
        return new CapacityPolicy(id, organisationId, name, assetId, metric, limit, warnPercent, criticalPercent, windowDays, horizonDays, reportEveryHours, status,
                createdAt, updatedAt, lastValue, lastMeasuredAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /** Behavior Qualifier: {@code update}. The whole definition again, in any status; the latest sample is not touched. */
    public PolicyAuditEntry update(String newName, UUID newAssetId, Metric newMetric, double newLimit, int newWarnPercent, int newCriticalPercent, int newWindowDays,
                                   int newHorizonDays, int newReportEveryHours, String executor) {
        validate(newName, newAssetId, newMetric, newLimit, newWarnPercent, newCriticalPercent, newWindowDays, newHorizonDays, newReportEveryHours);
        requireExecutor(executor);
        this.name = newName;
        this.assetId = newAssetId;
        this.metric = newMetric;
        this.limit = newLimit;
        this.warnPercent = newWarnPercent;
        this.criticalPercent = newCriticalPercent;
        this.windowDays = newWindowDays;
        this.horizonDays = newHorizonDays;
        this.reportEveryHours = newReportEveryHours;
        return record("UPDATED", executor, this.status, this.status, "Policy updated.");
    }

    /** Behavior Qualifier: {@code control/pause}. ACTIVE -&gt; PAUSED: the policy is no longer judged. */
    public PolicyAuditEntry pause(String executor) {
        requireStatus(PolicyStatus.ACTIVE);
        requireExecutor(executor);
        this.status = PolicyStatus.PAUSED;
        return record("PAUSED", executor, PolicyStatus.ACTIVE, PolicyStatus.PAUSED, "Paused: the policy is no longer judged.");
    }

    /** Behavior Qualifier: {@code control/resume}. PAUSED -&gt; ACTIVE. */
    public PolicyAuditEntry resume(String executor) {
        requireStatus(PolicyStatus.PAUSED);
        requireExecutor(executor);
        this.status = PolicyStatus.ACTIVE;
        return record("RESUMED", executor, PolicyStatus.PAUSED, PolicyStatus.ACTIVE, "Resumed.");
    }

    /**
     * How full the asset is and where it is heading, at that moment (ADR-031). The level is the latest sample against the warning and critical
     * utilisation, with hysteresis on the way down (ADR-034); the sample is stale when the tool is silent for more than twice its expected interval. The forecast draws a least-squares line
     * through the samples of the window ({@code points}, oldest first), reads its slope in units per day, and counts the days from the latest sample
     * to the limit at that pace: a flat or falling line is stable, and fewer than two samples an hour apart say nothing. Nothing of it is stored.
     */
    public Assessment assess(Instant now, List<Point> points) {
        if (status == PolicyStatus.PAUSED) {
            return new Assessment(Level.PAUSED, null, false, null, null, null);
        }
        if (lastValue == null) {
            return new Assessment(Level.NO_DATA, null, false, Forecast.NOT_ENOUGH_DATA, null, null);
        }
        double utilisation = lastValue / limit * 100.0;
        Level level = levelWithHysteresis(points);
        boolean stale = now.isAfter(lastMeasuredAt.plus(Duration.ofHours(2L * reportEveryHours)));
        Double slope = slopePerDay(points);
        if (slope == null) {
            return new Assessment(level, utilisation, stale, Forecast.NOT_ENOUGH_DATA, null, null);
        }
        if (slope <= 0) {
            return new Assessment(level, utilisation, stale, Forecast.STABLE, null, null);
        }
        double daysToLimit = lastValue >= limit ? 0.0 : (limit - lastValue) / slope;
        Instant exhaustion = lastMeasuredAt.plusSeconds(Math.round(daysToLimit * SECONDS_PER_DAY));
        Forecast forecast = daysToLimit <= horizonDays ? Forecast.WITHIN_HORIZON : Forecast.BEYOND_HORIZON;
        return new Assessment(level, utilisation, stale, forecast, daysToLimit, exhaustion);
    }

    /**
     * The level after replaying the samples of the window, oldest first (ADR-034). It rises as soon as a sample reaches a percentage, but only falls
     * once the utilisation is {@link #HYSTERESIS_POINTS} below the percentage that entered it, so a value hovering at a threshold does not flip the
     * level on every read. The first sample has no past and is judged plainly; the latest sample counts even when the window does not hold it.
     */
    private Level levelWithHysteresis(List<Point> points) {
        double margin = Math.min(HYSTERESIS_POINTS, warnPercent / 2.0);
        Level level = null;
        for (Point point : points) {
            level = next(level, point.value() / limit * 100.0, margin);
        }
        boolean windowHoldsLatest = !points.isEmpty() && points.get(points.size() - 1).at().equals(lastMeasuredAt);
        if (!windowHoldsLatest) {
            level = next(level, lastValue / limit * 100.0, margin);
        }
        return level;
    }

    private Level next(Level previous, double utilisation, double margin) {
        Level plain = utilisation >= criticalPercent ? Level.CRITICAL : utilisation >= warnPercent ? Level.WARNING : Level.OK;
        if (previous == null || plain.compareTo(previous) >= 0) {
            return plain;
        }
        if (previous == Level.CRITICAL && utilisation >= criticalPercent - margin) {
            return Level.CRITICAL;
        }
        return utilisation >= warnPercent - margin ? Level.WARNING : Level.OK;
    }

    /** The least-squares slope of the points in units per day, or null when there are not enough of them to draw a line. */
    private static Double slopePerDay(List<Point> points) {
        if (points.size() < 2) {
            return null;
        }
        Instant first = points.get(0).at();
        Instant last = points.get(points.size() - 1).at();
        if (Duration.between(first, last).compareTo(MIN_TREND_SPAN) < 0) {
            return null;
        }
        double meanX = 0;
        double meanY = 0;
        for (Point point : points) {
            meanX += secondsBetween(first, point.at()) / SECONDS_PER_DAY;
            meanY += point.value();
        }
        meanX /= points.size();
        meanY /= points.size();
        double covariance = 0;
        double variance = 0;
        for (Point point : points) {
            double dx = secondsBetween(first, point.at()) / SECONDS_PER_DAY - meanX;
            covariance += dx * (point.value() - meanY);
            variance += dx * dx;
        }
        return covariance / variance;
    }

    private static double secondsBetween(Instant from, Instant to) {
        return Duration.between(from, to).toMillis() / 1000.0;
    }

    // --- Internal helpers ---

    private PolicyAuditEntry record(String action, String executor, PolicyStatus from, PolicyStatus to, String detail) {
        this.updatedAt = Instant.now();
        PolicyAuditEntry entry = new PolicyAuditEntry(this.updatedAt, action, executor, from, to, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(PolicyStatus expected) {
        if (this.status != expected) {
            throw new InvalidCapacityPolicyStatusException(String.format("Illegal transition: CapacityPolicy is [%s], expected [%s].", this.status, expected));
        }
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable CapacityPolicy mutations.");
        }
    }

    private static void validate(String name, UUID assetId, Metric metric, double limit, int warnPercent, int criticalPercent, int windowDays, int horizonDays,
                                 int reportEveryHours) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Name is mandatory for a CapacityPolicy (up to " + MAX_NAME_LENGTH + " characters).");
        }
        if (assetId == null) {
            throw new IllegalArgumentException("The asset the policy watches is mandatory.");
        }
        if (metric == null) {
            throw new IllegalArgumentException("The metric the policy watches is mandatory.");
        }
        if (!(limit > 0) || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("The limit must be more than 0 and at most " + (long) MAX_LIMIT + ".");
        }
        if (warnPercent < 1 || warnPercent > 99) {
            throw new IllegalArgumentException("The warning utilisation must be between 1 and 99 percent.");
        }
        if (criticalPercent <= warnPercent || criticalPercent > 100) {
            throw new IllegalArgumentException("The critical utilisation must be above the warning one, up to 100 percent.");
        }
        if (windowDays < 1 || windowDays > MAX_WINDOW_DAYS) {
            throw new IllegalArgumentException("The trend window must be between 1 and " + MAX_WINDOW_DAYS + " days.");
        }
        if (horizonDays < 1 || horizonDays > MAX_HORIZON_DAYS) {
            throw new IllegalArgumentException("The forecast horizon must be between 1 and " + MAX_HORIZON_DAYS + " days.");
        }
        if (reportEveryHours < 1 || reportEveryHours > MAX_REPORT_EVERY_HOURS) {
            throw new IllegalArgumentException("The expected reporting interval must be between 1 and " + MAX_REPORT_EVERY_HOURS + " hours.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getName() { return name; }
    public UUID getAssetId() { return assetId; }
    public Metric getMetric() { return metric; }
    public double getLimit() { return limit; }
    public int getWarnPercent() { return warnPercent; }
    public int getCriticalPercent() { return criticalPercent; }
    public int getWindowDays() { return windowDays; }
    public int getHorizonDays() { return horizonDays; }
    public int getReportEveryHours() { return reportEveryHours; }
    public PolicyStatus getStatus() { return status; }
    /**
     * Whether the policy's promise is being kept, given how it was judged (platform ADR-001): broken ({@code CAPACITY_CRITICAL}) at the critical level, kept at
     * OK or WARNING (a warning is a notice on the screen, not an incident), not judged when paused or without a sample yet.
     */
    public static Promise promise(Assessment judged) {
        return switch (judged.level()) {
            case CRITICAL -> Promise.broken("CAPACITY_CRITICAL");
            case OK, WARNING -> Promise.kept();
            case PAUSED, NO_DATA -> Promise.notJudged();
        };
    }

    /** The last moment the policy or the facts it is judged on changed: an update, or the latest sample measured. */
    public Instant changedAt() {
        return lastMeasuredAt != null && lastMeasuredAt.isAfter(updatedAt) ? lastMeasuredAt : updatedAt;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Double getLastValue() { return lastValue; }
    public Instant getLastMeasuredAt() { return lastMeasuredAt; }
    public List<PolicyAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    public enum PolicyStatus { ACTIVE, PAUSED }

    /** What is measured. The unit is whatever the tool reports in, the same as the limit. */
    public enum Metric { DISK, MEMORY, CPU, NETWORK, LICENSES, OTHER }

    /** PAUSED is not judged; NO_DATA has had no sample yet; the rest compare the latest sample with the warning and critical utilisation. */
    public enum Level { PAUSED, NO_DATA, OK, WARNING, CRITICAL }

    /** Where the trend leads: not enough samples to say, flat or falling, running out within the horizon, or later than that. */
    public enum Forecast { NOT_ENOUGH_DATA, STABLE, WITHIN_HORIZON, BEYOND_HORIZON }

    public record PolicyAuditEntry(Instant occurredAt, String action, String executor, PolicyStatus fromStatus, PolicyStatus toStatus, String detail) {}

    /** One sample as the trend sees it. */
    public record Point(Instant at, double value) {}

    /** The judgment of one moment: nothing of it is stored. The utilisation, days and date are null when they do not apply; the forecast is null for a paused policy. */
    public record Assessment(Level level, Double utilisationPercent, boolean stale, Forecast forecast, Double daysToLimit, Instant projectedExhaustionAt) {}
}
