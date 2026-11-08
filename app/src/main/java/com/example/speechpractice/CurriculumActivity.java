package com.example.speechpractice;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class CurriculumActivity extends AppCompatActivity {
    private LinearLayout areaContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_curriculum);

        areaContainer = findViewById(R.id.curriculum_area_container);

        findViewById(R.id.curriculum_home_button).setOnClickListener(v -> {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            finish();
        });

        findViewById(R.id.curriculum_progress_button).setOnClickListener(v -> startActivity(new Intent(this, ProgressDashboardActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (areaContainer != null) buildAreas();
    }

    private void buildAreas() {
        areaContainer.removeAllViews();
        List<LessonCatalog.LessonArea> areas = LessonCatalog.getAreas();
        LessonDefinition recommended = null;
        LessonCatalog.LessonArea recommendedArea = null;
        for (LessonCatalog.LessonArea area : areas) {
            for (LessonDefinition lesson : area.lessons) {
                if (!CourseProgressStore.isLessonComplete(this, lesson.id)) {
                    recommended = lesson;
                    recommendedArea = area;
                    break;
                }
            }
            if (recommended != null) break;
        }

        TextView recommendation = findViewById(R.id.curriculum_recommendation);
        Button recommendedButton = findViewById(R.id.curriculum_recommended_button);
        if (recommended == null) {
            recommendation.setText("You have completed each lesson at least once. Choose any activity to practise again.");
            recommendedButton.setText("Practise the first lesson again");
            LessonCatalog.LessonArea firstArea = areas.isEmpty() ? null : areas.get(0);
            if (firstArea == null || firstArea.lessons.isEmpty()) {
                recommendedButton.setEnabled(false);
            } else {
                LessonDefinition firstLesson = firstArea.lessons.get(0);
                recommendedButton.setOnClickListener(v -> openLesson(firstLesson));
            }
        } else {
            recommendation.setText("Suggested next activity · " + recommendedArea.title + "\n"
                    + recommended.title + "\nYou can also choose any other lesson.");
            LessonDefinition nextLesson = recommended;
            recommendedButton.setText("Start: " + recommended.title);
            recommendedButton.setOnClickListener(v -> openLesson(nextLesson));
        }

        for (LessonCatalog.LessonArea area : areas) {
            int completed = 0;
            for (LessonDefinition lesson : area.lessons) {
                if (CourseProgressStore.isLessonComplete(this, lesson.id)) completed++;
            }

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(14);
            card.setLayoutParams(cardParams);
            card.setBackgroundResource(R.drawable.card_background);

            LinearLayout summary = new LinearLayout(this);
            summary.setOrientation(LinearLayout.HORIZONTAL);
            summary.setGravity(Gravity.CENTER_VERTICAL);
            ImageView image = new ImageView(this);
            image.setImageResource(VisualContent.areaIllustration(area.key));
            image.setContentDescription(area.title
                    + " practice illustration. It does not show a sensor measurement.");
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            summary.addView(image, new LinearLayout.LayoutParams(dp(96), dp(78)));

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            detailsParams.leftMargin = dp(12);
            summary.addView(details, detailsParams);

            TextView areaTitle = new TextView(this);
            areaTitle.setText(area.title);
            areaTitle.setTextColor(getColor(R.color.ink));
            areaTitle.setTextSize(20f);
            areaTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            details.addView(areaTitle);

            TextView count = new TextView(this);
            count.setText(completed + " of " + area.lessons.size() + " tried");
            count.setTextColor(getColor(R.color.purple));
            count.setTextSize(16f);
            details.addView(count);
            card.addView(summary);

            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(Math.max(1, area.lessons.size()));
            progress.setProgress(completed);
            progress.setContentDescription(area.title + " progress: " + completed
                    + " of " + area.lessons.size() + " lessons tried");
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
            progressParams.topMargin = dp(10);
            card.addView(progress, progressParams);

            TextView description = new TextView(this);
            description.setText(shortDescription(area.key));
            description.setTextSize(16f);
            description.setTextColor(getColor(R.color.ink));
            description.setPadding(0, dp(8), 0, dp(4));
            card.addView(description);

            Button areaButton = new Button(this);
            areaButton.setMinHeight(dp(52));
            areaButton.setText("Open " + area.title);
            areaButton.setAllCaps(false);
            areaButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, AreaLessonsActivity.class);
                intent.putExtra(AreaLessonsActivity.EXTRA_AREA_KEY, area.key);
                startActivity(intent);
            });
            card.addView(areaButton);
            areaContainer.addView(card);
        }
    }

    private String shortDescription(String key) {
        switch (key) {
            case "breath": return "Notice easy breaths and pauses.";
            case "phonation": return "Try gentle voice sounds.";
            case "pitch": return "Explore high and low sounds.";
            case "loudness": return "Listen to comfortable voice levels.";
            case "articulation": return "Practise clear sounds.";
            case "syllables": return "Try sounds, words, and short phrases.";
            case "pacing": return "Practise an easy speaking rhythm.";
            case "connected": return "Use speech in everyday situations.";
            case "mouth_articulation": return "Explore small, comfortable mouth movements.";
            default: return "Choose one short activity.";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openLesson(LessonDefinition lesson) {
        Intent intent = new Intent(this, LessonActivity.class);
        intent.putExtra(LessonActivity.EXTRA_LESSON_ID, lesson.id);
        startActivity(intent);
    }
}
