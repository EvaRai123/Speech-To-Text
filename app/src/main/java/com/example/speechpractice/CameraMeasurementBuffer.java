package com.example.speechpractice;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class CameraMeasurementBuffer {
    private final Map<MeasurementType, MeasurementRecord> latest =
            new EnumMap<>(MeasurementType.class);

    void put(MeasurementRecord measurement) {
        latest.put(measurement.type, measurement);
    }

    boolean contains(MeasurementType type) {
        return latest.containsKey(type);
    }

    boolean hasMeasurements() {
        return !latest.isEmpty();
    }

    int size() {
        return latest.size();
    }

    List<MeasurementRecord> drain() {
        List<MeasurementRecord> records = new ArrayList<>(latest.values());
        latest.clear();
        return records;
    }
}
