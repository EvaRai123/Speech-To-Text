package com.example.speechpractice;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class AreaLessonsActivity extends AppCompatActivity {
    public static final String EXTRA_AREA_KEY = "extra_area_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_area_lessons);

        String areaKey = getIntent().getStringExtra(EXTRA_AREA_KEY);
        LessonCatalog.LessonArea area = LessonCatalog.getArea(areaKey);

        TextView title = findViewById(R.id.area_title);
        TextView description = findViewById(R.id.area_description);
        LinearLayout container = findViewById(R.id.area_lessons_container);

        if (area == null) {
            title.setText("Unknown lesson area");
            description.setText("The requested learning area is unavailable.");
            return;
        }

        title.setText(area.title);
        int completedInArea = 0;
        for (LessonDefinition lesson : area.lessons) {
            if (CourseProgressStore.isLessonComplete(this, lesson.id)) completedInArea++;
        }
        description.setText(shortDescription(area.key) + "\n" + completedInArea + " of "
                + area.lessons.size() + " lessons tried.");
        ImageView areaImage = new ImageView(this);
        areaImage.setImageResource(VisualContent.areaIllustration(area.key));
        areaImage.setContentDescription(area.title
                + " practice illustration. It does not show a sensor measurement.");
        areaImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        areaImage.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        container.addView(areaImage);

        String lastGroupTitle = null;
        for (LessonDefinition lesson : area.lessons) {
            if (!lesson.groupTitle.isEmpty() && !lesson.groupTitle.equals(lastGroupTitle)) {
                TextView groupHeading = new TextView(this);
                groupHeading.setText(lesson.groupTitle);
                groupHeading.setTextSize(18f);
                groupHeading.setTypeface(null, android.graphics.Typeface.BOLD);
                groupHeading.setTextColor(getColor(R.color.purple));
                groupHeading.setPadding(0, 12, 0, 8);
                container.addView(groupHeading);
                lastGroupTitle = lesson.groupTitle;
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 14);
            card.setLayoutParams(cardParams);
            card.setBackgroundResource(R.drawable.card_background);

            ImageView illustration = new ImageView(this);
            illustration.setImageResource(VisualContent.lessonIllustration(lesson));
            illustration.setContentDescription(VisualContent.lessonIllustrationDescription(lesson));
            illustration.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(illustration, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(110)));

            TextView lessonTitle = new TextView(this);
            lessonTitle.setText(lesson.title);
            lessonTitle.setTextSize(21f);
            lessonTitle.setTextColor(getResources().getColor(R.color.ink));
            lessonTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(lessonTitle);

            TextView meta = new TextView(this);
            String completed = CourseProgressStore.isLessonComplete(this, lesson.id) ? "Completed" : "Ready";
            meta.setText(lesson.prompts.size() + " short turns  ·  " + completed);
            meta.setTextSize(16f);
            meta.setTextColor(getResources().getColor(R.color.purple));
            card.addView(meta);

            TextView objective = new TextView(this);
            objective.setText("What will we do?\n" + lesson.objective);
            objective.setPadding(0, dp(8), 0, dp(8));
            objective.setTextSize(17f);
            objective.setTextColor(getResources().getColor(R.color.ink));
            card.addView(objective);

            TextView why = new TextView(this);
            why.setText("Why?\n" + lesson.educationalExplanation);
            why.setPadding(0, dp(4), 0, dp(8));
            why.setTextSize(16f);
            why.setTextColor(getResources().getColor(R.color.ink));
            why.setVisibility(View.GONE);
            card.addView(why);

            Button whyButton = new Button(this);
            whyButton.setText("Why are we doing this?");
            whyButton.setAllCaps(false);
            whyButton.setMinHeight(dp(48));
            whyButton.setOnClickListener(v -> {
                boolean expanded = why.getVisibility() == View.VISIBLE;
                why.setVisibility(expanded ? View.GONE : View.VISIBLE);
                whyButton.setText(expanded ? "Why are we doing this?" : "Hide why");
            });
            card.addView(whyButton);

            Button open = new Button(this);
            open.setText(CourseProgressStore.isLessonComplete(this, lesson.id)
                    ? "Practise again" : "Start this lesson");
            open.setAllCaps(false);
            open.setMinHeight(dp(54));
            open.setOnClickListener(v -> {
                Intent intent = new Intent(this, LessonActivity.class);
                intent.putExtra(LessonActivity.EXTRA_LESSON_ID, lesson.id);
                startActivity(intent);
            });
            card.addView(open);
            container.addView(card);
        }

        findViewById(R.id.area_back_button).setOnClickListener(v -> {
            Intent curriculum = new Intent(this, CurriculumActivity.class);
            curriculum.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(curriculum);
            finish();
        });
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
}
