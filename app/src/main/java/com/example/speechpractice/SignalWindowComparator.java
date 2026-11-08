package com.example.speechpractice;

public final class SignalWindowComparator {
    public WindowVariationResult compare(MeasurementRecord earlier, MeasurementRecord later,
                                         String earlierTaskId, String laterTaskId,
                                         String earlierConfigurationId, String laterConfigurationId) {
        if (earlier == null || later == null) {
            throw new IllegalArgumentException("Two measurement windows are required.");
        }
        String reason = incompatibleReason(earlier, later, earlierTaskId, laterTaskId,
                earlierConfigurationId, laterConfigurationId);
        if (reason != null) {
            return new WindowVariationResult(MeasurementStatus.UNAVAILABLE, earlier.source,
                    earlier.type, null, null, reason, earlier.quality, later.quality,
                    earlierTaskId, earlierConfigurationId);
        }
        return new WindowVariationResult(MeasurementStatus.AVAILABLE, earlier.source,
                earlier.type, later.value - earlier.value, earlier.unit,
                "Difference between compatible sample windows only; not a skill-improvement or clinical outcome.",
                earlier.quality, later.quality, earlierTaskId, earlierConfigurationId);
    }

    private String incompatibleReason(MeasurementRecord earlier, MeasurementRecord later,
                                     String earlierTask, String laterTask,
                                     String earlierConfiguration, String laterConfiguration) {
        if (earlier.source != later.source || earlier.type != later.type) {
            return "Measurement source or type differs.";
        }
        if (earlier.status != MeasurementStatus.AVAILABLE || later.status != MeasurementStatus.AVAILABLE
                || unreliable(earlier.quality) || unreliable(later.quality)) {
            return "A window is unavailable or has poor quality.";
        }
        if (!earlier.unit.equals(later.unit)) return "Measurement units differ.";
        if (earlier.windowStartMonotonicNanos == null || earlier.windowEndMonotonicNanos == null
                || later.windowStartMonotonicNanos == null || later.windowEndMonotonicNanos == null) {
            return "Acquisition window timestamps are unavailable.";
        }
        if (earlier.windowEndMonotonicNanos > later.windowStartMonotonicNanos) {
            return "Windows overlap or are not in chronological order.";
        }
        if (earlierTask == null || earlierTask.trim().isEmpty() || !earlierTask.equals(laterTask)) {
            return "Task identifiers are missing or incompatible.";
        }
        if (earlierConfiguration == null || earlierConfiguration.trim().isEmpty()
                || !earlierConfiguration.equals(laterConfiguration)) {
            return "Acquisition configuration identifiers are missing or incompatible.";
        }
        return null;
    }

    private boolean unreliable(MeasurementQuality quality) {
        return quality == MeasurementQuality.POOR || quality == MeasurementQuality.UNKNOWN;
    }
}
