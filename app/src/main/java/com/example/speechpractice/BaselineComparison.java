package com.example.speechpractice;

public final class BaselineComparison {
    public final MeasurementStatus status;
    public final Double difference;
    public final String unit;
    public final String explanation;

    BaselineComparison(MeasurementStatus status, Double difference, String unit, String explanation) {
        this.status = status;
        this.difference = difference;
        this.unit = unit;
        this.explanation = explanation;
    }

    static BaselineComparison unavailable(String explanation) {
        return new BaselineComparison(MeasurementStatus.UNAVAILABLE, null, null, explanation);
    }
}
