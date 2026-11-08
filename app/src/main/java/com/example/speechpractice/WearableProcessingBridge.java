package com.example.speechpractice;

import java.util.ArrayList;
import java.util.List;

public final class WearableProcessingBridge {
    public interface Listener {
        void onProcessedWindow(ProcessedSignalWindow window);
        void onRejectedBatch(MeasurementSource source, String reason);
    }

    private final AccelerometerProcessor accelerometerProcessor;
    private final SemgProcessor semgProcessor;
    private final AccelerometerProcessor.Config accelerometerConfig;
    private final SemgProcessor.Config semgConfig;
    private final String expectedAccelerometerConfigurationId;
    private final String expectedSemgConfigurationId;
    private final Listener listener;
    private final List<AccelerometerSample> accelerometerSamples = new ArrayList<>();
    private final List<SemgSample> semgSamples = new ArrayList<>();

    public WearableProcessingBridge(AccelerometerProcessor.Config accelerometerConfig,
                                    SemgProcessor.Config semgConfig,
                                    String expectedAccelerometerConfigurationId,
                                    String expectedSemgConfigurationId, Listener listener) {
        if (listener == null) throw new IllegalArgumentException("A processing listener is required.");
        this.accelerometerConfig = accelerometerConfig;
        this.semgConfig = semgConfig;
        this.expectedAccelerometerConfigurationId = expectedAccelerometerConfigurationId == null
                ? "" : expectedAccelerometerConfigurationId.trim();
        this.expectedSemgConfigurationId = expectedSemgConfigurationId == null
                ? "" : expectedSemgConfigurationId.trim();
        this.listener = listener;
        accelerometerProcessor = new AccelerometerProcessor();
        semgProcessor = new SemgProcessor();
    }

    public void accept(WearablePacket packet, WearableDeviceStatus status,
                       DeviceClockSynchronizer synchronizer) {
        if (packet == null || status == null || synchronizer == null || !synchronizer.isAvailable()) {
            return;
        }
        if (packet.type == WearableProtocol.TYPE_ACCELEROMETER_BATCH) {
            acceptAccelerometer(packet, status, synchronizer);
        } else if (packet.type == WearableProtocol.TYPE_SEMG_BATCH) {
            acceptSemg(packet, status, synchronizer);
        }
    }

    public void reset() {
        accelerometerSamples.clear();
        semgSamples.clear();
    }

    private void acceptAccelerometer(WearablePacket packet, WearableDeviceStatus status,
                                     DeviceClockSynchronizer synchronizer) {
        if (accelerometerConfig == null || !status.accelerometerAvailable
                || expectedAccelerometerConfigurationId.isEmpty()
                || !expectedAccelerometerConfigurationId.equals(status.accelerometerConfigurationId)) {
            listener.onRejectedBatch(MeasurementSource.ACCELEROMETER,
                    "No exact expected accelerometer configuration match and available device channel exists.");
            return;
        }
        double actualRate = 1_000_000.0 / status.accelerometerSamplePeriodMicros;
        if (accelerometerConfig.expectedSampleRateHz > 0.0
                && Math.abs(actualRate - accelerometerConfig.expectedSampleRateHz)
                / accelerometerConfig.expectedSampleRateHz > 0.20) {
            listener.onRejectedBatch(MeasurementSource.ACCELEROMETER,
                    "Device sample rate does not match the explicitly supplied processing configuration.");
            return;
        }
        List<AccelerometerSample> converted = new ArrayList<>();
        for (WearablePacket.AccelerometerWireSample sample : packet.accelerometerSamples) {
            if (sample.unitCode != status.accelerometerUnitCode
                    || Math.abs(sample.scaleToMetersPerSecondSquared
                    - status.accelerometerScaleToMetersPerSecondSquared) > 1e-9) {
                listener.onRejectedBatch(MeasurementSource.ACCELEROMETER,
                        "Sample scaling differs from the device's announced configuration.");
                return;
            }
            long timestampNanos = synchronizer.toAndroidMonotonicNanos(sample.deviceTimestampMicros);
            converted.add(new AccelerometerSample(timestampNanos,
                    sample.xRaw * sample.scaleToMetersPerSecondSquared,
                    sample.yRaw * sample.scaleToMetersPerSecondSquared,
                    sample.zRaw * sample.scaleToMetersPerSecondSquared));
        }
        appendOrdered(accelerometerSamples, converted, MeasurementSource.ACCELEROMETER);
        if (accelerometerSamples.size() >= 4) {
            listener.onProcessedWindow(accelerometerProcessor.process(
                    new ArrayList<>(accelerometerSamples), accelerometerConfig));
            accelerometerSamples.clear();
        }
    }

    private void acceptSemg(WearablePacket packet, WearableDeviceStatus status,
                            DeviceClockSynchronizer synchronizer) {
        if (semgConfig == null || !status.semgAvailable
                || expectedSemgConfigurationId.isEmpty()
                || !expectedSemgConfigurationId.equals(status.semgConfigurationId)
                || semgConfig.inputUnit.isEmpty()
                || !semgConfig.inputUnit.equals(status.semgInputUnit)) {
            listener.onRejectedBatch(MeasurementSource.SEMG,
                    "No compatible explicit sEMG input unit/front-end processing configuration or available device channel exists.");
            return;
        }
        if (Math.abs(status.semgSamplingRateHz - semgConfig.samplingRateHz)
                / semgConfig.samplingRateHz > semgConfig.rateToleranceFraction) {
            listener.onRejectedBatch(MeasurementSource.SEMG,
                    "Device sEMG rate does not match the explicitly supplied front-end configuration.");
            return;
        }
        List<SemgSample> converted = new ArrayList<>();
        for (WearablePacket.SemgWireSample sample : packet.semgSamples) {
            if (Math.abs(sample.scaleToConfiguredInputUnits
                    - status.semgScaleToConfiguredInputUnits) > 1e-9) {
                listener.onRejectedBatch(MeasurementSource.SEMG,
                        "sEMG scaling differs from the device's announced front-end configuration.");
                return;
            }
            long timestampNanos = synchronizer.toAndroidMonotonicNanos(sample.deviceTimestampMicros);
            converted.add(new SemgSample(timestampNanos,
                    sample.rawValue * sample.scaleToConfiguredInputUnits));
        }
        appendOrdered(semgSamples, converted, MeasurementSource.SEMG);
        if (semgSamples.size() >= 4) {
            listener.onProcessedWindow(semgProcessor.process(new ArrayList<>(semgSamples), semgConfig));
            semgSamples.clear();
        }
    }

    private <T> void appendOrdered(List<T> target, List<T> incoming, MeasurementSource source) {
        long previous = -1L;
        if (!target.isEmpty()) {
            previous = timestampOf(target.get(target.size() - 1));
        }
        for (T sample : incoming) {
            long timestamp = timestampOf(sample);
            if (timestamp <= previous) {
                target.clear();
                listener.onRejectedBatch(source,
                        "Converted timestamps are not strictly increasing; buffered samples were discarded.");
                return;
            }
            target.add(sample);
            previous = timestamp;
        }
    }

    private long timestampOf(Object sample) {
        if (sample instanceof AccelerometerSample) {
            return ((AccelerometerSample) sample).timestampMonotonicNanos;
        }
        return ((SemgSample) sample).timestampMonotonicNanos;
    }
}
