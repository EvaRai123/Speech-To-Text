package com.example.speechpractice;

public final class WindowVariationResult {
    public final MeasurementStatus status;
    public final MeasurementSource source;
    public final MeasurementType type;
    public final Double difference;
    public final String unit;
    public final String explanation;
    public final MeasurementQuality earlierQuality;
    public final MeasurementQuality laterQuality;
    public final String taskId;
    public final String configurationId;

    WindowVariationResult(MeasurementStatus status, MeasurementSource source, MeasurementType type,
                         Double difference, String unit, String explanation) {
        this(status, source, type, difference, unit, explanation, null, null, "", "");
    }

    WindowVariationResult(MeasurementStatus status, MeasurementSource source, MeasurementType type,
                          Double difference, String unit, String explanation,
                          MeasurementQuality earlierQuality, MeasurementQuality laterQuality,
                          String taskId, String configurationId) {
        this.status = status;
        this.source = source;
        this.type = type;
        this.difference = difference;
        this.unit = unit;
        this.explanation = explanation;
        this.earlierQuality = earlierQuality;
        this.laterQuality = laterQuality;
        this.taskId = taskId == null ? "" : taskId;
        this.configurationId = configurationId == null ? "" : configurationId;
    }
}
