package com.example.speechpractice;

public final class WearableDeviceStatus {
    public final String deviceId;
    public final String firmwareVersion;
    public final boolean accelerometerAvailable;
    public final boolean semgAvailable;
    public final boolean semgSafetyMetadataReported;
    public final int accelerometerInitializationCode;
    public final int semgInitializationCode;
    public final int accelerometerUnitCode;
    public final double accelerometerScaleToMetersPerSecondSquared;
    public final long accelerometerSamplePeriodMicros;
    public final double semgScaleToConfiguredInputUnits;
    public final double semgSamplingRateHz;
    public final String semgInputUnit;
    public final String semgAnalogFrontEndId;
    public final String accelerometerConfigurationId;
    public final String semgConfigurationId;

    WearableDeviceStatus(String deviceId, String firmwareVersion,
                         boolean accelerometerAvailable, boolean semgAvailable,
                         boolean semgSafetyMetadataReported,
                         int accelerometerInitializationCode, int semgInitializationCode,
                         int accelerometerUnitCode, double accelerometerScaleToMetersPerSecondSquared,
                         long accelerometerSamplePeriodMicros, double semgScaleToConfiguredInputUnits,
                         double semgSamplingRateHz, String semgInputUnit,
                         String semgAnalogFrontEndId, String accelerometerConfigurationId,
                         String semgConfigurationId) {
        this.deviceId = deviceId;
        this.firmwareVersion = firmwareVersion;
        this.accelerometerAvailable = accelerometerAvailable;
        this.semgAvailable = semgAvailable;
        this.semgSafetyMetadataReported = semgSafetyMetadataReported;
        this.accelerometerInitializationCode = accelerometerInitializationCode;
        this.semgInitializationCode = semgInitializationCode;
        this.accelerometerUnitCode = accelerometerUnitCode;
        this.accelerometerScaleToMetersPerSecondSquared = accelerometerScaleToMetersPerSecondSquared;
        this.accelerometerSamplePeriodMicros = accelerometerSamplePeriodMicros;
        this.semgScaleToConfiguredInputUnits = semgScaleToConfiguredInputUnits;
        this.semgSamplingRateHz = semgSamplingRateHz;
        this.semgInputUnit = semgInputUnit;
        this.semgAnalogFrontEndId = semgAnalogFrontEndId;
        this.accelerometerConfigurationId = accelerometerConfigurationId;
        this.semgConfigurationId = semgConfigurationId;
    }

    public String summary() {
        String accel = accelerometerAvailable
                ? "accelerometer available (" + accelerometerConfigurationId + ")"
                : "accelerometer unavailable (initialization/status " + accelerometerInitializationCode + ")";
        String emg = semgAvailable
                ? "sEMG available (" + semgConfigurationId + ")"
                : semgSafetyMetadataReported
                ? "sEMG unavailable (initialization/status " + semgInitializationCode + ")"
                : "sEMG unavailable (device has not reported all required safety metadata)";
        return accel + "; " + emg;
    }
}
