package com.example.speechpractice;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.util.Date;

public class ReviewActivity extends AppCompatActivity {
    public static final String EXTRA_SESSION = "session";
    private SessionRecord session;
    private LinearLayout promptResults;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);
        session = (SessionRecord) getIntent().getSerializableExtra(EXTRA_SESSION);
        promptResults = findViewById(R.id.prompt_results_container);
        if (session == null) {
            showText(promptResults, "No completed session data was supplied.");
            return;
        }
        populateReview();
        findViewById(R.id.review_home_button).setOnClickListener(v -> {
            Intent home = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            finish();
        });
        findViewById(R.id.review_new_session_button).setOnClickListener(v -> {
            startActivity(new Intent(this, PracticeActivity.class));
            finish();
        });
    }

    private void populateReview() {
        TextView date = findViewById(R.id.session_date);
        date.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(session.startedAtWallClock)));
        ((TextView) findViewById(R.id.session_overview)).setText(
                "Duration: " + durationText(session.durationMs()) +
                        "\nPrompts presented: " + session.attempts.size() +
                        "\nPrompts attempted: " + session.promptsAttempted() +
                        "\nPrompts with a response: " + session.promptsCompleted() +
                        "\nSkipped: " + session.skipped() +
                        "\nRecognition results: " + session.recognitionResults() +
                        "\nRecognition failures: " + session.recognitionFailures() +
                        "\nRetries: " + session.retries() +
                        "\nManually edited transcripts: " + session.manuallyEdited());
        ((TextView) findViewById(R.id.data_quality)).setText(
                "Usable transcripts: " + session.usableTranscripts() +
                        "\nMissing or unusable transcripts: " + session.missingTranscripts() +
                        "\nTiming data: callback timestamps are shown only when both required events were recorded." +
                        "\nRecognition errors describe service outcomes; they do not show that a person could not say a target word.");
        ((TextView) findViewById(R.id.history_empty)).setText(
                ReviewStore.savedSessionCount(this) > 1
                        ? "Earlier sessions are saved locally for future comparison. This prototype does not calculate trends yet."
                        : "No previous completed sessions are available for comparison yet.");
        for (AttemptRecord attempt : session.attempts) addAttemptCard(attempt);
    }

    private void addAttemptCard(AttemptRecord attempt) {
        StringBuilder text = new StringBuilder();
        text.append("Target: ").append(attempt.targetWord)
                .append("\nStatus: ").append(statusText(attempt.status))
                .append("\nTranscript: ").append(attempt.hasUsableTranscript() ? attempt.transcript : "Not available")
                .append("\nMachine transcript: ").append(attempt.machineTranscript.isEmpty() ? "Not available" : attempt.machineTranscript)
                .append("\nManual edit: ").append(attempt.manuallyEdited ? "Yes" : "No")
                .append("\nAttempts: ").append(attempt.attemptsStarted).append("; retries: ").append(attempt.retries)
                .append("\nPrompt-to-first-speech callback: ").append(elapsed(attempt.promptDisplayedAt, attempt.firstSpeechAt))
                .append("\nRecognizer speech interval estimate: ").append(elapsed(attempt.firstSpeechAt, attempt.endOfSpeechAt))
                .append("\nRecognition turnaround: ").append(elapsed(attempt.requestStartedAt, attempt.finalResultAt));
        if (!attempt.errorMessage.isEmpty()) text.append("\nReason: ").append(attempt.errorMessage);
        if (attempt.audioSummary == null) {
            text.append("\nAudio measurements: Not measured");
        } else {
            AudioSummaryRecord audio = attempt.audioSummary;
            text.append("\nAudio quality: ").append(audio.quality)
                    .append("\nAverage recorded signal level (RMS): ").append(format(audio.rmsDbfs)).append(" dBFS")
                    .append("\nHighest recorded signal level (peak): ").append(format(audio.peakDbfs)).append(" dBFS")
                    .append("\nEstimated background level: ").append(format(audio.noiseFloorDbfs)).append(" dBFS")
                    .append("\nEstimated signal-to-noise ratio: ").append(format(audio.snrDb)).append(" dB")
                    .append("\nDetected audio activity (estimate): ").append(format(audio.speechActiveDurationMs)).append(" ms")
                    .append("\nPauses: ").append(audio.pauseCount).append(" (total ").append(format(audio.pauseDurationMs)).append(" ms)")
                    .append("\nEstimated pitch: ").append(Double.isNaN(audio.medianF0Hz) ? "Not measured" : format(audio.medianF0Hz) + " Hz")
                    .append("\nRecording checks: ").append(qualityText(audio.quality))
                    .append("\nWarnings: ").append(qualityFlagsText(audio.qualityFlags))
                    .append("\nAlgorithm: ").append(audio.algorithmVersion)
                    .append("\n\nAbout these measurements: RMS is the average microphone signal level; peak is its strongest moment. dBFS describes the digital recording level, not absolute loudness in the room. Background level and signal-to-noise ratio are estimates affected by the recording and room. Detected activity is based on sound level, so it may miss quiet voice or include other sounds. Pitch is estimated and natural pitch changes are not errors. Pauses describe gaps between detected activity intervals. None of these values measures speech correctness, intelligibility, vocal effort, or recovery.");
        }
        showText(promptResults, text.toString());
    }

    private void showText(LinearLayout parent, String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(getColor(R.color.ink));
        view.setTextSize(16);
        view.setPadding(18, 18, 18, 18);
        view.setBackgroundResource(R.drawable.card_background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, 12);
        parent.addView(view, params);
    }

    private String statusText(String status) {
        if ("recognized".equals(status)) return "Transcript returned (not a correctness judgment)";
        if ("manual_edit".equals(status)) return "Transcript manually edited";
        if ("manual_entry".equals(status)) return "Manual response entered";
        if ("recognition_error".equals(status)) return "Recognition failure";
        if ("skipped".equals(status)) return "Skipped";
        if ("cancelled".equals(status)) return "Cancelled before result";
        return "No result";
    }

    private String elapsed(long start, long end) {
        if (start <= 0 || end <= 0 || end < start) return "Not measured";
        return (end - start) + " ms (experimental callback timing)";
    }

    private String durationText(long durationMs) {
        if (durationMs <= 0) return "Not measured";
        return (durationMs / 1000) + " seconds";
    }

    private String qualityText(String quality) {
        if ("USABLE".equals(quality)) return "Basic signal checks passed; this is not a clinical quality rating.";
        if ("LIMITED".equals(quality)) return "Limited — one or more checks raised a caution.";
        if ("INVALID".equals(quality)) return "Unavailable — the recording could not be analysed.";
        return "Unavailable.";
    }

    private String qualityFlagsText(String flags) {
        if (flags == null || flags.trim().isEmpty()) return "No warnings were flagged by the basic checks.";
        StringBuilder text = new StringBuilder();
        for (String flag : flags.split(",\\s*")) {
            if (text.length() > 0) text.append("; ");
            if ("CLIPPING".equals(flag)) text.append("the recording may have captured a sound too strongly");
            else if ("LOW_SNR".equals(flag)) text.append("background sound may affect the estimate");
            else if ("VERY_QUIET".equals(flag)) text.append("the captured signal was very quiet");
            else if ("NO_SPEECH_ACTIVITY_DETECTED".equals(flag)) text.append("voice-like activity was not detected by the sound-level check");
            else if ("PITCH_UNAVAILABLE".equals(flag)) text.append("pitch could not be estimated confidently");
            else text.append(flag.replace('_', ' ').toLowerCase(java.util.Locale.US));
        }
        return text.append('.').toString();
    }

    private String format(double value) { return Double.isNaN(value) ? "Not measured" : String.format(java.util.Locale.US, "%.1f", value); }
}
