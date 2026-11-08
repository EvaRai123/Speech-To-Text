package com.example.speechpractice;

import java.util.ArrayList;
import java.util.List;

public final class AttemptComparisonEngine {
    public List<AttemptComparison> compare(AttemptMeasurementSet first, AttemptMeasurementSet second) {
        if (first == null || second == null) throw new IllegalArgumentException("Two attempts are required.");
        if (first.lessonId.isEmpty() || !first.lessonId.equals(second.lessonId)) {
            throw new IllegalArgumentException("Attempts must belong to the same lesson for direct comparison.");
        }
        List<AttemptComparison> results = new ArrayList<>();
        for (MeasurementRecord earlier : first.measurements) {
            MeasurementRecord later = find(second.measurements, earlier.source, earlier.type);
            if (later == null) {
                results.add(unavailable(earlier, null, "The later attempt has no matching measurement."));
            } else if (earlier.status != MeasurementStatus.AVAILABLE || later.status != MeasurementStatus.AVAILABLE
                    || unreliable(earlier.quality) || unreliable(later.quality)) {
                results.add(unavailable(earlier, later, "At least one attempt is unavailable or has poor signal quality."));
            } else if (!earlier.unit.equals(later.unit)) {
                results.add(unavailable(earlier, later, "Measurement units differ."));
            } else {
                results.add(new AttemptComparison(earlier.source, earlier.type, earlier.value, later.value,
                        earlier.quality, later.quality, later.value - earlier.value,
                        earlier.unit, MeasurementStatus.AVAILABLE,
                        "Attempt-level numerical difference only. Acquisition-window alignment, configuration compatibility, and clinical meaning are not established."));
            }
        }
        for (MeasurementRecord later : second.measurements) {
            if (find(first.measurements, later.source, later.type) == null) {
                results.add(new AttemptComparison(later.source, later.type, null, later.value,
                        null, later.quality, null, later.unit, MeasurementStatus.UNAVAILABLE,
                        "The earlier attempt has no matching measurement."));
            }
        }
        return results;
    }

    private MeasurementRecord find(List<MeasurementRecord> records, MeasurementSource source, MeasurementType type) {
        for (MeasurementRecord record : records) {
            if (record.source == source && record.type == type) return record;
        }
        return null;
    }

    private boolean unreliable(MeasurementQuality quality) {
        return quality == MeasurementQuality.POOR || quality == MeasurementQuality.UNKNOWN;
    }

    private AttemptComparison unavailable(MeasurementRecord first, MeasurementRecord second, String reason) {
        return new AttemptComparison(first.source, first.type, first.value,
                second == null ? null : second.value, first.quality,
                second == null ? null : second.quality, null, first.unit,
                MeasurementStatus.UNAVAILABLE, reason);
    }
}
