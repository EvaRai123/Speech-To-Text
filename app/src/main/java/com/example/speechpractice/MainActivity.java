package com.example.speechpractice;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String ONBOARDING_PREFS = "speechbridge_onboarding";
    private static final String ONBOARDING_COMPLETE = "complete";
    private TextView progressSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (showFirstTimeIntroduction()) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        progressSummary = findViewById(R.id.home_progress_summary);

        findViewById(R.id.start_practice_button).setOnClickListener(v ->
                startActivity(new Intent(this, PracticeListActivity.class)));
        findViewById(R.id.view_progress_button).setOnClickListener(v ->
                startActivity(new Intent(this, ProgressDashboardActivity.class)));
        findViewById(R.id.about_button).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.curriculum_button).setOnClickListener(v ->
                startActivity(new Intent(this, CurriculumActivity.class)));
        findViewById(R.id.learn_button).setOnClickListener(v ->
                startActivity(new Intent(this, LearnActivity.class)));
        findViewById(R.id.research_workspace_button).setOnClickListener(v ->
                startActivity(new Intent(this, ResearchWorkspaceActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (progressSummary != null) {
            progressSummary.setText(CourseProgressStore.getCompletedCount(this)
                    + " of " + LessonCatalog.getLessonCount()
                    + " lessons tried. You can practise any lesson again.");
        }
    }

    private boolean showFirstTimeIntroduction() {
        android.content.SharedPreferences onboarding = getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE);
        if (onboarding.getBoolean(ONBOARDING_COMPLETE, false)) return false;

        boolean hasExistingActivity = ReviewStore.savedSessionCount(this) > 0
                || CourseProgressStore.getCompletedCount(this) > 0
                || !ResearchSessionStore.loadAll(this).isEmpty();
        for (PracticeExerciseType type : PracticeExerciseType.values()) {
            hasExistingActivity = hasExistingActivity || !PracticeBaselineStore.load(this, type).isEmpty();
        }
        if (hasExistingActivity) {
            onboarding.edit().putBoolean(ONBOARDING_COMPLETE, true).apply();
            return false;
        }
        return true;
    }
}
