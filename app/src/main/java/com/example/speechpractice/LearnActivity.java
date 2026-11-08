package com.example.speechpractice;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class LearnActivity extends AppCompatActivity {
    private static final LearnTopic[] TOPICS = {
            new LearnTopic("Why can speaking become difficult?",
                    "A stroke can change how the brain controls speaking muscles. Speech may sound or feel different. Each person is different.",
                    R.drawable.illustration_speaking),
            new LearnTopic("How do we speak?",
                    "Air, voice, lips, and tongue work together when we speak. Different parts help make different sounds.",
                    R.drawable.illustration_mouth),
            new LearnTopic("How does breathing help us speak?",
                    "Easy breathing can support speech. You can pause for a breath whenever you need one.",
                    R.drawable.illustration_breathing),
            new LearnTopic("How do we make our voice?",
                    "Air helps make voice. Try a gentle 'Aaa' if that feels comfortable.",
                    R.drawable.illustration_speaking),
            new LearnTopic("How do our lips and tongue help?",
                    "Lips and tongue help shape sounds. The camera cannot see your tongue or check how you say a sound.",
                    R.drawable.illustration_mouth),
            new LearnTopic("What are pitch and loudness?",
                    "Pitch is how high or low a voice sounds. Loudness is how soft or strong it sounds. Many voice changes are natural.",
                    R.drawable.illustration_microphone),
            new LearnTopic("What is speaking rhythm?",
                    "Rhythm is the timing of sounds and pauses. A pause can give you time to rest or think.",
                    R.drawable.illustration_speaking),
            new LearnTopic("How does SpeechBridge help?",
                    "The app offers short practice steps. You choose whether to record and can listen again.",
                    R.drawable.illustration_feedback),
            new LearnTopic("How does the microphone work?",
                    "The phone listens when you choose to record. Distance and room noise can change the sound it hears.",
                    R.drawable.illustration_microphone),
            new LearnTopic("What does the wearable device do?",
                    "A planned neck sensor would measure vibration where it sits. Skin pads would measure electrical activity beneath them. They are not set up for lesson practice in this version.",
                    R.drawable.illustration_wearable),
            new LearnTopic("What does feedback mean?",
                    "A result describes only part of an attempt. It is not a grade, diagnosis, or proof of change.",
                    R.drawable.illustration_feedback),
            new LearnTopic("What should I know about safety?",
                    "Choose easy activities. Stop if you feel pain or discomfort. Ask a health professional what is right for you.",
                    R.drawable.illustration_breathing)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learn);

        LinearLayout topicContainer = findViewById(R.id.learn_topics_container);
        for (LearnTopic topic : TOPICS) topicContainer.addView(createTopicCard(topic));

        bindButton(R.id.learn_dysarthria, DysarthriaActivity.class);
        bindButton(R.id.learn_how_it_works, HowSpeechBridgeWorksActivity.class);
        bindButton(R.id.learn_sensors, SensorsActivity.class);
        bindButton(R.id.learn_phonation, PhonationActivity.class);
        bindButton(R.id.learn_biofeedback, BiofeedbackActivity.class);
        bindButton(R.id.learn_glossary, GlossaryActivity.class);
        bindButton(R.id.learn_faq, FaqActivity.class);
        bindButton(R.id.learn_safety, SafetyActivity.class);
        bindButton(R.id.learn_references, ReferencesActivity.class);
        findViewById(R.id.learn_home_button).setOnClickListener(v -> {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            finish();
        });
    }

    private View createTopicCard(LearnTopic topic) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardParams);

        ImageView illustration = new ImageView(this);
        illustration.setImageResource(topic.illustration);
        illustration.setContentDescription(topic.title);
        illustration.setScaleType(ImageView.ScaleType.FIT_CENTER);
        illustration.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
        card.addView(illustration);

        TextView title = new TextView(this);
        title.setText(topic.title);
        title.setTextColor(getColor(R.color.ink));
        title.setTextSize(19f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(6), 0, dp(2));
        card.addView(title);

        TextView details = new TextView(this);
        details.setText(topic.details);
        details.setTextColor(getColor(R.color.ink));
        details.setTextSize(16f);
        details.setLineSpacing(dp(2), 1f);
        details.setVisibility(View.GONE);
        card.addView(details);

        Button open = new Button(this);
        open.setText("Read this topic");
        open.setAllCaps(false);
        open.setMinHeight(dp(48));
        open.setOnClickListener(v -> {
            boolean expanded = details.getVisibility() == View.VISIBLE;
            details.setVisibility(expanded ? View.GONE : View.VISIBLE);
            open.setText(expanded ? "Read this topic" : "Close this topic");
        });
        card.addView(open);
        return card;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void bindButton(int buttonId, Class<?> target) {
        Button button = findViewById(buttonId);
        button.setOnClickListener(v -> startActivity(new Intent(this, target)));
    }

    private static final class LearnTopic {
        final String title;
        final String details;
        final int illustration;

        LearnTopic(String title, String details, int illustration) {
            this.title = title;
            this.details = details;
            this.illustration = illustration;
        }
    }
}
