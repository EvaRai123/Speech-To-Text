package com.example.speechpractice;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ResearchSessionStore {
    private static final String TAG = "ResearchSessionStore";
    private static final String PREFS = "speechbridge_research_sessions";
    private static final String KEY = "sessions";

    private ResearchSessionStore() {
    }

    public static void save(Context context, ResearchSession session) {
        if (context == null || session == null) {
            throw new IllegalArgumentException("Research session context and record are required.");
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray history;
        try {
            history = new JSONArray(prefs.getString(KEY, "[]"));
        } catch (JSONException exception) {
            Log.e(TAG, "Research session history is unreadable; refusing to replace it.", exception);
            throw new IllegalStateException("Research session history is unreadable.", exception);
        }
        try {
            history.put(toJson(session));
        } catch (JSONException exception) {
            Log.e(TAG, "Research session could not be serialized.", exception);
            throw new IllegalStateException("Research session could not be serialized.", exception);
        }
        if (!prefs.edit().putString(KEY, history.toString()).commit()) {
            Log.e(TAG, "Research session history could not be persisted.");
            throw new IllegalStateException("Research session history could not be persisted.");
        }
    }

    public static List<ResearchSession> loadAll(Context context) {
        if (context == null) throw new IllegalArgumentException("Research session context is required.");
        List<ResearchSession> results = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final JSONArray array;
        try {
            array = new JSONArray(prefs.getString(KEY, "[]"));
        } catch (JSONException exception) {
            Log.e(TAG, "Research session history is unreadable.", exception);
            throw new IllegalStateException("Research session history is unreadable.", exception);
        }
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject item = array.getJSONObject(i);
                ResearchSession session = fromJson(item);
                if (session != null) results.add(session);
            } catch (JSONException | IllegalArgumentException exception) {
                Log.w(TAG, "Skipping invalid research session at index " + i + ".", exception);
            }
        }
        return results;
    }

    private static JSONObject toJson(ResearchSession session) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("sessionId", session.sessionId);
        item.put("condition", session.condition != null ? session.condition.name() : ResearchCondition.MICROPHONE_ONLY.name());
        item.put("exerciseType", session.exerciseType != null ? session.exerciseType.name() : PracticeExerciseType.SUSTAINED_VOWEL.name());
        item.put("startedAtMs", session.startedAtMs);
        item.put("finishedAtMs", session.finishedAtMs);
        item.put("completed", session.completed);
        item.put("notes", session.notes == null ? "" : session.notes);
        if (session.microphoneMeasurement != null) {
            item.put("rmsDbfs", session.microphoneMeasurement.rmsDbfs);
            item.put("peakDbfs", session.microphoneMeasurement.peakDbfs);
            item.put("quality", session.microphoneMeasurement.quality);
            item.put("speechActiveDurationMs", session.microphoneMeasurement.speechActiveDurationMs);
            item.put("durationMs", session.microphoneMeasurement.durationMs);
            item.put("noiseFloorDbfs", session.microphoneMeasurement.noiseFloorDbfs);
            item.put("snrDb", session.microphoneMeasurement.snrDb);
        }
        if (session.accelerometerMeasurement != null) {
            item.put("accelerometerAvailable", session.accelerometerMeasurement.available);
            item.put("accelerometerStatus", session.accelerometerMeasurement.status);
        }
        if (session.emgMeasurement != null) {
            item.put("emgAvailable", session.emgMeasurement.available);
            item.put("emgStatus", session.emgMeasurement.status);
        }
        JSONArray states = new JSONArray();
        for (PracticeMeasurementState state : session.measurementStates) {
            JSONObject stateObject = new JSONObject();
            stateObject.put("sourceName", state.sourceName);
            stateObject.put("source", state.source.name());
            stateObject.put("availability", state.availability.name());
            stateObject.put("statusMessage", state.statusMessage);
            stateObject.put("lastUpdateMs", state.lastUpdateMs);
            stateObject.put("details", state.details);
            states.put(stateObject);
        }
        item.put("measurementStates", states);
        return item;
    }

    private static ResearchSession fromJson(JSONObject item) {
        try {
            ResearchCondition condition = ResearchCondition.fromName(item.optString("condition", ResearchCondition.MICROPHONE_ONLY.name()));
            PracticeExerciseType exerciseType = PracticeExerciseType.fromName(item.optString("exerciseType", PracticeExerciseType.SUSTAINED_VOWEL.name()));
            long startedAtMs = item.optLong("startedAtMs", 0L);
            long finishedAtMs = item.optLong("finishedAtMs", 0L);
            boolean completed = item.optBoolean("completed", false);
            String notes = item.optString("notes", "");

            MicrophoneMeasurement mic = null;
            if (item.has("durationMs") || item.has("rmsDbfs") || item.has("quality")) {
                mic = new MicrophoneMeasurement(item.optLong("recordedAtMs", startedAtMs),
                        Math.max(0L, item.optLong("durationMs", 0L)),
                        item.optDouble("rmsDbfs", Double.NaN),
                        item.optDouble("peakDbfs", Double.NaN),
                        item.optDouble("noiseFloorDbfs", Double.NaN),
                        item.optDouble("snrDb", Double.NaN),
                        item.optDouble("speechActiveDurationMs", 0.0),
                        item.optDouble("estimatedF0Hz", Double.NaN),
                        item.optString("quality", "Unavailable"),
                        item.optString("qualityFlags", ""),
                        item.has("durationMs") || item.has("rmsDbfs") || item.has("quality"));
            }

            SensorMeasurement accel = null;
            if (item.has("accelerometerAvailable") || item.has("accelerometerStatus")) {
                accel = new SensorMeasurement("Accelerometer", startedAtMs, 0L, item.optBoolean("accelerometerAvailable", false),
                        item.optString("accelerometerStatus", "Unavailable"), "Condition requires hardware not yet connected.");
            }

            SensorMeasurement emg = null;
            if (item.has("emgAvailable") || item.has("emgStatus")) {
                emg = new SensorMeasurement("Surface EMG", startedAtMs, 0L, item.optBoolean("emgAvailable", false),
                        item.optString("emgStatus", "Unavailable"), "Condition requires hardware not yet connected.");
            }

            List<PracticeMeasurementState> states = new ArrayList<>();
            JSONArray stateArray = item.optJSONArray("measurementStates");
            if (stateArray != null) {
                for (int i = 0; i < stateArray.length(); i++) {
                    JSONObject stateItem = stateArray.getJSONObject(i);
                    PracticeMeasurementState.MeasurementSource source = PracticeMeasurementState.MeasurementSource.UNKNOWN;
                    try {
                        source = PracticeMeasurementState.MeasurementSource.valueOf(stateItem.optString("source", "UNKNOWN"));
                    } catch (IllegalArgumentException ignored) {
                    }
                    PracticeMeasurementState.Availability availability = PracticeMeasurementState.Availability.UNKNOWN;
                    try {
                        availability = PracticeMeasurementState.Availability.valueOf(stateItem.optString("availability", "UNKNOWN"));
                    } catch (IllegalArgumentException ignored) {
                    }
                    states.add(new PracticeMeasurementState(
                            stateItem.optString("sourceName", "Source"),
                            source,
                            availability,
                            stateItem.optString("statusMessage", "Unknown"),
                            stateItem.optLong("lastUpdateMs", 0L),
                            stateItem.optString("details", ""),
                            new ArrayList<>()));
                }
            }

            return new ResearchSession(item.optString("sessionId", "research_session_" + startedAtMs),
                    condition, exerciseType, startedAtMs, finishedAtMs, completed, mic, accel, emg, states, notes);
        } catch (JSONException | IllegalArgumentException exception) {
            Log.w(TAG, "Could not decode a saved research session.", exception);
            return null;
        }
    }
}
