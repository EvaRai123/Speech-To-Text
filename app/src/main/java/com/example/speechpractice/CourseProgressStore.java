package com.example.speechpractice;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.text.DateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CourseProgressStore {
    private static final String TAG = "CourseProgressStore";
    private static final String PREFS = "speechbridge_course_progress";
    private static final String MASTER_KEY = "completed_lessons";
    private static final String ATTEMPTS_KEY = "lesson_attempts";
    private static final String DAILY_GOAL_KEY = "daily_goal";
    static final String CAMERA_MEASUREMENT_STATUS_KEY = "cameraMeasurementStatus";

    private CourseProgressStore() {
    }

    public static void markLessonComplete(Context context, String lessonId) {
        if (context == null || lessonId == null || lessonId.trim().isEmpty()) {
            Log.e(TAG, "Cannot mark a lesson complete without a context and lesson ID.");
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray current = new JSONArray(prefs.getString(MASTER_KEY, "[]"));
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < current.length(); i++) {
                JSONObject entry = current.getJSONObject(i);
                String id = entry.optString("lessonId", "");
                if (!id.isEmpty()) seen.add(id);
            }
            if (!seen.contains(lessonId)) {
                JSONObject entry = new JSONObject();
                entry.put("lessonId", lessonId);
                entry.put("completedAt", System.currentTimeMillis());
                current.put(entry);
                prefs.edit().putString(MASTER_KEY, current.toString()).apply();
            }
        } catch (JSONException exception) {
            Log.e(TAG, "Could not update lesson completion; the existing record was left unchanged.", exception);
            return;
        }
        prefs.edit().putInt(DAILY_GOAL_KEY, Math.max(1, prefs.getInt(DAILY_GOAL_KEY, 1))).apply();
    }

    public static String beginLessonAttempt(Context context, String lessonId) {
        String attemptId = lessonId + "_" + UUID.randomUUID();
        updateLessonAttempt(context, attemptId, lessonId, "in_progress", 0, 0, 0, 0L, null, null);
        return attemptId;
    }

    public static void updateLessonAttempt(Context context, String attemptId, String lessonId,
                                           String status, int completedSteps, int activityAttempts, int skippedSteps,
                                           long durationMs, String measurementSummary,
                                           String selfAssessment) {
        if (context == null || attemptId == null || lessonId == null) {
            Log.e(TAG, "Cannot save lesson activity without a context, attempt ID, and lesson ID.");
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray attempts;
        try {
            attempts = new JSONArray(prefs.getString(ATTEMPTS_KEY, "[]"));
            JSONObject attempt = null;
            for (int i = 0; i < attempts.length(); i++) {
                JSONObject existing = attempts.optJSONObject(i);
                if (existing != null && attemptId.equals(existing.optString("attemptId"))) {
                    attempt = existing;
                    break;
                }
            }
            if (attempt == null) {
                attempt = new JSONObject();
                attempt.put("attemptId", attemptId);
                attempt.put("lessonId", lessonId);
                attempt.put("startedAt", System.currentTimeMillis());
                attempts.put(attempt);
            }
            attempt.put("status", status);
            attempt.put("updatedAt", System.currentTimeMillis());
            if (!"in_progress".equals(status) && attempt.optLong("endedAt", 0L) == 0L) {
                attempt.put("endedAt", System.currentTimeMillis());
            }
            attempt.put("completedSteps", Math.max(0, completedSteps));
            attempt.put("activityAttempts", Math.max(0, activityAttempts));
            attempt.put("skippedSteps", Math.max(0, skippedSteps));
            attempt.put("durationMs", Math.max(0L, durationMs));
            if (measurementSummary != null && !measurementSummary.trim().isEmpty()) {
                JSONArray summaries = attempt.optJSONArray("measurementSummaries");
                if (summaries == null) summaries = new JSONArray();
                summaries.put(measurementSummary);
                attempt.put("measurementSummaries", summaries);
            }
            if (selfAssessment != null) attempt.put("selfAssessment", selfAssessment);
            prefs.edit().putString(ATTEMPTS_KEY, attempts.toString()).apply();
        } catch (JSONException exception) {
            Log.e(TAG, "Could not save lesson activity progress; existing records were left unchanged.", exception);
        }
    }

    public static void finishLessonAttempt(Context context, String attemptId, String lessonId,
                                           String status, int completedSteps, int activityAttempts,
                                           int skippedSteps, long durationMs, String selfAssessment) {
        LessonDefinition definition = LessonCatalog.getLesson(lessonId);
        if ("completed".equals(status) && definition != null
                && !LessonParticipationPolicy.canComplete(definition, completedSteps, skippedSteps)) {
            Log.w(TAG, "Keeping Area 9 attempt incomplete because it contains no practiced turns.");
            status = "incomplete";
        }
        updateLessonAttempt(context, attemptId, lessonId, status, completedSteps, activityAttempts,
                skippedSteps, durationMs, null, selfAssessment);
        if ("completed".equals(status)) markLessonComplete(context, lessonId);
    }

    public static int getLessonAttemptCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            return new JSONArray(prefs.getString(ATTEMPTS_KEY, "[]")).length();
        } catch (JSONException exception) {
            Log.e(TAG, "Could not read lesson activity history.", exception);
            return 0;
        }
    }

    public static void addLessonMeasurement(Context context, String attemptId, String lessonId,
                                            MeasurementRecord measurement) {
        if (context == null || attemptId == null || lessonId == null || measurement == null) {
            Log.e(TAG, "Cannot store lesson measurement without its attempt context.");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray attempts = new JSONArray(prefs.getString(ATTEMPTS_KEY, "[]"));
            JSONObject attempt = null;
            for (int i = 0; i < attempts.length(); i++) {
                JSONObject existing = attempts.optJSONObject(i);
                if (existing != null && attemptId.equals(existing.optString("attemptId"))) {
                    attempt = existing;
                    break;
                }
            }

            if (attempt == null) {
                Log.e(TAG, "Cannot attach a measurement to a missing lesson attempt.");
                return;
            }
            JSONArray measurements = attempt.optJSONArray("measurements");
            if (measurements == null) measurements = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("source", measurement.source.name());
            item.put("type", measurement.type.name());
            item.put("status", measurement.status.name());
            if (measurement.value == null) item.put("value", JSONObject.NULL);
            else item.put("value", measurement.value);
            if (measurement.unit == null) item.put("unit", JSONObject.NULL);
            else item.put("unit", measurement.unit);
            if (measurement.acquiredAtMonotonicNanos == null) {
                item.put("acquiredAtMonotonicNanos", JSONObject.NULL);
            } else {
                item.put("acquiredAtMonotonicNanos", measurement.acquiredAtMonotonicNanos);
            }
            if (measurement.windowStartMonotonicNanos == null) {
                item.put("windowStartMonotonicNanos", JSONObject.NULL);
                item.put("windowEndMonotonicNanos", JSONObject.NULL);
            } else {
                item.put("windowStartMonotonicNanos", measurement.windowStartMonotonicNanos);
                item.put("windowEndMonotonicNanos", measurement.windowEndMonotonicNanos);
            }
            if (measurement.receivedAtWallClockMs == null) item.put("receivedAtWallClockMs", JSONObject.NULL);
            else item.put("receivedAtWallClockMs", measurement.receivedAtWallClockMs);
            item.put("quality", measurement.quality.name());
            if (measurement.confidence == null) item.put("confidence", JSONObject.NULL);
            else item.put("confidence", measurement.confidence);
            item.put("detail", measurement.detail);
            measurements.put(item);
            attempt.put("measurements", measurements);
            prefs.edit().putString(ATTEMPTS_KEY, attempts.toString()).apply();
        } catch (JSONException exception) {
            Log.e(TAG, "Could not save lesson measurement; existing attempt data was left unchanged.", exception);
        }
    }

    public static void updateAttemptSourceStatus(Context context, String attemptId,
                                                 MeasurementSource source, String status,
                                                 String detail) {
        if (context == null || attemptId == null || source == null
                || status == null || status.trim().isEmpty()) {
            Log.e(TAG, "Cannot store source status without an attempt, source, and status.");
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray attempts = new JSONArray(prefs.getString(ATTEMPTS_KEY, "[]"));
            for (int i = 0; i < attempts.length(); i++) {
                JSONObject attempt = attempts.optJSONObject(i);
                if (attempt == null || !attemptId.equals(attempt.optString("attemptId"))) continue;
                JSONObject sourceStatuses = attempt.optJSONObject("sourceStatuses");
                if (sourceStatuses == null) sourceStatuses = new JSONObject();
                JSONObject sourceStatus = new JSONObject();
                sourceStatus.put("status", status);
                sourceStatus.put("detail", detail == null ? "" : detail);
                sourceStatuses.put(source.name(), sourceStatus);
                attempt.put("sourceStatuses", sourceStatuses);
                attempt.put("updatedAt", System.currentTimeMillis());
                if (!prefs.edit().putString(ATTEMPTS_KEY, attempts.toString()).commit()) {
                    Log.e(TAG, "Could not persist attempt source status.");
                }
                return;
            }
            Log.e(TAG, "Cannot update source status for a missing attempt.");
        } catch (JSONException exception) {
            Log.e(TAG, "Could not save attempt source status; existing progress was left unchanged.", exception);
        }
    }

    public static void updateAttemptSourceConfiguration(Context context, String attemptId,
                                                        MeasurementSource source,
                                                        String observedConfigurationId) {
        if (context == null || attemptId == null || source == null
                || observedConfigurationId == null || observedConfigurationId.trim().isEmpty()) {
            Log.e(TAG, "Cannot store source configuration without an attempt, source, and observed identifier.");
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray attempts = new JSONArray(prefs.getString(ATTEMPTS_KEY, "[]"));
            for (int i = 0; i < attempts.length(); i++) {
                JSONObject attempt = attempts.optJSONObject(i);
                if (attempt == null || !attemptId.equals(attempt.optString("attemptId"))) continue;
                JSONObject configurations = attempt.optJSONObject("sourceConfigurations");
                if (configurations == null) configurations = new JSONObject();
                String existing = configurations.optString(source.name(), "");
                if (!existing.isEmpty() && !existing.equals(observedConfigurationId)) {
                    Log.e(TAG, "Refusing to overwrite a different source configuration within one attempt.");
                    return;
                }
                configurations.put(source.name(), observedConfigurationId);
                attempt.put("sourceConfigurations", configurations);
                attempt.put("updatedAt", System.currentTimeMillis());
                if (!prefs.edit().putString(ATTEMPTS_KEY, attempts.toString()).commit()) {
                    Log.e(TAG, "Could not persist source configuration.");
                }
                return;
            }
            Log.e(TAG, "Cannot update configuration for a missing attempt.");
        } catch (JSONException exception) {
            Log.e(TAG, "Could not store source configuration.", exception);
        }
    }

    public static void updateLessonResearchComfortReport(Context context, String attemptId, String report) {
        if (context == null || attemptId == null || report == null || report.trim().isEmpty()) {
            Log.e(TAG, "Cannot store a missing research comfort report.");
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray attempts = new JSONArray(prefs.getString(ATTEMPTS_KEY, "[]"));
            for (int i = 0; i < attempts.length(); i++) {
                JSONObject attempt = attempts.optJSONObject(i);
                if (attempt != null && attemptId.equals(attempt.optString("attemptId"))) {
                    attempt.put("researchComfortReport", report);
                    attempt.put("updatedAt", System.currentTimeMillis());
                    if (!prefs.edit().putString(ATTEMPTS_KEY, attempts.toString()).commit()) {
                        Log.e(TAG, "Could not persist research comfort report.");
                    }
                    return;
                }
            }
            Log.e(TAG, "Cannot save comfort report for a missing attempt.");
        } catch (JSONException exception) {
            Log.e(TAG, "Could not save research comfort report.", exception);
        }
    }

    public static List<AttemptMeasurementSet> loadMeasurementAttempts(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        List<AttemptMeasurementSet> results = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray attempts;
        try {
            attempts = new JSONArray(prefs.getString(ATTEMPTS_KEY, "[]"));
        } catch (JSONException exception) {
            Log.e(TAG, "Could not read lesson measurement history.", exception);
            throw new IllegalStateException("Lesson measurement history is unreadable.", exception);
        }
        for (int i = 0; i < attempts.length(); i++) {
            JSONObject attempt = attempts.optJSONObject(i);
            if (attempt == null) continue;
            List<MeasurementRecord> measurements = new ArrayList<>();
            JSONArray savedMeasurements = attempt.optJSONArray("measurements");
            if (savedMeasurements != null) {
                for (int j = 0; j < savedMeasurements.length(); j++) {
                    JSONObject saved = savedMeasurements.optJSONObject(j);
                    if (saved == null) continue;
                    try {
                        MeasurementSource source = MeasurementSource.valueOf(saved.getString("source"));
                        MeasurementType type = MeasurementType.valueOf(saved.getString("type"));
                        MeasurementStatus status = MeasurementStatus.valueOf(saved.getString("status"));
                        if (status != MeasurementStatus.AVAILABLE) {
                            measurements.add(MeasurementRecord.unavailable(source, type, status,
                                    saved.optString("detail", "")));
                            continue;
                        }
                        double value = saved.getDouble("value");
                        String unit = saved.getString("unit");
                        MeasurementQuality quality = MeasurementQuality.valueOf(saved.optString("quality", "UNKNOWN"));
                        Double confidence = nullableDouble(saved, "confidence");
                        Long acquired = nullableLong(saved, "acquiredAtMonotonicNanos");
                        Long windowStart = nullableLong(saved, "windowStartMonotonicNanos");
                        Long windowEnd = nullableLong(saved, "windowEndMonotonicNanos");
                        Long received = nullableLong(saved, "receivedAtWallClockMs");
                        if (received == null) throw new IllegalArgumentException("Available record has no receipt timestamp.");
                        String detail = saved.optString("detail", "");
                        if (windowStart != null || windowEnd != null) {
                            if (windowStart == null || windowEnd == null || acquired != null) {
                                throw new IllegalArgumentException("Stored measurement window is incomplete.");
                            }
                            measurements.add(MeasurementRecord.availableWindow(source, type, value, unit,
                                    windowStart, windowEnd, received, quality, confidence, detail));
                        } else {
                            measurements.add(MeasurementRecord.available(source, type, value, unit,
                                    acquired, received, quality, confidence, detail));
                        }
                    } catch (JSONException | IllegalArgumentException exception) {
                        Log.w(TAG, "Skipping invalid persisted measurement at attempt index " + i
                                + ", measurement index " + j, exception);
                    }
                }
            }
            java.util.Map<MeasurementSource, String> configurations =
                    new java.util.EnumMap<>(MeasurementSource.class);
            java.util.Map<MeasurementSource, AttemptSourceStatus> sourceStatuses =
                    new java.util.EnumMap<>(MeasurementSource.class);
            JSONObject storedConfigurations = attempt.optJSONObject("sourceConfigurations");
            if (storedConfigurations != null) {
                for (MeasurementSource source : MeasurementSource.values()) {
                    String configuration = storedConfigurations.optString(source.name(), "");
                    if (!configuration.trim().isEmpty()) configurations.put(source, configuration);
                }
            }
            JSONObject storedSourceStatuses = attempt.optJSONObject("sourceStatuses");
            if (storedSourceStatuses != null) {
                for (MeasurementSource source : MeasurementSource.values()) {
                    JSONObject status = storedSourceStatuses.optJSONObject(source.name());
                    if (status != null) {
                        String statusValue = status.optString("status", "");
                        if (!statusValue.trim().isEmpty()) {
                            sourceStatuses.put(source, new AttemptSourceStatus(statusValue,
                                    status.optString("detail", "")));
                        }
                    }
                }
            }
            results.add(new AttemptMeasurementSet(attempt.optString("attemptId", ""),
                    attempt.optString("lessonId", ""), attempt.optString("status", ""),
                    attempt.optLong("startedAt", 0L), attempt.optLong("updatedAt", 0L),
                    attempt.optLong("endedAt", 0L),
                    attempt.optString(CAMERA_MEASUREMENT_STATUS_KEY, ""),
                    attempt.optString("selfAssessment", ""),
                    attempt.optString("researchComfortReport", ""), attempt.optInt("completedSteps", 0),
                    attempt.optInt("activityAttempts", 0), attempt.optInt("skippedSteps", 0),
                    configurations, sourceStatuses, measurements));
        }
        results.sort((first, second) -> Long.compare(second.updatedAtWallClockMs, first.updatedAtWallClockMs));
        return results;
    }

    private static Long nullableLong(JSONObject object, String key) throws JSONException {
        return object.has(key) && !object.isNull(key) ? object.getLong(key) : null;
    }

    private static Double nullableDouble(JSONObject object, String key) throws JSONException {
        if (!object.has(key) || object.isNull(key)) return null;
        double value = object.getDouble(key);
        return Double.isFinite(value) ? value : null;
    }

    public static void updateLessonCameraStatus(Context context, String attemptId, String status) {
        if (context == null || attemptId == null || status == null || status.trim().isEmpty()) {
            Log.e(TAG, "Cannot save camera status without an attempt and status.");
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray attempts = new JSONArray(prefs.getString(ATTEMPTS_KEY, "[]"));
            for (int i = 0; i < attempts.length(); i++) {
                JSONObject attempt = attempts.optJSONObject(i);
                if (attempt != null && attemptId.equals(attempt.optString("attemptId"))) {
                    attempt.put(CAMERA_MEASUREMENT_STATUS_KEY, status);
                    attempt.put("updatedAt", System.currentTimeMillis());
                    prefs.edit().putString(ATTEMPTS_KEY, attempts.toString()).apply();
                    return;
                }
            }
            Log.e(TAG, "Cannot update camera status for a missing lesson attempt.");
        } catch (JSONException exception) {
            Log.e(TAG, "Could not save camera status; existing attempt data was left unchanged.", exception);
        }
    }

    public static String getRecentLessonActivitySummary(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<JSONObject> recent = new ArrayList<>();
        try {
            JSONArray attempts = new JSONArray(prefs.getString(ATTEMPTS_KEY, "[]"));
            for (int i = 0; i < attempts.length(); i++) {
                JSONObject entry = attempts.optJSONObject(i);
                if (entry != null) recent.add(entry);
            }
        } catch (JSONException exception) {
            Log.e(TAG, "Could not read recent lesson activity.", exception);
            return "Recent lesson activity is unavailable.";
        }
        if (recent.isEmpty()) return "Recent lesson activity\nNo guided lesson attempts yet.";
        Collections.sort(recent, Comparator.comparingLong(
                (JSONObject entry) -> entry.optLong("updatedAt", 0L)).reversed());
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        StringBuilder summary = new StringBuilder("Recent lesson activity\n");
        int displayed = Math.min(5, recent.size());
        for (int i = 0; i < displayed; i++) {
            JSONObject attempt = recent.get(i);
            String lessonId = attempt.optString("lessonId", "");
            LessonDefinition lesson = LessonCatalog.getLesson(lessonId);
            String title = lesson == null ? "Guided activity" : lesson.title;
            String status = attempt.optString("status", "in_progress");
            summary.append("\n").append(title).append(" — ")
                    .append(status.replace('_', ' '))
                    .append(", ").append(attempt.optInt("completedSteps", 0))
                    .append(" of ").append(lesson == null ? "?" : lesson.prompts.size())
                    .append(" turns");
            String cameraStatus = attempt.optString(CAMERA_MEASUREMENT_STATUS_KEY, "");
            if (!cameraStatus.isEmpty()) {
                summary.append("\nCamera measurements: ")
                        .append(cameraStatus.toLowerCase().replace('_', ' '));
            }
            long updatedAt = attempt.optLong("updatedAt", 0L);
            if (updatedAt > 0) summary.append("\n").append(dateFormat.format(new Date(updatedAt)));
            summary.append("\n");
        }
        return summary.toString().trim();
    }

    public static boolean isLessonComplete(Context context, String lessonId) {
        if (context == null || lessonId == null) return false;
        for (String completeLesson : getCompletedLessonIds(context)) {
            if (completeLesson.equals(lessonId)) return true;
        }
        return false;
    }

    public static List<String> getCompletedLessonIds(Context context) {
        List<String> ids = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray current = new JSONArray(prefs.getString(MASTER_KEY, "[]"));
            for (int i = 0; i < current.length(); i++) {
                JSONObject entry = current.getJSONObject(i);
                String id = entry.optString("lessonId", "");
                if (!id.isEmpty()) ids.add(id);
            }
        } catch (JSONException exception) {
            Log.e(TAG, "Could not read lesson completion records.", exception);
        }
        return ids;
    }

    public static int getCompletedCount(Context context) {
        return getCompletedLessonIds(context).size();
    }

    public static int getCurrentStreak(Context context) {
        List<Long> timestamps = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray current = new JSONArray(prefs.getString(MASTER_KEY, "[]"));
            for (int i = 0; i < current.length(); i++) {
                JSONObject entry = current.getJSONObject(i);
                long time = entry.optLong("completedAt", 0L);
                if (time > 0L) timestamps.add(time);
            }
        } catch (JSONException exception) {
            Log.e(TAG, "Could not read lesson dates for the activity streak.", exception);
            return 0;
        }
        if (timestamps.isEmpty()) return 0;
        Collections.sort(timestamps, Collections.reverseOrder());

        Set<String> uniqueDates = new HashSet<>();
        for (Long value : timestamps) {
            uniqueDates.add(dateKey(value));
        }
        List<String> sortedDates = new ArrayList<>(uniqueDates);
        Collections.sort(sortedDates, Collections.reverseOrder());

        int streak = 0;
        String today = dateKey(System.currentTimeMillis());
        String cursor = today;
        for (String date : sortedDates) {
            if (date.equals(cursor)) {
                streak++;
                cursor = previousDay(cursor);
            }
        }
        return streak;
    }

    public static int getDailyGoal(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(DAILY_GOAL_KEY, 1);
    }

    public static void setDailyGoal(Context context, int goal) {
        if (goal < 1) goal = 1;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(DAILY_GOAL_KEY, goal).apply();
    }

    public static int getCompletionPercent(Context context, int totalLessons) {
        if (totalLessons <= 0) return 0;
        return Math.min(100, (getCompletedCount(context) * 100) / totalLessons);
    }

    public static List<String> getRecentAchievements(Context context) {
        List<String> achievements = new ArrayList<>();
        int completed = getCompletedCount(context);
        if (completed >= 1) achievements.add("First lesson completed");
        if (completed >= 5) achievements.add("Five lessons completed");
        if (completed >= 10) achievements.add("Ten lessons completed");
        if (completed >= 20) achievements.add("Twenty lessons completed");
        if (completed >= 40) achievements.add("Full curriculum started");
        return achievements;
    }

    private static String dateKey(long timestamp) {
        Date date = new Date(timestamp);
        return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
    }

    private static String previousDay(String isoDate) {
        try {
            java.time.LocalDate localDate = java.time.LocalDate.parse(isoDate);
            return localDate.minusDays(1).toString();
        } catch (Exception ignored) {
            return isoDate;
        }
    }
}
