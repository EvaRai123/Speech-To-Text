package com.example.speechpractice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PracticeSession implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String sessionId;
    public final long startedAtMs;
    public final long finishedAtMs;
    public final PracticeExerciseType exerciseType;
    public final MicrophoneMeasurement microphoneMeasurement;
    public final SensorMeasurement accelerometerMeasurement;
    public final SensorMeasurement emgMeasurement;
    public final List<String> notes;
    public final List<PracticeMeasurementState> measurementStates;

    public PracticeSession(String sessionId, long startedAtMs, long finishedAtMs, PracticeExerciseType exerciseType,
                          MicrophoneMeasurement microphoneMeasurement, SensorMeasurement accelerometerMeasurement,
                          SensorMeasurement emgMeasurement, List<String> notes, List<PracticeMeasurementState> measurementStates) {
        this.sessionId = sessionId;
        this.startedAtMs = startedAtMs;
        this.finishedAtMs = finishedAtMs;
        this.exerciseType = exerciseType;
        this.microphoneMeasurement = microphoneMeasurement;
        this.accelerometerMeasurement = accelerometerMeasurement;
        this.emgMeasurement = emgMeasurement;
        this.notes = notes == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(notes));
        this.measurementStates = measurementStates == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(measurementStates));
    }
}
