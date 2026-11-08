package com.example.speechpractice;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class ProgressDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress_dashboard);

        int totalLessons = 0;
        for (LessonCatalog.LessonArea area : LessonCatalog.getAreas()) totalLessons += area.lessons.size();

        int completed = CourseProgressStore.getCompletedCount(this);
        int streak = CourseProgressStore.getCurrentStreak(this);
        int dailyGoal = CourseProgressStore.getDailyGoal(this);
        int percent = CourseProgressStore.getCompletionPercent(this, totalLessons);
        LessonDefinition nextLesson = findNextLesson();
        String nextText = nextLesson == null
                ? "Suggested next activity: choose any lesson to practise again."
                : "Suggested next activity: " + nextLesson.areaTitle + " — " + nextLesson.title;

        TextView overview = findViewById(R.id.progress_overview);
        overview.setText("Lessons completed: " + completed + " / " + totalLessons
                + "\nActivities tried: " + CourseProgressStore.getLessonAttemptCount(this)
                + "\nCourse participation: " + percent + "%"
                + "\nPractice days in a row: " + streak
                + "\nDaily goal: " + dailyGoal + " lesson(s) per day"
                + "\n" + nextText);

        TextView achievements = findViewById(R.id.progress_achievements);
        StringBuilder builder = new StringBuilder("Achievements:\n");
        for (String achievement : CourseProgressStore.getRecentAchievements(this)) {
            builder.append("• ").append(achievement).append("\n");
        }
        if (builder.toString().equals("Achievements:\n")) builder.append("• Start your first lesson to unlock your first achievement.");
        achievements.setText(builder.toString());

        TextView note = findViewById(R.id.progress_note);
        note.setText("This progress dashboard reflects actual completed lesson sessions in the local app history. It does not imply clinical improvement or a medical recovery score.");
        TextView lessonHistory = findViewById(R.id.progress_lesson_history);
        lessonHistory.setText(CourseProgressStore.getRecentLessonActivitySummary(this));
        TextView sessions = findViewById(R.id.progress_sessions);
        int savedSessions = ReviewStore.savedSessionCount(this);
        List<PracticeSession> practiceSessions = loadPracticeSessions();
        savedSessions += practiceSessions.size();
        sessions.setText(savedSessions == 0
                ? "Speech-practice history: no saved sessions yet."
                : "Speech-practice history: " + savedSessions + " saved session(s). Recent microphone practice attempts are listed below.");
        TextView practiceHistory = findViewById(R.id.progress_practice_history);
        practiceHistory.setText(formatPracticeHistory(practiceSessions));

        findViewById(R.id.progress_home_button).setOnClickListener(v -> {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            finish();
        });

        findViewById(R.id.progress_curriculum_button).setOnClickListener(v -> {
            Intent curriculum = new Intent(this, CurriculumActivity.class);
            curriculum.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(curriculum);
            finish();
        });

        View historyButton = findViewById(R.id.progress_history_button);
        int savedReviewSessions = ReviewStore.savedSessionCount(this);
        if (savedReviewSessions == 0) {
            historyButton.setVisibility(View.GONE);
        } else {
            historyButton.setOnClickListener(v -> {
                SessionRecord latestSession = ReviewStore.loadLatest(this);
                if (latestSession != null) {
                    Intent review = new Intent(this, ReviewActivity.class);
                    review.putExtra(ReviewActivity.EXTRA_SESSION, latestSession);
                    startActivity(review);
                }
            });
        }
    }

    private LessonDefinition findNextLesson() {
        for (LessonCatalog.LessonArea area : LessonCatalog.getAreas()) {
            for (LessonDefinition lesson : area.lessons) {
                if (!CourseProgressStore.isLessonComplete(this, lesson.id)) return lesson;
            }
        }
        return null;
    }

    private List<PracticeSession> loadPracticeSessions() {
        List<PracticeSession> sessions = new ArrayList<>();
        for (PracticeExerciseType type : PracticeExerciseType.values()) {
            sessions.addAll(PracticeBaselineStore.load(this, type));
        }
        Collections.sort(sessions, Comparator.comparingLong((PracticeSession session) -> session.finishedAtMs).reversed());
        return sessions;
    }

    private String formatPracticeHistory(List<PracticeSession> sessions) {
        if (sessions.isEmpty()) {
            return "Recent voice practice\nNo saved microphone practice attempts yet. Complete an exercise to see its session summary here.";
        }
        StringBuilder text = new StringBuilder("Recent voice practice\n");
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        int displayed = Math.min(5, sessions.size());
        for (int i = 0; i < displayed; i++) {
            PracticeSession session = sessions.get(i);
            text.append("\n")
                    .append(dateFormat.format(new Date(session.finishedAtMs)))
                    .append(" — ")
                    .append(session.exerciseType == null ? "Voice exercise" : session.exerciseType.getLabel())
                    .append("\nRecording: ")
                    .append(session.microphoneMeasurement == null ? "unavailable" : session.microphoneMeasurement.durationMs + " ms");
            if (session.microphoneMeasurement != null && !Double.isNaN(session.microphoneMeasurement.speechActiveDurationMs)) {
                text.append("; detected audio activity: ")
                        .append(Math.round(session.microphoneMeasurement.speechActiveDurationMs))
                        .append(" ms");
            }
            if (session.microphoneMeasurement != null && !Double.isNaN(session.microphoneMeasurement.rmsDbfs)) {
                text.append("; average signal: ")
                        .append(String.format(java.util.Locale.US, "%.1f", session.microphoneMeasurement.rmsDbfs))
                        .append(" dBFS");
            }
            text.append("\n");
        }
        text.append("\nThese are microphone estimates from saved practice recordings, not clinical scores. Detected activity is based on sound level, not exact speaking time. The average signal level is relative to the phone recording and can change with microphone distance and room noise.");
        return text.toString();
    }
}
