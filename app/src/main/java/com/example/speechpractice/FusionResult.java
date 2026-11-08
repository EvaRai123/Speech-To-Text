package com.example.speechpractice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FusionResult {
    public final MeasurementStatus status;
    public final List<MeasurementRecord> channelMeasurements;
    public final List<MeasurementSource> eligibleSources;
    public final List<MeasurementSource> contributingSources;
    public final List<String> exclusions;
    public final String explanation;

    FusionResult(MeasurementStatus status, List<MeasurementRecord> channelMeasurements,
                 List<MeasurementSource> eligibleSources, List<MeasurementSource> contributingSources,
                 List<String> exclusions,
                 String explanation) {
        this.status = status;
        this.channelMeasurements = Collections.unmodifiableList(new ArrayList<>(channelMeasurements));
        this.eligibleSources = Collections.unmodifiableList(new ArrayList<>(eligibleSources));
        this.contributingSources = Collections.unmodifiableList(new ArrayList<>(contributingSources));
        this.exclusions = Collections.unmodifiableList(new ArrayList<>(exclusions));
        this.explanation = explanation;
    }
}
