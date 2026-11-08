package com.example.speechpractice;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class PracticeListActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice_list);

        bind(PracticeExerciseType.SUSTAINED_VOWEL, R.id.practice_sustained_vowel_button);
        bind(PracticeExerciseType.CONTROLLED_VOICE_ONSET, R.id.practice_voice_onset_button);
        bind(PracticeExerciseType.COMFORTABLE_LOUDNESS, R.id.practice_loudness_button);

        findViewById(R.id.practice_list_home_button).setOnClickListener(v -> {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            finish();
        });
    }

    private void bind(PracticeExerciseType type, int buttonId) {
        Button button = findViewById(buttonId);
        button.setOnClickListener(v -> {
            Intent intent = new Intent(this, PracticeExerciseActivity.class);
            intent.putExtra(PracticeExerciseActivity.EXTRA_EXERCISE_TYPE, type.name());
            startActivity(intent);
        });
    }
}
