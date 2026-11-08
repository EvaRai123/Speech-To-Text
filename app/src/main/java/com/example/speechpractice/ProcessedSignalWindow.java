package com.example.speechpractice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProcessedSignalWindow {
    public final MeasurementSource source;
    public final MeasurementStatus status;
    public final double estimatedSamplingRateHz;
    public final int sampleCount;
    public final int estimatedMissingSamples;
    public final SignalQualityAssessment quality;
    public final List<MeasurementRecord> measurements;
    public final String detail;

    ProcessedSignalWindow(MeasurementSource source, MeasurementStatus status,
                          double estimatedSamplingRateHz, int sampleCount,
                          int estimatedMissingSamples, SignalQualityAssessment quality,
                          List<MeasurementRecord> measurements, String detail) {
        this.source = source;
        this.status = status;
        this.estimatedSamplingRateHz = estimatedSamplingRateHz;
        this.sampleCount = sampleCount;
        this.estimatedMissingSamples = estimatedMissingSamples;
        this.quality = quality;
        this.measurements = Collections.unmodifiableList(new ArrayList<>(measurements));
        this.detail = detail == null ? "" : detail;
    }

    static ProcessedSignalWindow unavailable(MeasurementSource source, MeasurementType type,
                                             MeasurementStatus status, String detail) {
        List<MeasurementRecord> records = Collections.singletonList(
                MeasurementRecord.unavailable(source, type, status, detail));
        return new ProcessedSignalWindow(source, status, Double.NaN, 0, 0,
                SignalQualityAssessment.unknown(source, detail), records, detail);
    }
}
