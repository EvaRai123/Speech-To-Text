package com.example.speechpractice;

public enum MeasurementType {
    SPEECH_ACTIVITY(MeasurementSource.AUDIO, "ms"),
    VOICED_DURATION(MeasurementSource.AUDIO, "ms"),
    FUNDAMENTAL_FREQUENCY(MeasurementSource.AUDIO, "Hz"),
    PITCH_VARIATION(MeasurementSource.AUDIO, "Hz"),
    RELATIVE_INTENSITY(MeasurementSource.AUDIO, "dBFS"),
    PAUSE_DURATION(MeasurementSource.AUDIO, "ms"),
    ATTEMPT_DURATION(MeasurementSource.AUDIO, "ms"),
    MECHANICAL_VIBRATION(MeasurementSource.ACCELEROMETER, "m/s^2"),
    SURFACE_MUSCLE_ACTIVITY(MeasurementSource.SEMG, "uV"),
    ACCELERATION_RMS(MeasurementSource.ACCELEROMETER, "m/s^2"),
    ACCELERATION_PEAK_TO_PEAK(MeasurementSource.ACCELEROMETER, "m/s^2"),
    ACCELERATION_ENERGY(MeasurementSource.ACCELEROMETER, "(m/s^2)^2*s"),
    ACCELERATION_DOMINANT_FREQUENCY(MeasurementSource.ACCELEROMETER, "Hz"),
    SEMG_ENVELOPE_RMS(MeasurementSource.SEMG, "uV"),
    SEMG_DC_OFFSET(MeasurementSource.SEMG, "uV"),
    LIP_CLOSURE_RATIO(MeasurementSource.CAMERA, "ratio"),
    LIP_SEPARATION_RATIO(MeasurementSource.CAMERA, "ratio"),
    LIP_SPREAD_WIDTH_RATIO(MeasurementSource.CAMERA, "ratio"),
    LIP_ROUNDING_PROXY(MeasurementSource.CAMERA, "ratio"),
    MOUTH_OPENING_RATIO(MeasurementSource.CAMERA, "ratio"),
    LOWER_FACE_DISPLACEMENT_PROXY(MeasurementSource.CAMERA, "ratio"),
    RESTING_LIP_POSITION(MeasurementSource.CAMERA, "ratio"),
    LIP_CLOSURE(MeasurementSource.CAMERA, "ratio"),
    LIP_SEPARATION(MeasurementSource.CAMERA, "ratio"),
    LIP_SPREADING(MeasurementSource.CAMERA, "ratio"),
    LIP_ROUNDING(MeasurementSource.CAMERA, "ratio"),
    LIP_SHAPE_CHANGE(MeasurementSource.CAMERA, "ratio"),
    MOUTH_OPENING(MeasurementSource.CAMERA, "ratio"),
    MOUTH_CLOSING(MeasurementSource.CAMERA, "ratio"),
    RELATIVE_MOUTH_OPENING(MeasurementSource.CAMERA, "ratio"),
    JAW_MOVEMENT(MeasurementSource.CAMERA, "ratio"),
    LIP_JAW_TIMING(MeasurementSource.CAMERA, "ms"),
    LIP_MOVEMENT_TRANSITION(MeasurementSource.CAMERA, "count"),
    REPEATED_LIP_MOVEMENT(MeasurementSource.CAMERA, "count"),
    MOUTH_CONFIGURATION_CHANGE(MeasurementSource.CAMERA, "ratio"),
    VISIBLE_MOUTH_MOVEMENT(MeasurementSource.CAMERA, "ratio"),
    VISIBLE_LIP_TEETH_POSITION(MeasurementSource.CAMERA, "ratio");

    public final MeasurementSource source;
    public final String canonicalUnit;

    MeasurementType(MeasurementSource source, String canonicalUnit) {
        this.source = source;
        this.canonicalUnit = canonicalUnit;
    }

    public boolean accepts(MeasurementSource measurementSource, String unit, double value) {
        if (measurementSource != source || !canonicalUnit.equals(unit) || !Double.isFinite(value)) return false;
        switch (this) {
            case SPEECH_ACTIVITY:
            case VOICED_DURATION:
            case PAUSE_DURATION:
            case ATTEMPT_DURATION:
                return value >= 0.0;
            case FUNDAMENTAL_FREQUENCY:
            case ACCELERATION_DOMINANT_FREQUENCY:
                return value > 0.0;
            case PITCH_VARIATION:
            case SURFACE_MUSCLE_ACTIVITY:
            case ACCELERATION_RMS:
            case ACCELERATION_PEAK_TO_PEAK:
            case ACCELERATION_ENERGY:
            case SEMG_ENVELOPE_RMS:
                return value >= 0.0;
            case SEMG_DC_OFFSET:
                return true;
            case RELATIVE_INTENSITY:
                return value <= 0.0;
            case LIP_MOVEMENT_TRANSITION:
            case REPEATED_LIP_MOVEMENT:
                return value >= 0.0 && value == Math.rint(value);
            case RESTING_LIP_POSITION:
            case LIP_CLOSURE_RATIO:
            case LIP_SEPARATION_RATIO:
            case LIP_SPREAD_WIDTH_RATIO:
            case LIP_ROUNDING_PROXY:
            case MOUTH_OPENING_RATIO:
            case LOWER_FACE_DISPLACEMENT_PROXY:
            case LIP_CLOSURE:
            case LIP_SEPARATION:
            case LIP_SPREADING:
            case LIP_ROUNDING:
            case LIP_SHAPE_CHANGE:
            case MOUTH_OPENING:
            case MOUTH_CLOSING:
            case RELATIVE_MOUTH_OPENING:
            case JAW_MOVEMENT:
            case MOUTH_CONFIGURATION_CHANGE:
            case VISIBLE_MOUTH_MOVEMENT:
            case VISIBLE_LIP_TEETH_POSITION:
                return value >= 0.0 && value <= 1.0;
            case MECHANICAL_VIBRATION:
                return true;
            case LIP_JAW_TIMING:
                return value >= 0.0;
            default:
                return true;
        }
    }
}
