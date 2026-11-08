package com.example.speechpractice;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChannelAblationEngine {
    public ChannelAblationResult compare(AttemptMeasurementSet attempt, Set<MeasurementSource> selectedSources) {
        if (attempt == null || selectedSources == null) {
            throw new IllegalArgumentException("An attempt and explicit channel selection are required.");
        }
        Map<MeasurementSource, List<MeasurementRecord>> included = new EnumMap<>(MeasurementSource.class);
        Map<MeasurementSource, String> excluded = new EnumMap<>(MeasurementSource.class);
        for (MeasurementSource source : MeasurementSource.values()) {
            if (!selectedSources.contains(source)) {
                excluded.put(source, "Excluded by this channel-ablation configuration.");
                continue;
            }
            List<MeasurementRecord> sourceRecords = new ArrayList<>();
            boolean unavailable = false;
            for (MeasurementRecord record : attempt.measurements) {
                if (record.source != source) continue;
                if (record.status == MeasurementStatus.AVAILABLE
                        && record.quality != MeasurementQuality.POOR
                        && record.quality != MeasurementQuality.UNKNOWN) {
                    sourceRecords.add(record);
                } else {
                    unavailable = true;
                }
            }
            if (sourceRecords.isEmpty()) {
                excluded.put(source, unavailable ? "No usable record; available records were unavailable or poor quality."
                        : "No measurements were persisted for this source.");
            } else {
                included.put(source, sourceRecords);
                if (unavailable) excluded.put(source, "Some source records were excluded because they were unavailable or poor quality.");
            }
        }
        return new ChannelAblationResult(attempt.attemptId, included, excluded,
                "Ablation preserves per-channel measurements only. It does not calculate superiority or a combined score.");
    }

    public ChannelAblationResult compare(AttemptMeasurementSet earlier, AttemptMeasurementSet later,
                                         Set<MeasurementSource> selectedSources) {
        if (earlier == null || later == null || selectedSources == null) {
            throw new IllegalArgumentException("Two attempts and explicit channel selection are required.");
        }
        Map<MeasurementSource, List<MeasurementRecord>> included = new EnumMap<>(MeasurementSource.class);
        Map<MeasurementSource, List<WindowVariationResult>> comparisons =
                new EnumMap<>(MeasurementSource.class);
        Map<MeasurementSource, String> excluded = new EnumMap<>(MeasurementSource.class);
        SignalWindowComparator comparator = new SignalWindowComparator();
        for (MeasurementSource source : MeasurementSource.values()) {
            if (!selectedSources.contains(source)) {
                excluded.put(source, "Excluded by this channel-ablation selection.");
                continue;
            }
            if (!earlier.lessonId.equals(later.lessonId)) {
                excluded.put(source, "Equivalent task identity is unavailable; lesson identifiers differ.");
                continue;
            }
            String earlierConfiguration = earlier.sourceConfigurations.get(source);
            String laterConfiguration = later.sourceConfigurations.get(source);
            if (earlierConfiguration == null || laterConfiguration == null
                    || !earlierConfiguration.equals(laterConfiguration)) {
                excluded.put(source, "Acquisition configuration is missing or incompatible.");
                continue;
            }
            List<MeasurementRecord> firstRecords = recordsFor(earlier, source);
            List<MeasurementRecord> secondRecords = recordsFor(later, source);
            List<WindowVariationResult> sourceComparisons = new ArrayList<>();
            List<MeasurementRecord> accepted = new ArrayList<>();
            boolean matched = false;
            for (MeasurementRecord first : firstRecords) {
                for (MeasurementRecord second : secondRecords) {
                    if (first.type != second.type || !first.unit.equals(second.unit)) continue;
                    matched = true;
                    WindowVariationResult result = comparator.compare(first, second,
                            earlier.lessonId, later.lessonId,
                            earlierConfiguration, laterConfiguration);
                    sourceComparisons.add(result);
                    if (result.status == MeasurementStatus.AVAILABLE) {
                        accepted.add(first);
                        accepted.add(second);
                    }
                }
            }
            if (!matched) {
                excluded.put(source, "No equivalent measurement type and unit were saved in both attempts.");
            } else {
                comparisons.put(source, sourceComparisons);
                if (!accepted.isEmpty()) included.put(source, accepted);
                if (accepted.isEmpty()) {
                    excluded.put(source, "Matched measurements could not be compared: acquisition windows, quality, or ordering were incompatible.");
                }
            }
        }
        return new ChannelAblationResult(earlier.attemptId, later.attemptId, included,
                comparisons, excluded,
                "Only same-task, same-source/type/unit, quality-acceptable, timestamped windows with an exact configuration match are compared. No combined score or superiority claim is produced.");
    }

    private List<MeasurementRecord> recordsFor(AttemptMeasurementSet attempt, MeasurementSource source) {
        List<MeasurementRecord> records = new ArrayList<>();
        for (MeasurementRecord record : attempt.measurements) {
            if (record.source == source) records.add(record);
        }
        return records;
    }
}
