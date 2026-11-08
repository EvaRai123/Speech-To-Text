package com.example.speechpractice;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CalibrationProfileStore {
    private static final String TAG = "CalibrationProfileStore";
    private static final String PREFS = "speechbridge_calibration_profiles";
    private static final String PARTICIPANT_KEY = "local_participant_id";
    private static final String PROFILES_KEY = "profiles";

    private CalibrationProfileStore() {
    }

    public static String getOrCreateParticipantId(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String current = preferences.getString(PARTICIPANT_KEY, null);
        if (current != null && !current.trim().isEmpty()) return current;
        String generated = UUID.randomUUID().toString();
        if (!preferences.edit().putString(PARTICIPANT_KEY, generated).commit()) {
            throw new IllegalStateException("Could not save local participant identifier.");
        }
        return generated;
    }

    public static String getParticipantId(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PARTICIPANT_KEY, null);
    }

    public static void save(Context context, PersonalBaseline baseline) {
        if (context == null || baseline == null) throw new IllegalArgumentException("Context and baseline are required.");
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray profiles;
        try {
            profiles = new JSONArray(preferences.getString(PROFILES_KEY, "[]"));
            JSONObject replacement = toJson(baseline);
            int index = -1;
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject item = profiles.optJSONObject(i);
                if (item != null && baseline.baselineId.equals(item.optString("baselineId"))) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) profiles.put(index, replacement);
            else profiles.put(replacement);
        } catch (JSONException exception) {
            Log.e(TAG, "Could not read calibration profile history; no profile was overwritten.", exception);
            throw new IllegalStateException("Calibration profile history is unreadable.", exception);
        }
        if (!preferences.edit().putString(PROFILES_KEY, profiles.toString()).commit()) {
            throw new IllegalStateException("Could not save calibration profile.");
        }
    }

    public static List<PersonalBaseline> loadAll(Context context, String participantId) {
        if (context == null || participantId == null || participantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Context and participant ID are required.");
        }
        List<PersonalBaseline> profiles = new ArrayList<>();
        JSONArray stored;
        try {
            stored = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PROFILES_KEY, "[]"));
        } catch (JSONException exception) {
            Log.e(TAG, "Could not read calibration profile history.", exception);
            throw new IllegalStateException("Calibration profile history is unreadable.", exception);
        }
        for (int i = 0; i < stored.length(); i++) {
            try {
                PersonalBaseline profile = fromJson(stored.getJSONObject(i));
                if (participantId.equals(profile.participantId)) profiles.add(profile);
            } catch (JSONException | IllegalArgumentException exception) {
                Log.w(TAG, "Skipping malformed calibration profile at index " + i, exception);
            }
        }
        return profiles;
    }

    private static JSONObject toJson(PersonalBaseline baseline) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("baselineId", baseline.baselineId);
        item.put("participantId", baseline.participantId);
        item.put("source", baseline.source.name());
        item.put("taskId", baseline.taskId);
        item.put("configurationId", baseline.configurationId);
        item.put("createdAtWallClockMs", baseline.createdAtWallClockMs);
        item.put("updatedAtWallClockMs", baseline.updatedAtWallClockMs);
        item.put("expiresAtWallClockMs", baseline.expiresAtWallClockMs);
        JSONObject values = new JSONObject();
        JSONObject counts = new JSONObject();
        for (MeasurementType type : baseline.values.keySet()) {
            values.put(type.name(), baseline.values.get(type));
            counts.put(type.name(), baseline.sampleCounts.get(type));
        }
        item.put("values", values);
        item.put("sampleCounts", counts);
        JSONArray history = new JSONArray();
        for (PersonalBaseline.BaselineUpdate update : baseline.updateHistory) {
            JSONObject entry = new JSONObject();
            entry.put("type", update.type.name());
            entry.put("value", update.value);
            entry.put("recordedAtWallClockMs", update.recordedAtWallClockMs);
            entry.put("attemptId", update.attemptId);
            history.put(entry);
        }
        item.put("updateHistory", history);
        return item;
    }

    private static PersonalBaseline fromJson(JSONObject item) throws JSONException {
        String participant = item.getString("participantId");
        MeasurementSource source = MeasurementSource.valueOf(item.getString("source"));
        String task = item.getString("taskId");
        String configuration = item.getString("configurationId");
        long created = item.getLong("createdAtWallClockMs");
        long updated = item.getLong("updatedAtWallClockMs");
        long expires = item.getLong("expiresAtWallClockMs");
        JSONObject storedValues = item.getJSONObject("values");
        JSONObject storedCounts = item.getJSONObject("sampleCounts");
        java.util.Map<MeasurementType, Double> values = new java.util.EnumMap<>(MeasurementType.class);
        java.util.Map<MeasurementType, Integer> counts = new java.util.EnumMap<>(MeasurementType.class);
        JSONArray names = storedValues.names();
        if (names == null) throw new IllegalArgumentException("Baseline has no measurements.");
        for (int i = 0; i < names.length(); i++) {
            MeasurementType type = MeasurementType.valueOf(names.getString(i));
            values.put(type, storedValues.getDouble(type.name()));
            counts.put(type, storedCounts.getInt(type.name()));
        }
        JSONArray storedHistory = item.optJSONArray("updateHistory");
        List<PersonalBaseline.BaselineUpdate> history = new ArrayList<>();
        if (storedHistory != null) {
            for (int i = 0; i < storedHistory.length(); i++) {
                JSONObject entry = storedHistory.getJSONObject(i);
                history.add(new PersonalBaseline.BaselineUpdate(MeasurementType.valueOf(entry.getString("type")),
                        entry.getDouble("value"), entry.getLong("recordedAtWallClockMs"),
                        entry.optString("attemptId", "")));
            }
        }
        return PersonalBaseline.restore(item.getString("baselineId"), participant, source, task,
                configuration, created, updated, expires, values, counts, history);
    }
}
