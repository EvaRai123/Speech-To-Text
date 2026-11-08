package com.example.speechpractice;

public final class AttemptComparison {
    public final MeasurementSource source;
    public final MeasurementType type;
    public final Double firstValue;
    public final Double secondValue;
    public final MeasurementQuality firstQuality;
    public final MeasurementQuality secondQuality;
    public final Double difference;
    public final String unit;
    public final MeasurementStatus status;
    public final String explanation;

    AttemptComparison(MeasurementSource source, MeasurementType type, Double firstValue,
                      Double secondValue, MeasurementQuality firstQuality,
                      MeasurementQuality secondQuality, Double difference, String unit,
                      MeasurementStatus status, String explanation) {
        this.source = source;
        this.type = type;
        this.firstValue = firstValue;
        this.secondValue = secondValue;
        this.firstQuality = firstQuality;
        this.secondQuality = secondQuality;
        this.difference = difference;
        this.unit = unit;
        this.status = status;
        this.explanation = explanation;
    }
}
