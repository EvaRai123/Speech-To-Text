package com.example.speechpractice;

import java.util.Set;

public interface MeasurementProvider {
    interface Listener {
        void onMeasurement(MeasurementRecord measurement);
        void onStatusChanged(MeasurementSource source, MeasurementStatus status, String detail);
        void onFailure(MeasurementSource source, String detail, Throwable cause);
    }

    MeasurementSource getSource();
    MeasurementStatus getStatus();
    void start(Set<MeasurementType> requestedTypes, Listener listener);
    void stop();
}
