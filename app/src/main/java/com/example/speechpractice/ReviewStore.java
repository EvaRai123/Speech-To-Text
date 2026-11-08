package com.example.speechpractice;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ReviewStore {
    private static final String PREFS = "speech_practice_reviews";
    private static final String HISTORY = "session_history";

    private ReviewStore() { }

    public static boolean save(Context context, SessionRecord session) {
        try {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray history = new JSONArray(preferences.getString(HISTORY, "[]"));
            history.put(session.toJson());
            preferences.edit().putString(HISTORY, history.toString()).apply();
            return true;
        } catch (JSONException exception) {
            return false;
        }
    }

    public static int savedSessionCount(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(HISTORY, "[]")).length();
        } catch (JSONException exception) {
            return 0;
        }
    }

    public static SessionRecord loadLatest(Context context) {
        try {
            JSONArray history = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(HISTORY, "[]"));
            if (history.length() == 0) return null;

            JSONObject storedSession = history.getJSONObject(history.length() - 1);
            SessionRecord session = new SessionRecord(
                    storedSession.optString("sessionId", ""),
                    storedSession.optLong("startedAtWallClock", 0L),
                    storedSession.optLong("startedAtElapsed", 0L));
            session.finishedAtWallClock = storedSession.optLong("finishedAtWallClock", 0L);
            session.finishedAtElapsed = storedSession.optLong("finishedAtElapsed", 0L);

            JSONArray storedAttempts = storedSession.optJSONArray("attempts");
            if (storedAttempts != null) {
                for (int i = 0; i < storedAttempts.length(); i++) {
                    JSONObject storedAttempt = storedAttempts.optJSONObject(i);
                    if (storedAttempt == null) continue;
                    AttemptRecord attempt = new AttemptRecord(
                            storedAttempt.optString("promptId", ""),
                            storedAttempt.optString("targetWord", ""));
                    attempt.attemptsStarted = storedAttempt.optInt("attemptsStarted", 0);
                    attempt.retries = storedAttempt.optInt("retries", 0);
                    attempt.transcript = storedAttempt.optString("transcript", "");
                    attempt.machineTranscript = storedAttempt.optString("machineTranscript", "");
                    attempt.partialOnly = storedAttempt.optBoolean("partialOnly", false);
                    attempt.manuallyEdited = storedAttempt.optBoolean("manuallyEdited", false);
                    attempt.status = storedAttempt.optString("status", "not_attempted");
                    attempt.errorMessage = storedAttempt.optString("errorMessage", "");
                    attempt.errorCode = storedAttempt.optInt("errorCode", 0);
                    attempt.promptDisplayedAt = storedAttempt.optLong("promptDisplayedAt", 0L);
                    attempt.requestStartedAt = storedAttempt.optLong("requestStartedAt", 0L);
                    attempt.firstSpeechAt = storedAttempt.optLong("firstSpeechAt", 0L);
                    attempt.endOfSpeechAt = storedAttempt.optLong("endOfSpeechAt", 0L);
                    attempt.finalResultAt = storedAttempt.optLong("finalResultAt", 0L);
                    attempt.errorAt = storedAttempt.optLong("errorAt", 0L);
                    JSONObject audio = storedAttempt.optJSONObject("audioSummary");
                    if (audio != null) attempt.audioSummary = new AudioSummaryRecord(audio);
                    session.attempts.add(attempt);
                }
            }
            return session;
        } catch (JSONException exception) {
            return null;
        }
    }
}
