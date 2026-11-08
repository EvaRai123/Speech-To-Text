package com.example.speechpractice;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public final class AudioSummaryRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String algorithmVersion;
    public final long computedAtWallClockMs;
    public final int sampleRateHz;
    public final double rmsDbfs;
    public final double peakDbfs;
    public final double noiseFloorDbfs;
    public final double snrDb;
    public final boolean clippingDetected;
    public final double speechActiveDurationMs;
    public final int pauseCount;
    public final double pauseDurationMs;
    public final double medianF0Hz;
    public final double f0MinHz;
    public final double f0MaxHz;
    public final double f0VariabilityHz;
    public final double pitchConfidence;
    public final String quality;
    public final String qualityFlags;

    public AudioSummaryRecord(AudioMeasurementResult result) {
        algorithmVersion = AudioMeasurementResult.ALGORITHM_VERSION;
        computedAtWallClockMs = result.computedAtWallClockMs;
        sampleRateHz = result.sampleRateHz;
        rmsDbfs = result.rmsDbfs;
        peakDbfs = result.peakDbfs;
        noiseFloorDbfs = result.noiseFloorDbfs;
        snrDb = result.snrDb;
        clippingDetected = result.clippingDetected;
        speechActiveDurationMs = result.speechActiveDurationMs;
        pauseCount = result.pauseCount;
        pauseDurationMs = result.pauseDurationMs;
        medianF0Hz = result.medianF0Hz;
        f0MinHz = result.f0MinHz;
        f0MaxHz = result.f0MaxHz;
        f0VariabilityHz = result.f0VariabilityHz;
        pitchConfidence = result.pitchConfidence;
        quality = result.quality;
        qualityFlags = String.join(", ", result.qualityFlags);
    }

    public AudioSummaryRecord(JSONObject json) {
        algorithmVersion = json.optString("algorithmVersion", "unknown");
        computedAtWallClockMs = json.optLong("computedAtWallClockMs", 0L);
        sampleRateHz = json.optInt("sampleRateHz", 0);
        rmsDbfs = json.optDouble("rmsDbfs", Double.NaN);
        peakDbfs = json.optDouble("peakDbfs", Double.NaN);
        noiseFloorDbfs = json.optDouble("noiseFloorDbfs", Double.NaN);
        snrDb = json.optDouble("snrDb", Double.NaN);
        clippingDetected = json.optBoolean("clippingDetected", false);
        speechActiveDurationMs = json.optDouble("speechActiveDurationMs", Double.NaN);
        pauseCount = json.optInt("pauseCount", 0);
        pauseDurationMs = json.optDouble("pauseDurationMs", Double.NaN);
        medianF0Hz = json.optDouble("medianF0Hz", Double.NaN);
        f0MinHz = json.optDouble("f0MinHz", Double.NaN);
        f0MaxHz = json.optDouble("f0MaxHz", Double.NaN);
        f0VariabilityHz = json.optDouble("f0VariabilityHz", Double.NaN);
        pitchConfidence = json.optDouble("pitchConfidence", Double.NaN);
        quality = json.optString("quality", "Unavailable");
        qualityFlags = json.optString("qualityFlags", "");
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("algorithmVersion", algorithmVersion);
        json.put("computedAtWallClockMs", computedAtWallClockMs);
        json.put("sampleRateHz", sampleRateHz);
        json.put("rmsDbfs", rmsDbfs);
        json.put("peakDbfs", peakDbfs);
        json.put("noiseFloorDbfs", noiseFloorDbfs);
        json.put("snrDb", snrDb);
        json.put("clippingDetected", clippingDetected);
        json.put("speechActiveDurationMs", speechActiveDurationMs);
        json.put("pauseCount", pauseCount);
        json.put("pauseDurationMs", pauseDurationMs);
        json.put("medianF0Hz", medianF0Hz);
        json.put("f0MinHz", f0MinHz);
        json.put("f0MaxHz", f0MaxHz);
        json.put("f0VariabilityHz", f0VariabilityHz);
        json.put("pitchConfidence", pitchConfidence);
        json.put("quality", quality);
        json.put("qualityFlags", qualityFlags);
        return json;
    }
}
