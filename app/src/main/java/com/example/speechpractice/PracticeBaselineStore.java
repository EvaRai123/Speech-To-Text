package com.example.speechpractice;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class PracticeBaselineStore {
    private static final String PREFS = "speechbridge_practice_baselines";

    private PracticeBaselineStore() {
    }

    public static void save(Context context, PracticeSession session) {
        if (session == null || session.exerciseType == null || session.microphoneMeasurement == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = "exercise_" + session.exerciseType.name();
        JSONArray history = new JSONArray();
        String existing = prefs.getString(key, "[]");
        try {
            JSONArray current = new JSONArray(existing);
            for (int i = 0; i < current.length(); i++) {
                history.put(current.getJSONObject(i));
            }
            history.put(toJson(session));
            prefs.edit().putString(key, history.toString()).apply();
        } catch (Exception ignored) {
            prefs.edit().putString(key, "[]").apply();
        }
    }

    public static List<PracticeSession> load(Context context, PracticeExerciseType exerciseType) {
        if (exerciseType == null) return new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = "exercise_" + exerciseType.name();
        List<PracticeSession> sessions = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                long startedAtMs = item.optLong("startedAtMs", 0L);
                long finishedAtMs = item.optLong("finishedAtMs", 0L);
                long recordedAtMs = item.optLong("recordedAtMs", 0L);
                long durationMs = Math.max(0L, item.optLong("durationMs", 0L));
                double rmsDbfs = item.optDouble("rmsDbfs", Double.NaN);
                double peakDbfs = item.optDouble("peakDbfs", Double.NaN);
                double noiseFloorDbfs = item.optDouble("noiseFloorDbfs", Double.NaN);
                double snrDb = item.optDouble("snrDb", Double.NaN);
                double speechActiveDurationMs = item.optDouble("speechActiveDurationMs", Double.NaN);
                double estimatedF0Hz = item.optDouble("estimatedF0Hz", Double.NaN);
                String quality = item.optString("quality", "");
                String qualityFlags = item.optString("qualityFlags", "");
                boolean available = item.has("rmsDbfs") || item.has("quality");
                MicrophoneMeasurement measurement = new MicrophoneMeasurement(recordedAtMs, durationMs,
                        rmsDbfs, peakDbfs, noiseFloorDbfs, snrDb, speechActiveDurationMs,
                        estimatedF0Hz, quality, qualityFlags, available);
                sessions.add(new PracticeSession(item.optString("sessionId", "baseline-" + i), startedAtMs, finishedAtMs,
                        exerciseType, measurement, null, null, new ArrayList<>(), new ArrayList<>()));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return sessions;
    }

    private static JSONObject toJson(PracticeSession session) throws Exception {
        JSONObject item = new JSONObject();
        item.put("sessionId", session.sessionId);
        item.put("startedAtMs", session.startedAtMs);
        item.put("finishedAtMs", session.finishedAtMs);
        item.put("recordedAtMs", session.microphoneMeasurement != null ? session.microphoneMeasurement.recordedAtMs : 0L);
        item.put("durationMs", session.microphoneMeasurement != null ? session.microphoneMeasurement.durationMs : 0L);
        if (session.microphoneMeasurement != null) {
            putFinite(item, "rmsDbfs", session.microphoneMeasurement.rmsDbfs);
            putFinite(item, "peakDbfs", session.microphoneMeasurement.peakDbfs);
            putFinite(item, "noiseFloorDbfs", session.microphoneMeasurement.noiseFloorDbfs);
            putFinite(item, "snrDb", session.microphoneMeasurement.snrDb);
            putFinite(item, "speechActiveDurationMs", session.microphoneMeasurement.speechActiveDurationMs);
            putFinite(item, "estimatedF0Hz", session.microphoneMeasurement.estimatedF0Hz);
        }
        item.put("quality", session.microphoneMeasurement != null ? session.microphoneMeasurement.quality : "");
        item.put("qualityFlags", session.microphoneMeasurement != null ? session.microphoneMeasurement.qualityFlags : "");
        return item;
    }

    private static void putFinite(JSONObject item, String key, double value) throws Exception {
        if (!Double.isNaN(value) && !Double.isInfinite(value)) item.put(key, value);
    }
}
