package com.example.speechpractice;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class MultimodalFusionEngine {
    public FusionResult assess(AttemptMeasurementSet attempt, long maxWindowSeparationNanos) {
        if (attempt == null || maxWindowSeparationNanos < 0L) {
            throw new IllegalArgumentException("An attempt and non-negative synchronization tolerance are required.");
        }
        List<String> exclusions = new ArrayList<>();
        Set<MeasurementSource> sources = EnumSet.noneOf(MeasurementSource.class);
        Set<MeasurementSource> observedSources = EnumSet.noneOf(MeasurementSource.class);
        long minimumCenter = Long.MAX_VALUE;
        long maximumCenter = Long.MIN_VALUE;
        for (MeasurementRecord measurement : attempt.measurements) {
            observedSources.add(measurement.source);
            if (measurement.status != MeasurementStatus.AVAILABLE) {
                exclusions.add(measurement.source + "/" + measurement.type + ": status " + measurement.status + ".");
                continue;
            }
            if (measurement.quality == MeasurementQuality.POOR || measurement.quality == MeasurementQuality.UNKNOWN) {
                exclusions.add(measurement.source + "/" + measurement.type + ": quality is " + measurement.quality + ".");
                continue;
            }
            if (measurement.windowStartMonotonicNanos == null
                    || measurement.windowEndMonotonicNanos == null) {
                exclusions.add(measurement.source + "/" + measurement.type
                        + ": timestamped acquisition window is unavailable.");
                continue;
            }
            sources.add(measurement.source);
            long center = measurement.windowStartMonotonicNanos
                    + (measurement.windowEndMonotonicNanos - measurement.windowStartMonotonicNanos) / 2L;
            minimumCenter = Math.min(minimumCenter, center);
            maximumCenter = Math.max(maximumCenter, center);
        }
        for (MeasurementSource source : MeasurementSource.values()) {
            if (!observedSources.contains(source)) {
                exclusions.add(source + ": no measurements were persisted for this attempt.");
            }
        }
        if (sources.size() < 2) {
            return new FusionResult(MeasurementStatus.UNAVAILABLE, attempt.measurements,
                    new ArrayList<>(sources), new ArrayList<>(), exclusions,
                    "At least two synchronized usable channels are required; no combined value is produced.");
        }
        if (maximumCenter - minimumCenter > maxWindowSeparationNanos) {
            exclusions.add("Measurement windows exceed the configured synchronization tolerance.");
            return new FusionResult(MeasurementStatus.UNAVAILABLE, attempt.measurements,
                    new ArrayList<>(sources), new ArrayList<>(), exclusions,
                    "Window synchronization failed. Individual channel results are retained; fusion is unavailable.");
        }
        return new FusionResult(MeasurementStatus.UNAVAILABLE, attempt.measurements,
                new ArrayList<>(sources), new ArrayList<>(), exclusions,
                "Windows are temporally compatible, but no validated mapping combines these distinct channel constructs. Individual results are retained and no universal score or weighted fusion is inferred.");
    }
}
