package com.example.speechpractice;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        findViewById(R.id.about_back_button).setOnClickListener(v -> finish());
        findViewById(R.id.about_learn_button).setOnClickListener(v ->
                startActivity(new Intent(this, LearnActivity.class)));
        findViewById(R.id.about_safety_button).setOnClickListener(v ->
                startActivity(new Intent(this, SafetyActivity.class)));
    }
}
