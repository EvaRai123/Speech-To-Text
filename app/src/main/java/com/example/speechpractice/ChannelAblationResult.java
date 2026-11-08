package com.example.speechpractice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ChannelAblationResult {
    public final String attemptId;
    public final String comparisonAttemptId;
    public final Map<MeasurementSource, List<MeasurementRecord>> included;
    public final Map<MeasurementSource, List<WindowVariationResult>> comparisons;
    public final Map<MeasurementSource, String> excluded;
    public final String limitation;

    ChannelAblationResult(String attemptId, Map<MeasurementSource, List<MeasurementRecord>> included,
                          Map<MeasurementSource, String> excluded, String limitation) {
        this(attemptId, "", included, Collections.emptyMap(), excluded, limitation);
    }

    ChannelAblationResult(String attemptId, String comparisonAttemptId,
                          Map<MeasurementSource, List<MeasurementRecord>> included,
                          Map<MeasurementSource, List<WindowVariationResult>> comparisons,
                          Map<MeasurementSource, String> excluded, String limitation) {
        this.attemptId = attemptId;
        this.comparisonAttemptId = comparisonAttemptId;
        Map<MeasurementSource, List<MeasurementRecord>> includedCopy = new EnumMap<>(MeasurementSource.class);
        for (Map.Entry<MeasurementSource, List<MeasurementRecord>> entry : included.entrySet()) {
            includedCopy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.included = Collections.unmodifiableMap(includedCopy);
        Map<MeasurementSource, List<WindowVariationResult>> comparisonCopy =
                new EnumMap<>(MeasurementSource.class);
        for (Map.Entry<MeasurementSource, List<WindowVariationResult>> entry : comparisons.entrySet()) {
            comparisonCopy.put(entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.comparisons = Collections.unmodifiableMap(comparisonCopy);
        this.excluded = Collections.unmodifiableMap(new EnumMap<>(excluded));
        this.limitation = limitation;
    }
}
