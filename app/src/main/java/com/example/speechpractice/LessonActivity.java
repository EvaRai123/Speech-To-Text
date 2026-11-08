package com.example.speechpractice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.app.AlertDialog;

public class LessonActivity extends AppCompatActivity {
    public static final String EXTRA_LESSON_ID = "extra_lesson_id";
    private static final String TAG = "SpeechBridgeLesson";

    private LessonDefinition lesson;
    private TextView titleView;
    private TextView areaView;
    private TextView objectiveView;
    private TextView explanationView;
    private TextView progressView;
    private TextView promptView;
    private TextView instructionView;
    private TextView statusView;
    private TextView measurementView;
    private ProgressBar progressBar;
    private EditText personalNameView;
    private Button exampleButton;
    private Button stepButton;
    private Button recordButton;
    private Button stopButton;
    private Button replayButton;
    private Button skipButton;
    private Button nextButton;
    private Button finishButton;
    private Button restButton;
    private Button cameraButton;
    private RadioGroup selfAssessment;
    private PreviewView cameraPreview;
    private TextView cameraStatusView;
    private TextView cameraExplanationView;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startRecording();
                else statusView.setText("Microphone permission was not granted. You can try again or skip this turn.");
            });
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCameraMeasurements();
                else recordCameraUnavailable(MeasurementStatus.PERMISSION_DENIED,
                        "Camera permission was denied. You can continue without camera feedback.");
            });

    private PcmPracticePipeline pipeline;
    private CameraMeasurementProvider cameraProvider;
    private TextToSpeech textToSpeech;
    private AudioTrack audioTrack;
    private short[] lastRecording;
    private int sampleRateHz = AudioCaptureManager.DEFAULT_SAMPLE_RATE_HZ;
    private int stepIndex;
    private int completedSteps;
    private int activityAttempts;
    private int skippedSteps;
    private int remainingSeconds;
    private int restRemainingSeconds;
    private boolean stepPrepared;
    private boolean recording;
    private boolean timerPaused;
    private boolean ttsReady;
    private boolean finished;
    private String selectedSelfAssessment;
    private String attemptId;
    private long startedAtElapsedMs;
    private long recordingStartedElapsedMs;
    private final List<MeasurementRecord> attemptMeasurements = new ArrayList<>();
    private final CameraMeasurementBuffer latestCameraMeasurements = new CameraMeasurementBuffer();
    private boolean cameraActive;
    private boolean cameraAttempted;
    private Integer preferredExternalMicId;
    private boolean externalMicRequired;
    private String audioRouteStatus = "NOT_ENABLED";
    private String audioRouteDetail = "Audio recording was not started during this lesson attempt.";

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (remainingSeconds <= 0) {
                stepPrepared = true;
                stepButton.setVisibility(View.GONE);
                nextButton.setVisibility(View.VISIBLE);
                statusView.setText("Round finished. Continue when you feel ready.");
                persistAttempt("in_progress", null);
                return;
            }

            statusView.setText("Take your time. " + remainingSeconds + " seconds left.");
            remainingSeconds--;
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable restTick = new Runnable() {
        @Override
        public void run() {
            if (restRemainingSeconds <= 0) {
                restButton.setText("Take a short rest");
                statusView.setText("Rest timer finished. Continue only when you feel comfortable.");
                return;
            }
            statusView.setText("Resting. " + restRemainingSeconds + " seconds left; end the rest whenever you like.");
            restRemainingSeconds--;
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lesson);

        titleView = findViewById(R.id.lesson_title);
        areaView = findViewById(R.id.lesson_area);
        objectiveView = findViewById(R.id.lesson_objective);
        explanationView = findViewById(R.id.lesson_educational_explanation);
        progressView = findViewById(R.id.lesson_progress);
        promptView = findViewById(R.id.lesson_prompt);
        instructionView = findViewById(R.id.lesson_instructions);
        statusView = findViewById(R.id.lesson_status);
        measurementView = findViewById(R.id.lesson_measurement_summary);
        progressBar = findViewById(R.id.lesson_progress_bar);
        personalNameView = findViewById(R.id.lesson_personal_name);
        exampleButton = findViewById(R.id.lesson_example_button);
        stepButton = findViewById(R.id.lesson_step_button);
        recordButton = findViewById(R.id.lesson_record_button);
        stopButton = findViewById(R.id.lesson_stop_button);
        replayButton = findViewById(R.id.lesson_replay_button);
        skipButton = findViewById(R.id.lesson_skip_button);
        nextButton = findViewById(R.id.lesson_next_button);
        finishButton = findViewById(R.id.lesson_finish_button);
        restButton = findViewById(R.id.lesson_rest_button);
        cameraButton = findViewById(R.id.lesson_camera_button);
        cameraPreview = findViewById(R.id.lesson_camera_preview);
        cameraStatusView = findViewById(R.id.lesson_camera_status);
        cameraExplanationView = findViewById(R.id.lesson_camera_explanation);
        selfAssessment = findViewById(R.id.lesson_self_assessment);
        bindButtons();

        String lessonId = getIntent().getStringExtra(EXTRA_LESSON_ID);
        lesson = LessonCatalog.getLesson(lessonId);
        if (lesson == null) {
            titleView.setText("Lesson unavailable");
            areaView.setText("This activity could not be found.");
            objectiveView.setText("Return to your lessons and choose another activity.");
            hideActivityControls();
            return;
        }
        if (lesson.prompts.isEmpty()) {
            titleView.setText("Lesson unavailable");
            areaView.setText("This activity has no practice steps.");
            objectiveView.setText("Please choose another activity.");
            hideActivityControls();
            Log.e(TAG, "Lesson " + lesson.id + " has no configured prompts.");
            return;
        }

        startedAtElapsedMs = SystemClock.elapsedRealtime();
        attemptId = CourseProgressStore.beginLessonAttempt(this, lesson.id);
        setupSelfAssessment();
        setupPersonalName();
        initializeTextToSpeech();
        showActivityControls();
        renderStep();
    }

    private void bindButtons() {
        stepButton.setOnClickListener(v -> beginCurrentStep());
        recordButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                statusView.setText("The microphone is used only after you choose Record. Your voice is analysed on this phone.");
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        });
        stopButton.setOnClickListener(v -> stopRecording());
        exampleButton.setOnClickListener(v -> speakExample());
        replayButton.setOnClickListener(v -> playLastRecording());
        skipButton.setOnClickListener(v -> skipCurrentStep());
        nextButton.setOnClickListener(v -> advanceToNextStep());
        finishButton.setOnClickListener(v -> finishLesson());
        restButton.setOnClickListener(v -> toggleRest());
        cameraButton.setOnClickListener(v -> toggleCamera());
        findViewById(R.id.lesson_back_button).setOnClickListener(v -> returnToArea());
        findViewById(R.id.lesson_progress_button).setOnClickListener(v ->
                startActivity(new Intent(this, ProgressDashboardActivity.class)));
        selfAssessment.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = findViewById(checkedId);
            selectedSelfAssessment = selected == null ? null : selected.getText().toString();
            finishButton.setEnabled(canFinishCurrentLesson());
        });
    }

    private void showActivityControls() {
        stepButton.setVisibility(lesson.recordingRequired ? View.GONE : View.VISIBLE);
        recordButton.setVisibility(lesson.recordingRequired ? View.VISIBLE : View.GONE);
        stopButton.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        skipButton.setVisibility(View.VISIBLE);
        finishButton.setVisibility(View.GONE);
        replayButton.setVisibility(View.GONE);
        restButton.setVisibility(View.GONE);
    }

    private void hideActivityControls() {
        stepButton.setVisibility(View.GONE);
        recordButton.setVisibility(View.GONE);
        stopButton.setVisibility(View.GONE);
        exampleButton.setVisibility(View.GONE);
        skipButton.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        finishButton.setVisibility(View.GONE);
    }

    private void renderStep() {
        if (stepIndex >= lesson.prompts.size()) {
            showFinishPanel();
            return;
        }
        stepPrepared = false;
        cancelRest(false);
        timerPaused = false;
        remainingSeconds = 0;
        restButton.setText("Take a short rest");
        String prompt = lesson.prompts.get(stepIndex);
        personalNameView.setVisibility("connected_1".equals(lesson.id) && stepIndex == 0
                ? View.VISIBLE : View.GONE);
        promptView.setText(personalizedPrompt(prompt));
        progressView.setText("Turn " + (stepIndex + 1) + " of " + lesson.prompts.size());
        progressBar.setMax(lesson.prompts.size());
        progressBar.setProgress(completedSteps);
        instructionView.setText("Let's try!\n" + lesson.instructions);
        String groupContext = lesson.groupTitle.isEmpty() ? "" : lesson.groupTitle + "  ·  ";
        areaView.setText(groupContext + lesson.areaTitle + "  ·  " + lesson.estimatedDuration);
        titleView.setText(lesson.title);
        objectiveView.setText("What will we do?\n" + lesson.objective);
        explanationView.setText("Why?\n" + lesson.educationalExplanation);
        explanationView.setVisibility(lesson.educationalExplanation.trim().isEmpty() ? View.GONE : View.VISIBLE);
        LessonMeasurementRequirements.ChannelRequirement cameraRequirement =
                lesson.measurementRequirements.forSource(MeasurementSource.CAMERA);
        boolean cameraApplicable = cameraRequirement.need != LessonMeasurementRequirements.Need.NOT_APPLICABLE;
        cameraExplanationView.setVisibility(cameraApplicable ? View.VISIBLE : View.GONE);
        cameraStatusView.setVisibility(cameraApplicable ? View.VISIBLE : View.GONE);
        cameraButton.setVisibility(cameraApplicable ? View.VISIBLE : View.GONE);
        cameraPreview.setVisibility(cameraActive ? View.VISIBLE : View.GONE);
        if (cameraApplicable) {
            cameraStatusView.setText(cameraRequirement.limitation
                    + " Camera feedback is optional. Images are processed on-device and not stored.");
        }
        statusView.setText(lesson.recordingRequired
                ? "When you are ready, record this turn. You can replay it, retry, or skip."
                : "When you are ready, begin this turn. You can pause or skip at any time.");
        measurementView.setVisibility(View.GONE);
        exampleButton.setVisibility(isSpeakableExample() && ttsReady ? View.VISIBLE : View.GONE);
        recordButton.setVisibility(lesson.recordingRequired ? View.VISIBLE : View.GONE);
        stepButton.setVisibility(lesson.recordingRequired ? View.GONE : View.VISIBLE);
        stepButton.setText(lesson.timedStepSeconds > 0
                ? "Start this gentle round" : "I've tried this step");
        skipButton.setVisibility(View.VISIBLE);
        nextButton.setVisibility(View.GONE);
        replayButton.setVisibility(lastRecording == null ? View.GONE : View.VISIBLE);
        setIllustration();
    }

    private boolean isSpeakableExample() {
        return LessonDefinition.ACTIVITY_SOUND.equals(lesson.activityType)
                || LessonDefinition.ACTIVITY_WORD.equals(lesson.activityType)
                || LessonDefinition.ACTIVITY_PHRASE.equals(lesson.activityType)
                || LessonDefinition.ACTIVITY_READING.equals(lesson.activityType);
    }

    private void setIllustration() {
        ImageView illustration = findViewById(R.id.lesson_illustration);
        illustration.setImageResource(VisualContent.lessonIllustration(lesson));
        illustration.setContentDescription(VisualContent.lessonIllustrationDescription(lesson));
    }

    private void beginCurrentStep() {
        if (stepPrepared || recording) return;
        activityAttempts++;
        if (lesson.timedStepSeconds > 0) {
            remainingSeconds = lesson.timedStepSeconds;
            stepButton.setVisibility(View.GONE);
            statusView.setText("Take a comfortable breath. No need to breathe deeply or hold your breath.");
            handler.removeCallbacks(timerTick);
            timerPaused = false;
            restButton.setText("Take a short rest");
            handler.post(timerTick);
        } else {
            stepPrepared = true;
            stepButton.setVisibility(View.GONE);
            nextButton.setVisibility(View.VISIBLE);
            statusView.setText("Step done. Continue when you feel ready.");
        }
        persistAttempt("in_progress", null);
    }

    private void startRecording() {
        if (recording || finished) return;
        showAudioInputChoices();
    }

    private void startRecording(Integer externalDeviceId, boolean requireExternal) {
        if (recording || finished) return;
        cancelRest(false);
        releasePipeline();
        pipeline = new PcmPracticePipeline(this);
        pipeline.setListener(recordingListener);
        preferredExternalMicId = externalDeviceId;
        externalMicRequired = requireExternal;
        recordingStartedElapsedMs = SystemClock.elapsedRealtime();
        if (!pipeline.startForMeasurement(null, externalDeviceId, requireExternal)) {
            statusView.setText("The selected microphone could not start or its route could not be verified.");
            releasePipeline();
            return;
        }
        recording = true;
        activityAttempts++;
        recordButton.setVisibility(View.GONE);
        stopButton.setVisibility(View.VISIBLE);
        skipButton.setVisibility(View.GONE);
        statusView.setText("Recording. Say the prompt comfortably, then tap Stop recording.");
        persistAttempt("in_progress", null);
    }

    private void showAudioInputChoices() {
        List<AudioInputDeviceManager.InputDevice> externalInputs =
                AudioInputDeviceManager.getExternalInputs(this);
        if (externalInputs.isEmpty()) {
            externalMicRequired = false;
            preferredExternalMicId = null;
            statusView.setText("No external microphone is currently available. Recording will use Android's default input.");
            startRecording(null, false);
            return;
        }
        String[] labels = new String[externalInputs.size() + 1];
        labels[0] = "Android default input (route not externally verified)";
        for (int i = 0; i < externalInputs.size(); i++) {
            AudioInputDeviceManager.InputDevice device = externalInputs.get(i);
            labels[i + 1] = "External: " + device.name + " (verify route)";
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose recording input")
                .setItems(labels, (dialog, selected) -> {
                    if (selected == 0) startRecording(null, false);
                    else {
                        AudioInputDeviceManager.InputDevice selectedDevice = externalInputs.get(selected - 1);
                        statusView.setText("Selected external input: " + selectedDevice.name
                                + ". The actual routed input will be checked when capture starts.");
                        startRecording(selectedDevice.id, true);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleCamera() {
        if (lesson == null) return;
        if (cameraActive) {
            stopCameraMeasurements();
            return;
        }
        Set<MeasurementType> requested = lesson.measurementRequirements
                .forSource(MeasurementSource.CAMERA).relevantTypes;
        boolean hasSupportedType = false;
        for (MeasurementType type : requested) {
            if (CameraMeasurementSemantics.isSupported(type)) {
                hasSupportedType = true;
                break;
            }
        }
        if (!hasSupportedType) {
            recordCameraUnsupported(requested);
            return;
        }
        cameraAttempted = true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCameraMeasurements();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCameraMeasurements() {
        if (lesson == null || cameraActive) return;
        cameraAttempted = true;
        Set<MeasurementType> requested = lesson.measurementRequirements
                .forSource(MeasurementSource.CAMERA).relevantTypes;
        if (requested.isEmpty()) return;
        boolean hasSupportedType = false;
        for (MeasurementType type : requested) {
            if (CameraMeasurementSemantics.isSupported(type)) {
                hasSupportedType = true;
                break;
            }
        }
        if (!hasSupportedType) {
            recordCameraUnsupported(requested);
            return;
        }
        if (cameraProvider != null) {
            cameraProvider.stop();
            cameraProvider = null;
        }
        cameraPreview.setVisibility(View.VISIBLE);
        cameraStatusView.setText("Starting front camera. Keep your face centered and well lit.");
        cameraProvider = new CameraMeasurementProvider(this, this, cameraPreview);
        cameraProvider.start(requested, new MeasurementProvider.Listener() {
            @Override
            public void onMeasurement(MeasurementRecord measurement) {
                if (finished || cameraProvider == null || measurement == null
                        || measurement.source != MeasurementSource.CAMERA) return;
                latestCameraMeasurements.put(measurement);
                cameraStatusView.setText(measurement.status == MeasurementStatus.AVAILABLE
                        ? "On-device facial contours detected. Values are relative observations, not pronunciation scores."
                        : measurement.detail);
            }

            @Override
            public void onStatusChanged(MeasurementSource source, MeasurementStatus status, String detail) {
                if (finished) return;
                cameraStatusView.setText(detail);
                if (status == MeasurementStatus.AVAILABLE) {
                    cameraActive = cameraProvider.getStatus() == MeasurementStatus.AVAILABLE;
                    cameraButton.setText(cameraActive ? "Stop camera feedback" : "Start optional camera feedback");
                }
            }

            @Override
            public void onFailure(MeasurementSource source, String detail, Throwable cause) {
                Log.w(TAG, "Camera measurement failed for " + lesson.id + ": " + detail, cause);
            }
        });
        cameraActive = cameraProvider.getStatus() == MeasurementStatus.AVAILABLE;
        cameraButton.setText(cameraActive ? "Stop camera feedback" : "Start optional camera feedback");
    }

    private void recordCameraUnavailable(MeasurementStatus status, String detail) {
        if (lesson == null) return;
        cameraAttempted = true;
        for (MeasurementType type : lesson.measurementRequirements
                .forSource(MeasurementSource.CAMERA).relevantTypes) {
            latestCameraMeasurements.put(MeasurementRecord.unavailable(
                    MeasurementSource.CAMERA, type, status, detail));
        }
        persistCameraMeasurements();
        CourseProgressStore.updateLessonCameraStatus(this, attemptId, "UNAVAILABLE");
        cameraStatusView.setText(detail);
    }

    private void recordCameraUnsupported(Set<MeasurementType> types) {
        if (lesson == null || attemptId == null || types.isEmpty()) return;
        cameraAttempted = true;
        for (MeasurementType type : types) {
            latestCameraMeasurements.put(CameraMeasurementSemantics.unavailableRecord(type));
        }
        persistCameraMeasurements();
        CourseProgressStore.updateLessonCameraStatus(this, attemptId, "UNAVAILABLE");
        cameraStatusView.setText("Camera feedback is unsupported for this lesson: "
                + CameraMeasurementSemantics.unsupportedDetail(types.iterator().next()));
        cameraButton.setText("Camera measurement unsupported");
        cameraButton.setEnabled(false);
    }

    private void stopCameraMeasurements() {
        boolean cameraWasRequested = cameraActive || cameraProvider != null
                || latestCameraMeasurements.hasMeasurements();
        int savedCount = latestCameraMeasurements.size();
        if (cameraWasRequested && lesson != null) {
            for (MeasurementType type : lesson.measurementRequirements
                    .forSource(MeasurementSource.CAMERA).relevantTypes) {
                if (!latestCameraMeasurements.contains(type)) {
                    latestCameraMeasurements.put(MeasurementRecord.unavailable(
                            MeasurementSource.CAMERA, type, MeasurementStatus.DISCONNECTED,
                            "Camera stopped before a usable observation for this type was available."));
                }
            }
        }
        if (cameraProvider != null) {
            cameraProvider.stop();
            cameraProvider = null;
        }
        cameraActive = false;
        cameraPreview.setVisibility(View.GONE);
        cameraButton.setText("Start optional camera feedback");
        if (!cameraWasRequested) return;
        persistCameraMeasurements();
        CourseProgressStore.updateLessonCameraStatus(this, attemptId, "ATTEMPTED");
        cameraStatusView.setText("Camera stopped. " + savedCount
                + " measurement types had observations; missing observations were recorded as unavailable.");
    }

    private void persistCameraMeasurements() {
        if (attemptId == null || lesson == null) return;
        for (MeasurementRecord measurement : latestCameraMeasurements.drain()) {
            attemptMeasurements.add(measurement);
            CourseProgressStore.addLessonMeasurement(this, attemptId, lesson.id, measurement);
        }
    }

    private void stopRecording() {
        if (recording && pipeline != null) {
            statusView.setText("Recording stopped. Checking the sound captured by the phone.");
            stopButton.setEnabled(false);
            pipeline.stop();
        }
    }

    private final PcmPracticePipeline.Listener recordingListener = new PcmPracticePipeline.Listener() {
        @Override public void onReady() { }

        @Override
        public void onStarted() {
            if (recording) statusView.setText("Recording. Speak comfortably; stop whenever you are ready.");
        }

        @Override
        public void onInputRoute(String selectedInput, String actualRoute, boolean verified) {
            if (externalMicRequired) {
                if (verified && attemptId != null) {
                    audioRouteStatus = "ROUTE_VERIFIED";
                    audioRouteDetail = "Selected external input was verified as the actual routed AudioRecord device (observed route label: "
                            + actualRoute + "; this label is not a stable configuration identifier).";
                    CourseProgressStore.updateAttemptSourceStatus(LessonActivity.this,
                            attemptId, MeasurementSource.AUDIO, audioRouteStatus, audioRouteDetail);
                } else if (attemptId != null) {
                    audioRouteStatus = "ROUTE_UNVERIFIED";
                    audioRouteDetail = "External microphone route was not verified; measurements must not be attributed to the selected external input.";
                    CourseProgressStore.updateAttemptSourceStatus(LessonActivity.this,
                            attemptId, MeasurementSource.AUDIO, audioRouteStatus, audioRouteDetail);
                }
                statusView.setText(verified
                        ? "External input selected: " + selectedInput + ". Actual Android route: " + actualRoute + "."
                        : "External input selected: " + selectedInput + ". Actual route not verified: " + actualRoute + ".");
            } else {
                audioRouteStatus = "DEFAULT_INPUT_USED";
                audioRouteDetail = "Android's actual input route was observed, but external microphone verification was not requested.";
                statusView.setText("Android default input selected. Actual route: " + actualRoute
                        + ". External input verification was not requested.");
            }
        }

        @Override public void onInputLevel(double dbfs, boolean speechLikely) { }
        @Override public void onPartialTranscript(String transcript) { }
        @Override public void onFinalTranscript(String transcript) { }

        @Override
        public void onAudioCaptured(short[] samples, int capturedSampleRateHz) {
            lastRecording = samples.clone();
            sampleRateHz = capturedSampleRateHz;
            replayButton.setVisibility(View.VISIBLE);
        }

        @Override
        public void onStopped(AudioMeasurementResult result) {
            recording = false;
            stopButton.setVisibility(View.GONE);
            stopButton.setEnabled(true);
            recordButton.setVisibility(View.VISIBLE);
            skipButton.setVisibility(View.VISIBLE);
            long receivedAt = System.currentTimeMillis();
            long durationMs = Math.max(0L, SystemClock.elapsedRealtime() - recordingStartedElapsedMs);
            addMeasurement(MeasurementRecord.available(MeasurementSource.AUDIO, MeasurementType.ATTEMPT_DURATION,
                    durationMs, "ms", null, receivedAt, qualityFor(result), null,
                    "Elapsed time for this captured audio attempt; acquisition clock time is not exposed by the current pipeline."));
            if (result == null || "INVALID".equals(result.quality)) {
                markAudioAnalysisUnavailable("The captured audio could not be analysed.");
                statusView.setText("The recording did not contain usable sound. Try again, or skip this turn.");
                measurementView.setText("No reliable measurement is available for this recording.");
                stepPrepared = false;
            } else {
                captureMeasurementRecords(result);
                String summary = measurementSummary(result);
                measurementView.setText(summary);
                measurementView.setVisibility(View.VISIBLE);
                statusView.setText("Recording captured. You can replay it, try again, or continue.");
                stepPrepared = true;
                nextButton.setVisibility(View.VISIBLE);
                persistAttempt("in_progress", summary);
            }
        }

        @Override
        public void onError(String message, Throwable cause) {
            if (externalMicRequired) {
                audioRouteStatus = "ROUTE_LOST";
                audioRouteDetail = "External microphone capture failed or its verified route was lost: " + message;
                if (attemptId != null) {
                    CourseProgressStore.updateAttemptSourceStatus(LessonActivity.this,
                            attemptId, MeasurementSource.AUDIO, audioRouteStatus, audioRouteDetail);
                }
            }
            recording = false;
            stopButton.setVisibility(View.GONE);
            stopButton.setEnabled(true);
            recordButton.setVisibility(View.VISIBLE);
            skipButton.setVisibility(View.VISIBLE);
            long receivedAt = System.currentTimeMillis();
            long durationMs = Math.max(0L, SystemClock.elapsedRealtime() - recordingStartedElapsedMs);
            addMeasurement(MeasurementRecord.available(MeasurementSource.AUDIO, MeasurementType.ATTEMPT_DURATION,
                    durationMs, "ms", null, receivedAt, MeasurementQuality.UNKNOWN, null,
                    "Elapsed time before audio capture failed; acquisition clock time is not exposed by the current pipeline."));
            markAudioAnalysisUnavailable("Audio analysis failed before a reliable measurement was available.");
            statusView.setText("Recording could not be completed: " + message);
            Log.w(TAG, "Lesson recording failed for " + lesson.id, cause);
        }
    };

    private void captureMeasurementRecords(AudioMeasurementResult result) {
        // AudioRecord does not expose a reliable per-frame acquisition timestamp here.
        long receivedAt = System.currentTimeMillis();

        if (!"INVALID".equals(result.quality) && Double.isFinite(result.speechActiveDurationMs)
                && result.speechActiveDurationMs >= 0.0) {
            addMeasurement(MeasurementRecord.available(MeasurementSource.AUDIO, MeasurementType.SPEECH_ACTIVITY,
                    result.speechActiveDurationMs, "ms", null, receivedAt, qualityFor(result),
                    null,                     "Sound-level estimate of voice-like activity; zero means none detected, not a pronunciation score."));
        } else {
            addUnavailableMeasurement(MeasurementType.SPEECH_ACTIVITY, MeasurementStatus.POOR_SIGNAL,
                    "No voice-like activity was detected reliably.");
        }

        if (!"INVALID".equals(result.quality) && Double.isFinite(result.voicedDurationMs)
                && result.voicedDurationMs >= 0.0) {
            addMeasurement(MeasurementRecord.available(MeasurementSource.AUDIO, MeasurementType.VOICED_DURATION,
                    result.voicedDurationMs, "ms", null, receivedAt, qualityFor(result),
                    result.pitchConfidence > 0.0 ? result.pitchConfidence : null,
                    "Estimated duration of frames with a usable periodic pitch estimate; zero means none detected."));
        } else {
            addUnavailableMeasurement(MeasurementType.VOICED_DURATION, MeasurementStatus.POOR_SIGNAL,
                    "No reliable voiced frames were available for a pitch estimate.");
        }

        if (Double.isFinite(result.pauseDurationMs) && result.pauseDurationMs >= 0.0) {
            addMeasurement(MeasurementRecord.available(MeasurementSource.AUDIO, MeasurementType.PAUSE_DURATION,
                    result.pauseDurationMs, "ms", null, receivedAt, qualityFor(result), null,
                    "Duration identified as pauses by the phone audio activity detector."));
        } else {
            addUnavailableMeasurement(MeasurementType.PAUSE_DURATION, MeasurementStatus.FAILURE,
                    "Pause duration could not be estimated from this recording.");
        }

        if (Double.isFinite(result.rmsDbfs)) {
            addMeasurement(MeasurementRecord.available(MeasurementSource.AUDIO, MeasurementType.RELATIVE_INTENSITY,
                    result.rmsDbfs, "dBFS", null, receivedAt, qualityFor(result), null,
                    "Relative microphone signal level; not calibrated sound pressure level."));
        } else {
            addUnavailableMeasurement(MeasurementType.RELATIVE_INTENSITY, MeasurementStatus.FAILURE,
                    "Relative microphone level could not be estimated.");
        }
        if (Double.isFinite(result.medianF0Hz) && Double.isFinite(result.pitchConfidence)
                && result.pitchConfidence >= 0.30) {
            addMeasurement(MeasurementRecord.available(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                    result.medianF0Hz, "Hz", null, receivedAt, qualityFor(result), result.pitchConfidence,
                    "Estimated pitch from the captured audio."));
        } else {
            addUnavailableMeasurement(MeasurementType.FUNDAMENTAL_FREQUENCY, MeasurementStatus.POOR_SIGNAL,
                    "Pitch could not be estimated confidently from this recording.");
        }
        if (Double.isFinite(result.f0VariabilityHz) && Double.isFinite(result.pitchConfidence)
                && result.pitchConfidence >= 0.30) {
            addMeasurement(MeasurementRecord.available(MeasurementSource.AUDIO, MeasurementType.PITCH_VARIATION,
                    result.f0VariabilityHz, "Hz", null, receivedAt, qualityFor(result),
                    result.pitchConfidence, "Variation in estimated pitch; this is not a voice quality score."));
        } else {
            addUnavailableMeasurement(MeasurementType.PITCH_VARIATION, MeasurementStatus.POOR_SIGNAL,
                    "Pitch variation could not be estimated confidently from this recording.");
        }
    }

    private void markAudioAnalysisUnavailable(String detail) {
        LessonMeasurementRequirements.ChannelRequirement audioRequirement =
                lesson.measurementRequirements.forSource(MeasurementSource.AUDIO);
        for (MeasurementType type : audioRequirement.relevantTypes) {
            if (type != MeasurementType.ATTEMPT_DURATION) {
                addUnavailableMeasurement(type, MeasurementStatus.FAILURE, detail);
            }
        }
    }

    private MeasurementQuality qualityFor(AudioMeasurementResult result) {
        if (result == null) return MeasurementQuality.UNKNOWN;
        return SignalQualityAssessment.forAudio(result).quality;
    }

    private void addMeasurement(MeasurementRecord measurement) {
        LessonMeasurementRequirements.ChannelRequirement audioRequirement =
                lesson.measurementRequirements.forSource(MeasurementSource.AUDIO);
        if (!audioRequirement.relevantTypes.contains(measurement.type)) return;
        attemptMeasurements.add(measurement);
        CourseProgressStore.addLessonMeasurement(this, attemptId, lesson.id, measurement);
    }

    private void addUnavailableMeasurement(MeasurementType type, MeasurementStatus status, String detail) {
        LessonMeasurementRequirements.ChannelRequirement audioRequirement =
                lesson.measurementRequirements.forSource(MeasurementSource.AUDIO);
        if (!audioRequirement.relevantTypes.contains(type)) return;
        MeasurementRecord measurement = MeasurementRecord.unavailable(
                MeasurementSource.AUDIO, type, status, detail);
        attemptMeasurements.add(measurement);
        CourseProgressStore.addLessonMeasurement(this, attemptId, lesson.id, measurement);
    }

    private String measurementSummary(AudioMeasurementResult result) {
        StringBuilder summary = new StringBuilder("Recording feedback\n");
        if (result.speechActiveDurationMs > 0) {
            summary.append("The phone detected voice-like sound for about ")
                    .append(Math.round(result.speechActiveDurationMs / 100.0) / 10.0)
                    .append(" seconds. This is a rough estimate, not a score.\n");
        } else {
            summary.append("No clear voice activity was detected. The sound may be too quiet or affected by background noise.\n");
        }
        if (!Double.isNaN(result.medianF0Hz) && result.pitchConfidence >= 0.30) {
            summary.append("Estimated pitch: about ").append(Math.round(result.medianF0Hz))
                    .append(" Hz. This estimate can be affected by the recording.\n");
        }
        summary.append("Microphone position and phone settings affect these results. They cannot judge pronunciation, breathing, or health.");
        return summary.toString();
    }

    private void speakExample() {
        if (ttsReady && textToSpeech != null) {
            textToSpeech.speak(promptView.getText().toString(), TextToSpeech.QUEUE_FLUSH, null, "lesson_example");
        } else {
            statusView.setText("A spoken example is not available on this device.");
        }
    }

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, result -> {
            ttsReady = result == TextToSpeech.SUCCESS
                    && textToSpeech.setLanguage(Locale.getDefault()) >= TextToSpeech.LANG_AVAILABLE;
            if (lesson != null && isSpeakableExample()) {
                exampleButton.setVisibility(ttsReady ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void setupPersonalName() {
        personalNameView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (lesson != null && "connected_1".equals(lesson.id) && stepIndex == 0) {
                    promptView.setText(personalizedPrompt(lesson.prompts.get(0)));
                }
            }
            @Override public void afterTextChanged(Editable value) { }
        });
    }

    private String personalizedPrompt(String prompt) {
        if (!"connected_1".equals(lesson.id) || stepIndex != 0) return prompt;
        String name = personalNameView.getText().toString().trim();
        return name.isEmpty() ? prompt : "Hello, my name is " + name + ".";
    }

    private void playLastRecording() {
        if (lastRecording == null || lastRecording.length == 0) {
            statusView.setText("There is no recording to replay yet.");
            return;
        }
        releaseAudioTrack();
        try {
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(lastRecording.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            int written = audioTrack.write(lastRecording, 0, lastRecording.length, AudioTrack.WRITE_BLOCKING);
            if (written != lastRecording.length) {
                statusView.setText("This recording could not be replayed completely.");
                releaseAudioTrack();
                return;
            }
            AudioTrack activeTrack = audioTrack;
            activeTrack.setNotificationMarkerPosition(lastRecording.length);
            activeTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override public void onMarkerReached(AudioTrack track) { handler.post(LessonActivity.this::releaseAudioTrack); }
                @Override public void onPeriodicNotification(AudioTrack track) { }
            });
            activeTrack.play();
            statusView.setText("Playing your recording.");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            Log.e(TAG, "Could not replay lesson audio.", exception);
            statusView.setText("This recording could not be replayed on this device.");
            releaseAudioTrack();
        }
    }

    private void releaseAudioTrack() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
            } catch (IllegalStateException ignored) {
            }
            audioTrack.release();
            audioTrack = null;
        }
    }

    private void skipCurrentStep() {
        if (recording) {
            statusView.setText("Stop the recording before skipping this turn.");
            return;
        }
        handler.removeCallbacks(timerTick);
        cancelRest(false);
        skippedSteps++;
        activityAttempts++;
        timerPaused = false;
        remainingSeconds = 0;
        restButton.setText("Take a short rest");
        stepPrepared = true;
        statusView.setText("This turn was skipped. Continue when you are ready.");
        nextButton.setVisibility(View.VISIBLE);
        stepButton.setVisibility(View.GONE);
        recordButton.setVisibility(View.GONE);
        skipButton.setVisibility(View.GONE);
        persistAttempt("in_progress", null);
    }

    private void advanceToNextStep() {
        if (!stepPrepared) return;
        cancelRest(false);
        completedSteps++;
        stepIndex++;
        lastRecording = null;
        replayButton.setVisibility(View.GONE);
        persistAttempt("in_progress", null);
        renderStep();
    }

    private void showFinishPanel() {
        progressBar.setProgress(lesson.prompts.size());
        progressView.setText("Activity steps finished");
        promptView.setText("Well done!");
        instructionView.setText("You worked through this activity. This records practice participation, not a medical result.");
        statusView.setText("You can finish now or return to your lessons.");
        exampleButton.setVisibility(View.GONE);
        stepButton.setVisibility(View.GONE);
        recordButton.setVisibility(View.GONE);
        stopButton.setVisibility(View.GONE);
        replayButton.setVisibility(lastRecording == null ? View.GONE : View.VISIBLE);
        skipButton.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        if (lesson.selfAssessmentRequired) {
            selfAssessment.setVisibility(View.VISIBLE);
            finishButton.setText("Save and finish");
        } else {
            selfAssessment.setVisibility(View.GONE);
            finishButton.setText("Finish activity");
        }
        String blocker = lesson.measurementRequirements.getRequiredMeasurementBlocker(attemptMeasurements);
        boolean participationMet = LessonParticipationPolicy.canComplete(lesson, completedSteps, skippedSteps);
        finishButton.setEnabled(blocker == null && participationMet
                && (!lesson.selfAssessmentRequired || selectedSelfAssessment != null));
        if (blocker != null) {
            statusView.setText(blocker + " Practice can remain saved as incomplete.");
        } else if (!participationMet) {
            statusView.setText("Try at least one practice turn without skipping to save lesson participation. "
                    + "This records practice, not demonstrated skill improvement.");
        } else if ("mouth_articulation".equals(lesson.areaKey)) {
            statusView.setText("Your practice participation can be saved. This does not measure skill improvement.");
        }
        finishButton.setVisibility(View.VISIBLE);
    }

    private void setupSelfAssessment() {
        addAssessmentChoice("Comfortable");
        addAssessmentChoice("A little effort");
        addAssessmentChoice("I want to change or stop");
        selfAssessment.setVisibility(View.GONE);
    }

    private void addAssessmentChoice(String label) {
        RadioButton choice = new RadioButton(this);
        choice.setId(View.generateViewId());
        choice.setText(label);
        choice.setTextColor(getColor(R.color.ink));
        choice.setMinHeight(48);
        selfAssessment.addView(choice);
    }

    private void finishLesson() {
        String blocker = lesson.measurementRequirements.getRequiredMeasurementBlocker(attemptMeasurements);
        if (blocker != null) {
            statusView.setText(blocker + " This lesson cannot be marked complete without it.");
            finishButton.setEnabled(false);
            return;
        }
        if (!LessonParticipationPolicy.canComplete(lesson, completedSteps, skippedSteps)) {
            statusView.setText("Try at least one practice turn without skipping before saving lesson participation. "
                    + "This does not measure skill improvement.");
            finishButton.setEnabled(false);
            return;
        }
        if (lesson.selfAssessmentRequired && selectedSelfAssessment == null) {
            statusView.setText("Choose how the activity felt before saving it.");
            return;
        }
        finished = true;
        stopCameraMeasurements();
        if (!cameraAttempted && lesson.measurementRequirements
                .forSource(MeasurementSource.CAMERA).need != LessonMeasurementRequirements.Need.NOT_APPLICABLE) {
            CourseProgressStore.updateLessonCameraStatus(this, attemptId, "NOT_COLLECTED");
        }
        CourseProgressStore.finishLessonAttempt(this, attemptId, lesson.id, "completed",
                completedSteps, activityAttempts, skippedSteps, elapsedDuration(), selectedSelfAssessment);
        progressBar.setProgress(lesson.prompts.size());
        progressView.setText("Saved to My Progress");
        promptView.setText("Activity saved");
        objectiveView.setText("You can return to this lesson and practise again whenever you want.");
        statusView.setText(skippedSteps == 0
                ? "Your practice activity is complete."
                : "Your activity is saved, including " + skippedSteps + " skipped turn(s).");
        if ("mouth_articulation".equals(lesson.areaKey)) {
            statusView.setText(statusView.getText() + " This records participation, not skill improvement.");
        }
        finishButton.setEnabled(false);
        finishButton.setText("Saved");
        skipButton.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        cameraButton.setVisibility(View.GONE);
        cameraPreview.setVisibility(View.GONE);
        persistAttempt("completed", null);
    }

    private void recordAttemptSourceStatuses() {
        if ("NOT_ENABLED".equals(audioRouteStatus) && hasSourceMeasurement(MeasurementSource.AUDIO)) {
            audioRouteStatus = "DEFAULT_INPUT_USED";
            audioRouteDetail = "Audio measurements were collected using Android's default input; external routing was not verified.";
        }
        CourseProgressStore.updateAttemptSourceStatus(this, attemptId, MeasurementSource.AUDIO,
                audioRouteStatus, audioRouteDetail);
        if (!cameraAttempted) {
            CourseProgressStore.updateAttemptSourceStatus(this, attemptId, MeasurementSource.CAMERA,
                    "NOT_ENABLED", "The user did not start optional camera feedback.");
        } else {
            recordSourceStatus(MeasurementSource.CAMERA, "ATTEMPTED",
                    "Camera was attempted, but no available camera measurement was saved.");
        }
        CourseProgressStore.updateAttemptSourceStatus(this, attemptId, MeasurementSource.ACCELEROMETER,
                "NOT_CONFIGURED", "No wearable acquisition or processing configuration is connected to guided lesson attempts.");
        CourseProgressStore.updateAttemptSourceStatus(this, attemptId, MeasurementSource.SEMG,
                "NOT_CONFIGURED", "No sEMG acquisition or processing configuration is connected to guided lesson attempts.");
    }

    private void recordSourceStatus(MeasurementSource source, String emptyStatus, String emptyDetail) {
        MeasurementRecord latest = null;
        MeasurementRecord available = null;
        for (MeasurementRecord measurement : attemptMeasurements) {
            if (measurement.source == source) {
                latest = measurement;
                if (measurement.status == MeasurementStatus.AVAILABLE) available = measurement;
            }
        }
        if (latest == null) {
            CourseProgressStore.updateAttemptSourceStatus(this, attemptId, source, emptyStatus, emptyDetail);
            return;
        }
        if (available != null) latest = available;
        String status = latest.status == MeasurementStatus.AVAILABLE ? "COLLECTED" : latest.status.name();
        CourseProgressStore.updateAttemptSourceStatus(this, attemptId, source, status, latest.detail);
    }

    private boolean hasSourceMeasurement(MeasurementSource source) {
        for (MeasurementRecord measurement : attemptMeasurements) {
            if (measurement.source == source) return true;
        }
        return false;
    }

    private boolean canFinishCurrentLesson() {
        return lesson != null
                && LessonParticipationPolicy.canComplete(lesson, completedSteps, skippedSteps)
                && lesson.measurementRequirements.getRequiredMeasurementBlocker(attemptMeasurements) == null
                && (!lesson.selfAssessmentRequired || selectedSelfAssessment != null);
    }

    private void persistAttempt(String status, String measurement) {
        if (attemptId == null || lesson == null) return;
        if (!"in_progress".equals(status)) recordAttemptSourceStatuses();
        CourseProgressStore.updateLessonAttempt(this, attemptId, lesson.id, status,
                completedSteps, activityAttempts, skippedSteps, elapsedDuration(),
                measurement, selectedSelfAssessment);
    }

    private long elapsedDuration() {
        return Math.max(0L, SystemClock.elapsedRealtime() - startedAtElapsedMs);
    }

    private void toggleRest() {
        if (remainingSeconds > 0) {
            if (timerPaused) {
                timerPaused = false;
                restButton.setText("Take a short rest");
                handler.post(timerTick);
                statusView.setText("Round resumed. Keep the activity gentle and comfortable.");
            } else {
                timerPaused = true;
                handler.removeCallbacks(timerTick);
                restButton.setText("Resume this round");
                statusView.setText("Round paused. Rest for as long as you need.");
            }
        } else if (restRemainingSeconds > 0) {
            cancelRest(true);
        } else {
            restRemainingSeconds = lesson.restSeconds;
            restButton.setText("End rest");
            statusView.setText("Rest for a moment. You can end this rest whenever you are ready.");
            handler.post(restTick);
        }
    }

    private void cancelRest(boolean endedEarly) {
        if (restRemainingSeconds <= 0) return;
        handler.removeCallbacks(restTick);
        restRemainingSeconds = 0;
        restButton.setText("Take a short rest");
        if (endedEarly) statusView.setText("Rest ended. Continue when you feel ready.");
    }

    private void returnToArea() {
        if (!finished && attemptId != null && lesson != null) persistAttempt("interrupted", null);
        if (lesson == null) {
            startActivity(new Intent(this, CurriculumActivity.class));
            finish();
            return;
        }
        Intent areaIntent = new Intent(this, AreaLessonsActivity.class);
        areaIntent.putExtra(AreaLessonsActivity.EXTRA_AREA_KEY, lesson == null ? null : lesson.areaKey);
        areaIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(areaIntent);
        finish();
    }

    private void releasePipeline() {
        if (pipeline != null) {
            pipeline.release();
            pipeline = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopCameraMeasurements();
        handler.removeCallbacks(timerTick);
        handler.removeCallbacks(restTick);
        if (!finished && attemptId != null && lesson != null && !isChangingConfigurations()) {
            persistAttempt("interrupted", null);
        }
        if (recording && pipeline != null) pipeline.stop();
        releasePipeline();
        releaseAudioTrack();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        stopCameraMeasurements();
        if (recording && pipeline != null) {
            pipeline.stop();
            statusView.setText("Recording paused because you left the activity.");
            persistAttempt("interrupted", null);
        }
        releaseAudioTrack();
        if (textToSpeech != null) textToSpeech.stop();
        super.onPause();
    }
}
