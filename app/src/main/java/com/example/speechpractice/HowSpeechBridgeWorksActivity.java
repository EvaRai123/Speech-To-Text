package com.example.speechpractice;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class HowSpeechBridgeWorksActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_how_works);
        findViewById(R.id.how_works_learn_button).setOnClickListener(v -> {
            Intent learn = new Intent(this, LearnActivity.class);
            learn.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(learn);
            finish();
        });
    }
}
