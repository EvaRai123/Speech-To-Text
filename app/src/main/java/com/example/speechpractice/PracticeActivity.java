package com.example.speechpractice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.List;

public class PracticeActivity extends AppCompatActivity {
    private final List<Prompt> prompts = PromptCatalog.all();
    private final SessionStats stats = new SessionStats();
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private boolean listening;
    private boolean suppressRecognitionCallbacks;
    private boolean sessionFinished;
    private boolean editedByUser;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable recognitionTimeout = () -> {
        if (!listening || suppressRecognitionCallbacks) return;
        listening = false;
        suppressRecognitionCallbacks = true;
        if (recognizer != null) recognizer.cancel();
        stats.recognitionErrors++;
        setState("Recognition took too long to complete. You can try again or type below.");
    };
    private int promptIndex;
    private TextView promptCount;
    private TextView instruction;
    private TextView state;
    private ImageView illustration;
    private EditText response;
    private Button nextButton;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startRecognition();
                else setState("Microphone permission was not granted. You can type your response instead.");
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);
        promptCount = findViewById(R.id.prompt_count);
        instruction = findViewById(R.id.prompt_instruction);
        state = findViewById(R.id.recognition_state);
        illustration = findViewById(R.id.prompt_illustration);
        response = findViewById(R.id.response_input);
        nextButton = findViewById(R.id.next_button);
        response.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) editedByUser = true; });
        findViewById(R.id.speak_button).setOnClickListener(v -> requestOrStartRecognition());
        findViewById(R.id.retry_button).setOnClickListener(v -> { response.setText(""); editedByUser = false; requestOrStartRecognition(); });
        findViewById(R.id.skip_button).setOnClickListener(v -> { stats.promptsSkipped++; moveNext(); });
        nextButton.setOnClickListener(v -> moveNext());
        findViewById(R.id.finish_button).setOnClickListener(v -> finishSession());
        showPrompt();
    }

    private void showPrompt() {
        Prompt prompt = prompts.get(promptIndex);
        stats.promptsPresented++;
        promptCount.setText(getString(R.string.prompt_counter, promptIndex + 1, prompts.size()));
        instruction.setText(prompt.instruction);
        illustration.setImageResource(prompt.illustration);
        illustration.setContentDescription(getString(R.string.picture_description, prompt.word));
        response.setText("");
        editedByUser = false;
        nextButton.setVisibility(promptIndex == prompts.size() - 1 ? View.GONE : View.VISIBLE);
        setState("Ready when you are.");
    }

    private void requestOrStartRecognition() {
        if (listening) { setState("Already listening. Take your time."); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setState("Speech recognition is not available. Type your response instead.");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else startRecognition();
    }

    private void startRecognition() {
        if (listening) return;
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { if (!suppressRecognitionCallbacks) setState("Listening… speak clearly when ready."); }
                @Override public void onBeginningOfSpeech() { if (!suppressRecognitionCallbacks) setState("Listening…"); }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { if (!suppressRecognitionCallbacks) setState("Processing your response…"); }
                @Override public void onError(int error) {
                    if (suppressRecognitionCallbacks) return;
                    mainHandler.removeCallbacks(recognitionTimeout);
                    listening = false; stats.recognitionErrors++; setState(error == SpeechRecognizer.ERROR_NO_MATCH ? "No speech was detected. Try again or type below." : "Recognition could not complete. You can try again or type below.");
                }
                @Override public void onResults(Bundle results) {
                    if (suppressRecognitionCallbacks) return;
                    mainHandler.removeCallbacks(recognitionTimeout);
                    listening = false; stats.recognitionResults++;
                    if (!editedByUser && results != null) {
                        java.util.ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) response.setText(matches.get(0));
                    }
                    setState("Transcript received. Review or edit it before continuing.");
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    if (suppressRecognitionCallbacks) return;
                    if (!editedByUser && partialResults != null) {
                        java.util.ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) response.setText(matches.get(0));
                    }
                }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
        }
        if (recognizerIntent == null) {
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        }
        suppressRecognitionCallbacks = false;
        listening = true; stats.attemptsStarted++; setState("Starting speech recognition…");
        try {
            recognizer.startListening(recognizerIntent);
            mainHandler.postDelayed(recognitionTimeout, 30000L);
        } catch (RuntimeException exception) {
            mainHandler.removeCallbacks(recognitionTimeout);
            listening = false;
            stats.recognitionErrors++;
            setState("Recognition could not start. You can type your response instead.");
        }
    }

    private void cancelRecognition() {
        if (recognizer == null) return;
        suppressRecognitionCallbacks = true;
        listening = false;
        mainHandler.removeCallbacks(recognitionTimeout);
        recognizer.cancel();
    }

    private void moveNext() { cancelRecognition(); if (promptIndex < prompts.size() - 1) { promptIndex++; showPrompt(); } else finishSession(); }
    private void setState(String message) { if (state != null) state.setText(message); }
    private void finishSession() {
        if (sessionFinished) return;
        sessionFinished = true;
        cancelRecognition();
        new AlertDialog.Builder(this).setTitle("Practice session complete")
                .setMessage(getString(R.string.session_summary, stats.promptsPresented, stats.attemptsStarted, stats.recognitionResults, stats.promptsSkipped, stats.recognitionErrors))
                .setPositiveButton("Return home", (d, w) -> finish()).setCancelable(false).show();
    }

    @Override protected void onDestroy() { cancelRecognition(); if (recognizer != null) { recognizer.destroy(); recognizer = null; } super.onDestroy(); }
}
