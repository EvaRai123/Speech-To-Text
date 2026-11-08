package com.example.speechpractice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchSession implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String sessionId;
    public final ResearchCondition condition;
    public final PracticeExerciseType exerciseType;
    public final long startedAtMs;
    public final long finishedAtMs;
    public final boolean completed;
    public final MicrophoneMeasurement microphoneMeasurement;
    public final SensorMeasurement accelerometerMeasurement;
    public final SensorMeasurement emgMeasurement;
    public final List<PracticeMeasurementState> measurementStates;
    public final String notes;

    public ResearchSession(String sessionId, ResearchCondition condition, PracticeExerciseType exerciseType,
                          long startedAtMs, long finishedAtMs, boolean completed,
                          MicrophoneMeasurement microphoneMeasurement,
                          SensorMeasurement accelerometerMeasurement, SensorMeasurement emgMeasurement,
                          List<PracticeMeasurementState> measurementStates, String notes) {
        this.sessionId = sessionId;
        this.condition = condition;
        this.exerciseType = exerciseType;
        this.startedAtMs = startedAtMs;
        this.finishedAtMs = finishedAtMs;
        this.completed = completed;
        this.microphoneMeasurement = microphoneMeasurement;
        this.accelerometerMeasurement = accelerometerMeasurement;
        this.emgMeasurement = emgMeasurement;
        this.measurementStates = measurementStates == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(measurementStates));
        this.notes = notes == null ? "" : notes;
    }
}
