package com.example.speechpractice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class PracticeExerciseActivity extends AppCompatActivity {
    public static final String EXTRA_EXERCISE_TYPE = "extra_exercise_type";
    public static final String EXTRA_RESEARCH_CONDITION = "extra_research_condition";
    private static final String TAG = "SpeechBridgePractice";

    private PracticeExerciseType exerciseType;
    private ResearchCondition researchCondition;
    private TextView titleView;
    private TextView descriptionView;
    private TextView instructionsView;
    private TextView statusView;
    private TextView measurementStatusView;
    private TextView resultsView;
    private TextView measurementDetailsView;
    private Button measurementDetailsButton;
    private Button startButton;
    private Button stopButton;
    private Button finishButton;
    private Button backButton;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startRecording();
                else setStatus("Microphone permission was denied. Recording is unavailable until permission is granted.");
            });

    private PcmPracticePipeline pipeline;
    private AudioMeasurementResult latestMeasurement;
    private boolean recording;
    private long recordingStartedAt;
    private long recordedDurationMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice_exercise);

        String typeName = getIntent().getStringExtra(EXTRA_EXERCISE_TYPE);
        exerciseType = PracticeExerciseType.fromName(typeName);
        String conditionName = getIntent().getStringExtra(EXTRA_RESEARCH_CONDITION);
        researchCondition = ResearchCondition.fromName(conditionName);

        titleView = findViewById(R.id.exercise_title);
        descriptionView = findViewById(R.id.exercise_description);
        instructionsView = findViewById(R.id.exercise_instructions);
        statusView = findViewById(R.id.exercise_status);
        measurementStatusView = findViewById(R.id.sensor_status);
        resultsView = findViewById(R.id.exercise_results);
        measurementDetailsView = findViewById(R.id.exercise_measurement_details);
        measurementDetailsButton = findViewById(R.id.exercise_details_toggle);
        startButton = findViewById(R.id.exercise_start_button);
        stopButton = findViewById(R.id.exercise_stop_button);
        finishButton = findViewById(R.id.exercise_finish_button);
        backButton = findViewById(R.id.exercise_back_button);

        startButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            } else {
                startRecording();
            }
        });
        stopButton.setOnClickListener(v -> stopRecording());
        finishButton.setOnClickListener(v -> finishSession());
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, PracticeListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        measurementDetailsButton.setOnClickListener(v -> {
            boolean expanded = measurementDetailsView.getVisibility() == android.view.View.VISIBLE;
            measurementDetailsView.setVisibility(expanded ? android.view.View.GONE : android.view.View.VISIBLE);
            measurementDetailsButton.setText(expanded ? "What do these numbers mean?" : "Hide measurement details");
        });

        configureScreen();
        setMeasurementStatus();
    }

    private void configureScreen() {
        titleView.setText(exerciseType.getLabel());
        String description;
        String instructions;
        switch (exerciseType) {
            case CONTROLLED_VOICE_ONSET:
                description = "Practise starting a comfortable voice sound gently.";
                instructions = "Sit comfortably. Tap Start, say Aaa when you are ready, then tap Stop. Do not push your voice.";
                break;
            case COMFORTABLE_LOUDNESS:
                description = "Explore a small, comfortable change in your voice level.";
                instructions = "Keep the phone in one place. Say Aaa comfortably, make a small easy change if you wish, and stop before any strain. Never shout.";
                break;
            case SUSTAINED_VOWEL:
            default:
                description = "Try holding a comfortable Aaa sound for as long as it feels easy.";
                instructions = "Sit comfortably. Tap Start, make a gentle Aaa sound, and tap Stop when ready. Do not hold your breath or strain.";
                break;
        }
        descriptionView.setText(description);
        instructionsView.setText(instructions);
        resultsView.setText("No completed measurement is available yet. Start a recording to capture microphone data for this exercise.");
        measurementDetailsButton.setVisibility(android.view.View.GONE);
        measurementDetailsView.setVisibility(android.view.View.GONE);
        setStatus("Ready to begin this exercise.");
    }

    private void startRecording() {
        if (recording) {
            setStatus("The microphone is already recording this exercise.");
            return;
        }
        if (researchCondition != null && researchCondition != ResearchCondition.MICROPHONE_ONLY) {
            setStatus("This research condition requires hardware not connected in this app build. Microphone-only recordings remain available for Condition A.");
            return;
        }

        try {
            latestMeasurement = null;
            recordedDurationMs = 0L;
            resetMeasurementDisplay();
            pipeline = new PcmPracticePipeline(this);
            pipeline.setListener(pipelineListener);
            recordingStartedAt = SystemClock.elapsedRealtime();
            boolean started = pipeline.startForMeasurement(null);
            if (!started) {
                releasePipeline();
                setStatus("The microphone could not start. Check microphone availability and permission.");
                return;
            }
            recording = true;
            setStatus("Recording in progress. Practise comfortably and stop when the exercise is complete.");
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            finishButton.setEnabled(false);
        } catch (Exception exception) {
            releasePipeline();
            Log.e(TAG, "Recording failed", exception);
            setStatus("The microphone could not start. Check permission and device availability.");
        }
    }

    private void resetMeasurementDisplay() {
        measurementDetailsView.setText("");
        measurementDetailsView.setVisibility(android.view.View.GONE);
        measurementDetailsButton.setVisibility(android.view.View.GONE);
        resultsView.setText("Recording in progress. Your new result will appear when you stop.");
    }

    private void stopRecording() {
        if (!recording || pipeline == null) {
            setStatus("No active recording to stop.");
            return;
        }
        setStatus("Recording stopped. Processing the captured PCM samples.");
        pipeline.stop();
    }

    private final PcmPracticePipeline.Listener pipelineListener = new PcmPracticePipeline.Listener() {
        @Override
        public void onReady() {
        }

        @Override
        public void onStarted() {
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        }

        @Override
        public void onInputLevel(double dbfs, boolean speechLikely) {
            if (recording) {
                String activityText = speechLikely ? "speech activity likely" : "quiet or calibrating";
                statusView.setText("Input level: " + formatDouble(dbfs) + " dBFS — " + activityText);
            }
        }

        @Override
        public void onPartialTranscript(String transcript) {
        }

        @Override
        public void onFinalTranscript(String transcript) {
        }

        @Override
        public void onStopped(AudioMeasurementResult measurements) {
            recording = false;
            latestMeasurement = measurements;
            recordedDurationMs = recordingStartedAt > 0L
                    ? Math.max(0L, SystemClock.elapsedRealtime() - recordingStartedAt) : 0L;
            stopButton.setEnabled(false);
            finishButton.setEnabled(true);
            if (measurements == null || measurements.quality.equals("INVALID")) {
                resultsView.setText("No valid microphone recording was captured. The recording may have been too quiet, too short, or interrupted.");
                setStatus("Recording finished but no valid measurement was produced.");
                setMeasurementStatus();
                return;
            }
            updateResultsFromRecording();
            setStatus("Recording finished. The summary below reflects actual captured PCM measurements.");
        }

        @Override
        public void onError(String message, Throwable cause) {
            recording = false;
            latestMeasurement = null;
            stopButton.setEnabled(false);
            finishButton.setEnabled(true);
            resultsView.setText("The microphone capture encountered an error: " + message + "\nThis recording was not used as a valid measurement.");
            setStatus(message);
            Log.w(TAG, message, cause);
        }
    };

    private void finishSession() {
        PracticeSession session = buildSession();
        if (session != null) {
            PracticeBaselineStore.save(this, session);
        }
        boolean researchSessionSaved = true;
        if (researchCondition != null) {
            ResearchSession researchSession = buildResearchSession(session);
            try {
                ResearchSessionStore.save(this, researchSession);
            } catch (IllegalStateException exception) {
                researchSessionSaved = false;
                Log.e(TAG, "Research session could not be saved.", exception);
            }
        }
        resultsView.setText(buildSummaryText(session));
        if (latestMeasurement != null && !"INVALID".equals(latestMeasurement.quality)) {
            measurementDetailsView.setText(buildMeasurementDetails(latestMeasurement, recordedDurationMs));
            measurementDetailsButton.setVisibility(android.view.View.VISIBLE);
        }
        setStatus(researchSessionSaved
                ? "Exercise complete. Measurements shown are based on the current captured recording only."
                : "Exercise complete, but the research session could not be saved. Existing research history was preserved.");
        finishButton.setEnabled(false);
        startButton.setEnabled(true);
    }

    private PracticeSession buildSession() {
        if (latestMeasurement == null) {
            return new PracticeSession("practice_" + System.currentTimeMillis(), recordingStartedAt, System.currentTimeMillis(),
                    exerciseType, null, null, null, new ArrayList<>(), new ArrayList<>());
        }
        MicrophoneMeasurement microphoneMeasurement = new MicrophoneMeasurement(
                System.currentTimeMillis(),
                recordedDurationMs,
                latestMeasurement.rmsDbfs,
                latestMeasurement.peakDbfs,
                latestMeasurement.noiseFloorDbfs,
                latestMeasurement.snrDb,
                latestMeasurement.speechActiveDurationMs,
                latestMeasurement.medianF0Hz,
                latestMeasurement.quality,
                String.join(", ", latestMeasurement.qualityFlags),
                latestMeasurement.quality.equals("USABLE") || latestMeasurement.quality.equals("LIMITED")
        );

        PracticeMeasurementState micState;
        if (latestMeasurement.quality.equals("INVALID") || Double.isNaN(latestMeasurement.rmsDbfs)) {
            micState = PracticeMeasurementState.unavailable("Microphone", PracticeMeasurementState.MeasurementSource.MICROPHONE,
                    "No valid microphone measurement is available from the captured audio.");
        } else {
            micState = PracticeMeasurementState.available(
                    "Microphone",
                    PracticeMeasurementState.MeasurementSource.MICROPHONE,
                    "Microphone analysis completed.",
                    latestMeasurement.computedAtWallClockMs,
                    "RMS: " + formatDouble(latestMeasurement.rmsDbfs) + " dBFS; SNR: " + formatDouble(latestMeasurement.snrDb) + " dB; speech activity: " + formatDouble(latestMeasurement.speechActiveDurationMs) + " ms"
            );
        }

        List<PracticeMeasurementState> states = new ArrayList<>(Arrays.asList(
                micState,
                PracticeMeasurementState.unavailable("Accelerometer", PracticeMeasurementState.MeasurementSource.ACCELEROMETER,
                        "No valid accelerometer session is connected. No wearable data is available."),
                PracticeMeasurementState.unavailable("Surface EMG", PracticeMeasurementState.MeasurementSource.SURFACE_EMG,
                        "No valid surface EMG channel is connected. No wearable data is available.")));

        List<String> notes = new ArrayList<>();
        notes.add("This result is microphone-derived and non-diagnostic. It is informational only.");
        notes.add("Estimated onset and phonation duration are derived from speech activity thresholds and may be affected by noise, distance, and recording quality.");

        return new PracticeSession("practice_" + System.currentTimeMillis(), recordingStartedAt, System.currentTimeMillis(),
                exerciseType, microphoneMeasurement, null, null, notes, states);
    }

    private ResearchSession buildResearchSession(PracticeSession session) {
        if (researchCondition == null) {
            return null;
        }
        boolean completed = researchCondition == ResearchCondition.MICROPHONE_ONLY && session != null && session.microphoneMeasurement != null && session.microphoneMeasurement.available;
        SensorMeasurement accel = null;
        SensorMeasurement emg = null;
        if (researchCondition.requiresAccelerometer) {
            accel = new SensorMeasurement("Accelerometer", System.currentTimeMillis(), 0L, false,
                    "Unavailable", "No accelerometer hardware is connected for this research condition.");
        }
        if (researchCondition.requiresSemg) {
            emg = new SensorMeasurement("Surface EMG", System.currentTimeMillis(), 0L, false,
                    "Unavailable", "No surface EMG hardware is connected for this research condition.");
        }
        String notes = "Condition " + researchCondition.label + " was recorded in the local app. Wearable channels are intentionally marked unavailable until a physical hardware setup is selected.";
        return new ResearchSession("research_" + System.currentTimeMillis(), researchCondition, exerciseType,
                session != null ? session.startedAtMs : System.currentTimeMillis(),
                session != null ? session.finishedAtMs : System.currentTimeMillis(),
                completed,
                session != null ? session.microphoneMeasurement : null,
                accel,
                emg,
                session != null ? session.measurementStates : new ArrayList<>(),
                notes);
    }

    private void updateResultsFromRecording() {
        if (latestMeasurement == null) {
            resultsView.setText("No valid microphone recording was captured. The exercise cannot report a measured result.");
            measurementDetailsButton.setVisibility(android.view.View.GONE);
            measurementDetailsView.setVisibility(android.view.View.GONE);
            return;
        }
        resultsView.setText(buildSummaryText(buildSession()));
        measurementDetailsView.setText(buildMeasurementDetails(latestMeasurement, recordedDurationMs));
        measurementDetailsView.setVisibility(android.view.View.GONE);
        measurementDetailsButton.setVisibility(android.view.View.VISIBLE);
        measurementDetailsButton.setText("What do these numbers mean?");
        setMeasurementStatus();
    }

    private String estimateOnsetMs(AudioMeasurementResult result) {
        if (result == null || result.speechIntervals == null || result.speechIntervals.isEmpty()) {
            return "not available";
        }
        double firstStart = result.speechIntervals.get(0).startMs;
        return formatDouble(firstStart) + " ms from recording start (activity threshold estimate)";
    }

    private long estimatePhonationDurationMs(AudioMeasurementResult result) {
        if (result == null) return 0L;
        if (result.speechActiveDurationMs > 0.0) return Math.round(result.speechActiveDurationMs);
        if (result.speechIntervals != null && !result.speechIntervals.isEmpty()) {
            double total = 0.0;
            for (AudioMeasurementResult.SpeechInterval interval : result.speechIntervals) {
                total += (interval.endMs - interval.startMs);
            }
            return Math.round(total);
        }
        return 0L;
    }

    private String buildSummaryText(PracticeSession session) {
        if (session == null || session.microphoneMeasurement == null) {
            return "No reliable microphone measurement is available for this recording.";
        }

        MicrophoneMeasurement measurement = session.microphoneMeasurement;
        StringBuilder text = new StringBuilder("Your recording is saved.\n");
        text.append("Recording time: ").append(formatDurationMs(measurement.durationMs)).append("\n");
        if (measurement.speechActiveDurationMs > 0) {
            text.append("Voice-like sound detected: about ")
                    .append(formatDurationMs(Math.round(measurement.speechActiveDurationMs))).append(" (estimate).\n");
        } else {
            text.append("Voice-like sound detected: not available for this recording.\n");
        }
        text.append(formatQuality(measurement.quality)).append("\n");
        text.append("These are microphone estimates, not a score or a measure of how well you spoke.");
        return text.toString();
    }

    private String buildMeasurementDetails(AudioMeasurementResult result, long durationMs) {
        StringBuilder details = new StringBuilder("About this recording\n");
        details.append("\nRecording duration: ").append(formatDurationMs(durationMs))
                .append(". This is how long the phone recorded. It helps describe the attempt, but cannot tell how much you spoke or whether it was a good attempt.\n");
        details.append("\nVoice activity: ")
                .append(result.speechActiveDurationMs > 0
                        ? formatDurationMs(Math.round(result.speechActiveDurationMs))
                        : "not available")
                .append(". This is the time a sound-level check marked as voice-like. It helps show when sound was present, but may miss quiet speech or include other sounds.\n");
        details.append("\nVoice level: ").append(formatDouble(result.rmsDbfs)).append(" dBFS. This is the average strength of sound reaching the phone. It may help compare similar recordings on the same phone; it is not room loudness, and higher is not automatically better. dBFS is the phone's digital signal scale.\n");
        details.append("\nPeak level: ").append(formatDouble(result.peakDbfs)).append(" dBFS. This is the strongest moment in the recording. It can flag a recording that is too strong for the microphone, but cannot rate your voice.\n");
        details.append("\nInitial sound level: ").append(formatDouble(result.noiseFloorDbfs)).append(" dBFS. This comes from an early part of the recording and may include your voice. It cannot isolate room noise or act as a calibrated noise meter.\n");
        details.append("\nSignal compared with the start: ").append(formatDouble(result.snrDb)).append(" dB. This compares the whole recording with that initial sound estimate. It may help flag a change in recording conditions, but it is not a reliable measure of speech clarity.\n");
        details.append("\nPitch: ")
                .append(Double.isNaN(result.medianF0Hz) ? "not available" : formatDouble(result.medianF0Hz) + " Hz")
                .append(". Pitch means how high or low a voice sounds. It may help describe this recording, but natural changes are normal and this is not a clinical voice assessment.\n");
        details.append("\nVoice start: ").append(estimateOnsetMs(result))
                .append(". This is when the sound check first detected activity. It may help review timing, but is not the exact start of voice production.\n");
        details.append("\nRecording check: ").append(formatQuality(result.quality)).append(" ")
                .append(formatQualityFlags(result.qualityFlags))
                .append(" These checks describe the recording only, not pronunciation, intelligibility, or recovery.\n");
        details.append("\nAccelerometer and skin-sensor readings: unavailable. No wearable is connected.");
        return details.toString();
    }

    private String formatQuality(String quality) {
        if ("USABLE".equals(quality)) return "Basic signal checks passed; this is not a clinical quality rating.";
        if ("LIMITED".equals(quality)) return "Limited — one or more checks raised a caution.";
        if ("INVALID".equals(quality)) return "Unavailable — the recording could not be analysed.";
        return "Unavailable.";
    }

    private String formatQualityFlags(List<String> flags) {
        if (flags == null || flags.isEmpty()
                || (flags.size() == 1 && flags.get(0).trim().isEmpty())) {
            return "No warnings were flagged by the basic checks.";
        }
        List<String> explanations = new ArrayList<>();
        for (String flag : flags) {
            switch (flag) {
                case "CLIPPING":
                    explanations.add("the recording may have captured a sound too strongly");
                    break;
                case "LOW_SNR":
                    explanations.add("background sound may affect the estimate");
                    break;
                case "VERY_QUIET":
                    explanations.add("the captured signal was very quiet");
                    break;
                case "NO_SPEECH_ACTIVITY_DETECTED":
                    explanations.add("the sound-level check did not detect voice-like activity");
                    break;
                case "PITCH_UNAVAILABLE":
                    explanations.add("pitch could not be estimated confidently");
                    break;
                case "EMPTY_OR_INVALID_INPUT":
                    explanations.add("there was not enough valid audio to analyse");
                    break;
                default:
                    explanations.add(flag.replace('_', ' ').toLowerCase(Locale.US));
                    break;
            }
        }
        return String.join("; ", explanations) + ".";
    }

    private void setMeasurementStatus() {
        StringBuilder sourceText = new StringBuilder();
        sourceText.append("Microphone: ")
                .append(latestMeasurement == null ? "ready when you choose to record."
                        : "recording captured; estimates below are from this attempt.");
        if (researchCondition != null) {
            sourceText.append("\nWearable sensors: not connected. No vibration or skin-sensor readings are available.");
        } else {
            sourceText.append("\nThis activity uses the phone microphone only.");
        }
        measurementStatusView.setText(sourceText.toString());
    }

    private void setStatus(String message) {
        statusView.setText(message);
    }

    private String formatDurationMs(long durationMs) {
        if (durationMs < 0) return "unavailable";
        if (durationMs < 60_000L) {
            return String.format(Locale.US, "%.1f seconds", durationMs / 1000.0);
        }
        return String.format(Locale.US, "%d min %d sec", durationMs / 60_000L, (durationMs % 60_000L) / 1000L);
    }

    private String formatDouble(double value) {
        if (Double.isNaN(value)) return "unavailable";
        return String.format(Locale.US, "%.1f", value);
    }

    private void releasePipeline() {
        if (pipeline != null) {
            pipeline.release();
            pipeline = null;
        }
    }

    @Override
    protected void onDestroy() {
        releasePipeline();
        super.onDestroy();
    }
}
