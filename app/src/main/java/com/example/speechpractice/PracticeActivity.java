package com.example.speechpractice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
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

import java.io.File;
import java.util.List;
import java.util.UUID;

public class PracticeActivity extends AppCompatActivity {
    private static final String TAG = "SpeechPractice";
    private final List<Prompt> prompts = PromptCatalog.all();
    private final SessionStats stats = new SessionStats();
    private final SessionRecord session = new SessionRecord(UUID.randomUUID().toString(), System.currentTimeMillis(), SystemClock.elapsedRealtime());
    private VoskModelManager modelManager;
    private PcmPracticePipeline pipeline;
    private boolean modelReady;
    private boolean sessionFinished;
    private boolean editedByUser;
    private long pipelineToken;
    private int promptIndex;
    private AttemptRecord currentAttempt;
    private String pendingAction;
    private TextView promptCount;
    private TextView instruction;
    private TextView state;
    private ImageView illustration;
    private EditText response;
    private Button speakButton;
    private Button stopButton;
    private Button nextButton;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) requestOrStartPipeline();
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
        speakButton = findViewById(R.id.speak_button);
        stopButton = findViewById(R.id.stop_button);
        nextButton = findViewById(R.id.next_button);
        response.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) editedByUser = true; });
        speakButton.setOnClickListener(v -> requestOrStartPipeline());
        stopButton.setOnClickListener(v -> stopPipeline("stopped"));
        findViewById(R.id.retry_button).setOnClickListener(v -> {
            if (currentAttempt != null) currentAttempt.retries++;
            stopPipeline("retry");
            if (pipeline == null || !pipeline.isRunning()) resetForRetry();
        });
        findViewById(R.id.skip_button).setOnClickListener(v -> stopPipeline("skip"));
        nextButton.setOnClickListener(v -> stopPipeline("next"));
        findViewById(R.id.finish_button).setOnClickListener(v -> stopPipeline("finish"));
        speakButton.setEnabled(false);
        stopButton.setEnabled(false);
        showPrompt();
        modelManager = new VoskModelManager(this);
        if (modelManager.isInstalled()) preparePipeline(modelManager.modelDirectory());
        else showModelSetup();
    }

    private void showPrompt() {
        Prompt prompt = prompts.get(promptIndex);
        currentAttempt = new AttemptRecord(prompt.id, prompt.word);
        currentAttempt.promptDisplayedAt = SystemClock.elapsedRealtime();
        session.attempts.add(currentAttempt);
        stats.promptsPresented++;
        promptCount.setText(getString(R.string.prompt_counter, promptIndex + 1, prompts.size()));
        instruction.setText(prompt.instruction);
        illustration.setImageResource(prompt.illustration);
        illustration.setContentDescription(getString(R.string.picture_description, prompt.word));
        response.setText("");
        editedByUser = false;
        nextButton.setVisibility(promptIndex == prompts.size() - 1 ? View.GONE : View.VISIBLE);
        setState(modelReady ? "Ready. Stay quiet briefly, then speak at the usual phone distance." : "Set up offline transcription to begin.");
    }

    private void showModelSetup() {
        new AlertDialog.Builder(this)
                .setTitle("Set up offline speech recognition")
                .setMessage("SpeechPractice needs the official Vosk English model for on-device transcription. It downloads about 40 MB and uses app-private storage; recordings are not uploaded. Continue when connected to a trusted network?")
                .setPositiveButton("Download", (dialog, which) -> downloadModel())
                .setNegativeButton("Type responses", (dialog, which) -> setState("Offline model not installed. You can type responses, or restart setup later."))
                .setOnDismissListener(dialog -> { if (!modelReady) setState("Offline model not installed. You can type responses, or restart setup later."); })
                .show();
    }

    private void downloadModel() {
        setState("Downloading offline model (about 40 MB)…");
        modelManager.downloadAsync(new VoskModelManager.Listener() {
            @Override public void onProgress(int percent) { setState(percent >= 0 ? "Downloading offline model… " + percent + "%" : "Downloading offline model…"); }
            @Override public void onComplete(File modelDirectory) { setState("Model installed. Loading transcription…"); preparePipeline(modelDirectory); }
            @Override public void onError(String message, Throwable cause) { Log.w(TAG, "Offline model setup failed", cause); setState(message + " You can type responses or retry setup."); }
        });
    }

    private void preparePipeline(File modelDirectory) {
        long token = ++pipelineToken;
        pipeline = new PcmPracticePipeline(this);
        pipeline.prepareAsync(modelDirectory, new PcmPracticePipeline.Listener() {
            @Override public void onReady() {
                if (token != pipelineToken || isFinishing()) return;
                modelReady = true;
                speakButton.setEnabled(true);
                setState("Ready. Stay quiet briefly, then speak at the usual phone distance.");
            }
            @Override public void onStarted() { if (token == pipelineToken) { speakButton.setEnabled(false); stopButton.setEnabled(true); setState("Listening and measuring… stay quiet briefly, then speak."); } }
            @Override public void onInputLevel(double dbfs, boolean speechLikely) {
                if (token == pipelineToken && pipeline != null && pipeline.isRunning()) {
                    setState(String.format(java.util.Locale.US, "Input level %.1f dBFS — %s", dbfs, speechLikely ? "speech activity likely" : "calibrating / quiet"));
                }
            }
            @Override public void onPartialTranscript(String transcript) { if (token == pipelineToken && !editedByUser) response.setText(transcript); }
            @Override public void onFinalTranscript(String transcript) {
                if (token != pipelineToken || currentAttempt == null) return;
                currentAttempt.machineTranscript = transcript;
                if (!editedByUser) { currentAttempt.transcript = transcript; response.setText(transcript); }
                currentAttempt.partialOnly = false;
            }
            @Override public void onStopped(AudioMeasurementResult measurements) { handleStopped(token, measurements); }
            @Override public void onError(String message, Throwable cause) { handlePipelineError(token, message, cause); }
        });
    }

    private void requestOrStartPipeline() {
        if (!modelReady || pipeline == null) { setState("Offline transcription is not ready. Install the model or type your response."); return; }
        if (pipeline.isRunning()) { setState("Already listening. Take your time."); return; }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else startPipeline();
    }

    private void startPipeline() {
        if (pipeline == null || pipeline.isRunning()) return;
        if (currentAttempt != null) {
            currentAttempt.requestStartedAt = SystemClock.elapsedRealtime();
            currentAttempt.attemptsStarted++;
        }
        stats.attemptsStarted++;
        editedByUser = false;
        response.setText("");
        setState("Starting microphone… stay quiet briefly for calibration, then speak.");
        if (!pipeline.start(null)) {
            markRecognitionError("The microphone could not be started. Check permission and device availability.", 0);
            setState("The microphone could not be started. You can type your response or try again.");
        }
    }

    private void stopPipeline(String action) {
        pendingAction = action;
        if (pipeline != null && pipeline.isRunning()) {
            setState("Stopping capture and finishing this attempt…");
            pipeline.stop();
        } else applyPendingAction();
    }

    private void handleStopped(long token, AudioMeasurementResult measurement) {
        if (token != pipelineToken || currentAttempt == null) return;
        currentAttempt.audioSummary = new AudioSummaryRecord(measurement);
        currentAttempt.endOfSpeechAt = SystemClock.elapsedRealtime();
        if (currentAttempt.machineTranscript != null && !currentAttempt.machineTranscript.isEmpty()) {
            currentAttempt.status = "recognized";
            stats.recognitionResults++;
        } else {
            currentAttempt.status = "recognition_error";
            currentAttempt.errorMessage = "No transcript was returned. This is separate from whether speech activity was detected.";
            stats.recognitionErrors++;
        }
        releasePipeline();
        applyPendingAction();
    }

    private void handlePipelineError(long token, String message, Throwable cause) {
        if (token != pipelineToken) return;
        Log.w(TAG, "PCM pipeline error promptIndex=" + promptIndex + " running=" + (pipeline != null && pipeline.isRunning()), cause);
        markRecognitionError(message, 0);
        releasePipeline();
        setState(message + " You can type your response or try again.");
        if (pendingAction != null) applyPendingAction();
    }

    private void markRecognitionError(String message, int code) {
        stats.recognitionErrors++;
        if (currentAttempt != null) {
            currentAttempt.status = "recognition_error";
            currentAttempt.errorMessage = message;
            currentAttempt.errorCode = code;
            currentAttempt.errorAt = SystemClock.elapsedRealtime();
        }
    }

    private void applyPendingAction() {
        String action = pendingAction;
        pendingAction = null;
        if (action == null || "stopped".equals(action)) {
            speakButton.setEnabled(modelReady);
            stopButton.setEnabled(false);
            setState("Attempt stopped. Review or edit the transcript, then try again or continue.");
            return;
        }
        if ("retry".equals(action)) { resetForRetry(); return; }
        finishCurrentAttempt("skip".equals(action) ? "skipped" : "");
        if ("skip".equals(action)) stats.promptsSkipped++;
        if ("finish".equals(action)) finishSession();
        else if (promptIndex < prompts.size() - 1) { promptIndex++; showPrompt(); }
        else finishSession();
    }

    private void resetForRetry() {
        if (currentAttempt != null) {
            currentAttempt.transcript = "";
            currentAttempt.machineTranscript = "";
            currentAttempt.status = "not_attempted";
            currentAttempt.errorMessage = "";
        }
        response.setText("");
        editedByUser = false;
        speakButton.setEnabled(modelReady);
        stopButton.setEnabled(false);
        setState("Ready to retry. Stay quiet briefly, then speak.");
    }

    private void finishCurrentAttempt(String requestedStatus) {
        if (currentAttempt == null) return;
        String text = response.getText().toString().trim();
        currentAttempt.transcript = text;
        currentAttempt.manuallyEdited = editedByUser && !text.equals(currentAttempt.machineTranscript);
        if ("skipped".equals(requestedStatus)) currentAttempt.status = "skipped";
        else if (!text.isEmpty() && currentAttempt.machineTranscript.isEmpty()) currentAttempt.status = "manual_entry";
        else if (!text.isEmpty() && currentAttempt.manuallyEdited) currentAttempt.status = "manual_edit";
    }

    private void finishSession() {
        if (sessionFinished) return;
        sessionFinished = true;
        finishCurrentAttempt("");
        session.finishedAtWallClock = System.currentTimeMillis();
        session.finishedAtElapsed = SystemClock.elapsedRealtime();
        ReviewStore.save(this, session);
        Intent reviewIntent = new Intent(this, ReviewActivity.class);
        reviewIntent.putExtra(ReviewActivity.EXTRA_SESSION, session);
        startActivity(reviewIntent);
        finish();
    }

    private void releasePipeline() {
        if (pipeline != null) { pipeline.release(); pipeline = null; }
        speakButton.setEnabled(modelReady);
        stopButton.setEnabled(false);
    }

    private void setState(String message) { if (state != null) state.setText(message); }

    @Override protected void onDestroy() {
        ++pipelineToken;
        if (pipeline != null) pipeline.release();
        if (modelManager != null) modelManager.shutdown();
        super.onDestroy();
    }
}
