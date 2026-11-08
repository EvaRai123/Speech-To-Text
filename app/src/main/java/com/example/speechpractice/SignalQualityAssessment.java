package com.example.speechpractice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SignalQualityAssessment {
    public final MeasurementSource source;
    public final MeasurementQuality quality;
    public final Double confidence;
    public final List<String> flags;
    public final String explanation;

    private SignalQualityAssessment(MeasurementSource source, MeasurementQuality quality,
                                    Double confidence, List<String> flags, String explanation) {
        this.source = source;
        this.quality = quality;
        this.confidence = confidence;
        this.flags = Collections.unmodifiableList(new ArrayList<>(flags));
        this.explanation = explanation;
    }

    public static SignalQualityAssessment forAudio(AudioMeasurementResult result) {
        if (result == null || "INVALID".equals(result.quality)) {
            return new SignalQualityAssessment(MeasurementSource.AUDIO, MeasurementQuality.UNKNOWN,
                    null, Collections.singletonList("INVALID_AUDIO"), "Audio analysis was unavailable.");
        }
        List<String> flags = new ArrayList<>(result.qualityFlags);
        MeasurementQuality quality = "USABLE".equals(result.quality)
                ? MeasurementQuality.GOOD : MeasurementQuality.LIMITED;
        Double confidence = Double.isFinite(result.pitchConfidence) && result.pitchConfidence >= 0.0
                && result.pitchConfidence <= 1.0 ? result.pitchConfidence : null;
        if (result.clippingDetected) quality = MeasurementQuality.POOR;
        return new SignalQualityAssessment(MeasurementSource.AUDIO, quality, confidence, flags,
                "Audio quality reflects clipping, level, noise-floor separation, detected activity, and pitch-estimator reliability; it is not clinical accuracy.");
    }

    public static SignalQualityAssessment forCamera(boolean faceDetected, boolean contoursComplete,
                                                    boolean geometryStable, boolean poorLightingDetected) {
        List<String> flags = new ArrayList<>();
        if (!faceDetected) flags.add("NO_FACE");
        if (!contoursComplete) flags.add("INCOMPLETE_LANDMARKS");
        if (poorLightingDetected) flags.add("POOR_LIGHTING");
        if (!geometryStable) flags.add("GEOMETRY_STABILITY_UNKNOWN_OR_POOR");
        MeasurementQuality quality = !faceDetected || !contoursComplete
                ? MeasurementQuality.POOR : MeasurementQuality.LIMITED;
        return new SignalQualityAssessment(MeasurementSource.CAMERA, quality, null, flags,
                "Camera quality is based on face and contour availability. Lighting and tracking stability are not reliably quantified by the current provider.");
    }

    public static SignalQualityAssessment unknown(MeasurementSource source, String explanation) {
        if (source == null) throw new IllegalArgumentException("A measurement source is required.");
        return new SignalQualityAssessment(source, MeasurementQuality.UNKNOWN, null,
                Collections.singletonList("QUALITY_UNAVAILABLE"), explanation);
    }

    public static SignalQualityAssessment forSampleWindow(MeasurementSource source,
                                                          double missingFraction,
                                                          double irregularIntervalFraction,
                                                          boolean saturated,
                                                          boolean unstableBaseline,
                                                          boolean excessiveNoise,
                                                          boolean lowSignal) {
        if (source != MeasurementSource.ACCELEROMETER && source != MeasurementSource.SEMG) {
            throw new IllegalArgumentException("Sample-window quality is only defined for accelerometer and sEMG sources.");
        }
        if (!Double.isFinite(missingFraction) || !Double.isFinite(irregularIntervalFraction)
                || missingFraction < 0.0 || missingFraction > 1.0
                || irregularIntervalFraction < 0.0 || irregularIntervalFraction > 1.0) {
            throw new IllegalArgumentException("Quality fractions must be finite values between zero and one.");
        }
        List<String> flags = new ArrayList<>();
        if (missingFraction > 0.0) flags.add("MISSING_SAMPLES");
        if (irregularIntervalFraction > 0.0) flags.add("IRREGULAR_SAMPLING");
        if (saturated) flags.add("SATURATION");
        if (unstableBaseline) flags.add("UNSTABLE_BASELINE");
        if (excessiveNoise) flags.add("EXCESSIVE_NOISE");
        if (lowSignal) flags.add("INSUFFICIENT_SIGNAL");
        double continuity = 1.0 - Math.min(1.0, missingFraction * 2.0 + irregularIntervalFraction);
        double reliability = continuity;
        if (saturated) reliability *= 0.5;
        if (source == MeasurementSource.ACCELEROMETER) {
            if (lowSignal) reliability *= 0.4;
            if (excessiveNoise) reliability *= 0.7;
        } else {
            if (unstableBaseline) reliability *= 0.7;
            if (excessiveNoise) reliability *= 0.7;
        }
        MeasurementQuality quality;
        if (saturated || source == MeasurementSource.ACCELEROMETER && lowSignal
                || missingFraction >= 0.20 || reliability < 0.45) quality = MeasurementQuality.POOR;
        else if (missingFraction > 0.02 || irregularIntervalFraction > 0.05
                || unstableBaseline || excessiveNoise) quality = MeasurementQuality.LIMITED;
        else quality = MeasurementQuality.GOOD;
        String sourceName = source == MeasurementSource.ACCELEROMETER ? "Accelerometer" : "sEMG";
        return new SignalQualityAssessment(source, quality, reliability, flags,
                sourceName + " reliability is an engineering estimate derived from observed continuity, interval regularity, saturation, baseline stability, and noise flags; it is not clinical accuracy.");
    }
}
