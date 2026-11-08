package com.example.speechpractice;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.EnumSet;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class ResearchWorkspaceActivity extends AppCompatActivity {
    private static final String TAG = "ResearchWorkspace";
    private static final int EXPORT_REQUEST = 6021;
    private WearableBleManager wearableManager;
    private ActivityResultLauncher<String[]> wearablePermissionLauncher;
    private int wearableWindowsProcessed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_research_workspace);
        wearableManager = new WearableBleManager(this, wearableListener);
        wearablePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> wearableManager.startScan());
        findViewById(R.id.wearable_scan_button).setOnClickListener(v -> {
            if (wearableManager.getState() == WearableConnectionState.CONNECTED
                    || wearableManager.getState() == WearableConnectionState.CONNECTING
                    || wearableManager.getState() == WearableConnectionState.SCANNING) {
                wearableManager.disconnect();
            } else if (wearableManager.hasRequiredPermissions()) {
                wearableManager.startScan();
            } else {
                wearablePermissionLauncher.launch(WearableBleManager.requiredPermissions());
            }
        });

        Button aButton = findViewById(R.id.research_condition_a);
        Button bButton = findViewById(R.id.research_condition_b);
        Button cButton = findViewById(R.id.research_condition_c);

        aButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, PracticeExerciseActivity.class);
            intent.putExtra(PracticeExerciseActivity.EXTRA_RESEARCH_CONDITION, ResearchCondition.MICROPHONE_ONLY.name());
            startActivity(intent);
        });

        bButton.setOnClickListener(v -> {
            TextView history = findViewById(R.id.research_history);
            history.setText("Condition B requires a connected accelerometer. This workspace is ready for the hardware batch but no wearable session is available yet.");
        });

        cButton.setOnClickListener(v -> {
            TextView history = findViewById(R.id.research_history);
            history.setText("Condition C requires both accelerometer and sEMG hardware. This workspace is ready for later sensor integration but no wearable data is available yet.");
        });

        findViewById(R.id.research_home_button).setOnClickListener(v -> {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(home);
            finish();
        });
        findViewById(R.id.research_export_button).setOnClickListener(v -> exportCsv());
        findViewById(R.id.research_ablation_button).setOnClickListener(v -> updateAblation());
        findViewById(R.id.research_recommendation_button).setOnClickListener(v -> updateRecommendation());
        findViewById(R.id.research_baseline_button).setOnClickListener(v -> updateBaselines());

    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHistory();
    }

    @Override
    protected void onDestroy() {
        if (wearableManager != null) wearableManager.close();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (wearableManager != null
                && wearableManager.getState() != WearableConnectionState.NOT_CONFIGURED
                && wearableManager.getState() != WearableConnectionState.DISCONNECTED) {
            wearableManager.disconnect();
        }
        super.onPause();
    }

    private final WearableBleManager.Listener wearableListener = new WearableBleManager.Listener() {
        @Override public void onStateChanged(WearableConnectionState state, String detail) {
            runOnUiThread(() -> {
                ((TextView) findViewById(R.id.wearable_status))
                        .setText("Wearable: " + state + ". " + detail);
                Button button = findViewById(R.id.wearable_scan_button);
                button.setText(state == WearableConnectionState.CONNECTED
                        ? "Disconnect wearable" : "Scan for SpeechBridge wearable");
            });
        }

        @Override public void onDeviceStatus(WearableDeviceStatus status) {
            runOnUiThread(() -> ((TextView) findViewById(R.id.wearable_status))
                    .setText("BLE connected to " + status.deviceId + " (firmware "
                            + status.firmwareVersion + "). " + status.summary()
                            + ". sEMG AFE " + (status.semgAnalogFrontEndId.isEmpty()
                            ? "not configured" : status.semgAnalogFrontEndId)
                            + ", input unit " + (status.semgInputUnit.isEmpty()
                            ? "unavailable" : status.semgInputUnit)
                            + ". Channel availability is based on device initialization/configuration status, not BLE connection alone."));
        }

        @Override public void onPacketSequence(WearableSequenceTracker.Result result) {
            if (result.disposition == WearableSequenceTracker.Disposition.GAP) {
                runOnUiThread(() -> ((TextView) findViewById(R.id.wearable_status))
                        .append("\nPacket gap detected: " + result.missingPackets
                                + " packet(s) missing; no samples were zero-filled."));
            } else if (result.disposition == WearableSequenceTracker.Disposition.NEW_SESSION) {
                runOnUiThread(() -> ((TextView) findViewById(R.id.wearable_status))
                        .append("\nDevice session changed; clock synchronization restarted."));
            }
        }

        @Override public void onPacket(WearablePacket packet, long receivedAtAndroidMonotonicNanos) {
            if (packet.type == WearableProtocol.TYPE_TIME_SYNC_RESPONSE
                    && wearableManager.isClockSynchronized()) {
                runOnUiThread(() -> ((TextView) findViewById(R.id.wearable_status))
                        .append("\nClock mapping estimated from device time and Android request/receipt midpoint; accuracy is not established."));
            }
        }

        @Override public void onProcessedWindow(ProcessedSignalWindow window) {
            wearableWindowsProcessed++;
            runOnUiThread(() -> ((TextView) findViewById(R.id.wearable_status))
                    .append("\nProcessed " + window.source + " window #" + wearableWindowsProcessed
                            + " (" + window.status + "). These sensor results are not persisted to a lesson attempt here."));
        }

        @Override public void onRejectedBatch(MeasurementSource source, String reason) {
            runOnUiThread(() -> ((TextView) findViewById(R.id.wearable_status))
                    .append("\n" + source + " batch rejected: " + reason));
        }

        @Override public void onError(String detail) {
            runOnUiThread(() -> ((TextView) findViewById(R.id.wearable_status))
                    .append("\n" + detail));
        }
    };

    private void updateHistory() {
        TextView history = findViewById(R.id.research_history);
        final List<ResearchSession> sessions;
        try {
            sessions = ResearchSessionStore.loadAll(this);
        } catch (IllegalStateException exception) {
            Log.e(TAG, "Research session history could not be read.", exception);
            history.setText("Research session history could not be read. Existing history was not modified.");
            updateMeasurementDashboard();
            return;
        }
        if (sessions.isEmpty()) {
            history.setText("No research sessions have been saved yet. A condition A recording will create the first microphone-only session.");
        } else {
            StringBuilder builder = new StringBuilder();
            for (ResearchSession session : sessions) {
                String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(session.startedAtMs));
                String signalStatus = session.microphoneMeasurement != null
                        ? "Mic: " + session.microphoneMeasurement.quality + ", RMS " + format(session.microphoneMeasurement.rmsDbfs) + " dBFS"
                        : "Mic: unavailable";
                builder.append(date).append("\n")
                        .append(session.condition.label).append(" • ")
                        .append(session.exerciseType.getLabel()).append("\n")
                        .append(signalStatus).append("\n")
                        .append(session.completed ? "Completed" : "Blocked until required hardware is connected").append("\n\n");
            }
            history.setText(builder.toString());
        }
        updateMeasurementDashboard();
        updateAblation();
    }

    private void updateMeasurementDashboard() {
        TextView dashboard = findViewById(R.id.research_dashboard);
        final List<AttemptMeasurementSet> attempts;
        try {
            attempts = CourseProgressStore.loadMeasurementAttempts(this);
        } catch (IllegalStateException exception) {
            Log.e(TAG, "Measurement dashboard could not read saved lesson attempts.", exception);
            dashboard.setText("Saved measurement history could not be read. Existing progress was not changed.");
            return;
        }
        StringBuilder text = new StringBuilder();
        updateChannelCards(attempts);
        text.append("Hardware availability\n")
                .append("Audio: saved summary measurements when available\n")
                .append("Camera: optional saved contour proxies when the user started the camera\n")
                .append("Accelerometer: unavailable; no wearable samples are connected\n")
                .append("sEMG: unavailable; no biopotential front end is connected\n")
                .append("Raw audio/video traces are not persisted by this view. Invalid rows rejected while loading are logged, not retained as attempt data.\n\n");
        int displayed = 0;
        for (AttemptMeasurementSet attempt : attempts) {
            text.append(attempt.lessonId).append(" — ").append(attempt.status)
                    .append(" (").append(attempt.attemptId).append(")\n");
            text.append("  Attempt timing: started ")
                    .append(attempt.startedAtWallClockMs > 0L
                            ? Long.toString(attempt.startedAtWallClockMs) : "unavailable")
                    .append(", ended ")
                    .append(attempt.endedAtWallClockMs > 0L
                            ? Long.toString(attempt.endedAtWallClockMs) : "not ended/legacy record")
                    .append('\n');
            text.append("  Camera status: ").append(attempt.cameraMeasurementStatus.isEmpty()
                    ? "not recorded (this does not establish whether the camera was required or enabled)"
                    : attempt.cameraMeasurementStatus).append('\n');
            for (MeasurementSource source : MeasurementSource.values()) {
                AttemptSourceStatus sourceStatus = attempt.sourceStatuses.get(source);
                if (sourceStatus != null) {
                    text.append("  ").append(source).append(" source status: ")
                            .append(sourceStatus.status);
                    if (!sourceStatus.detail.isEmpty()) text.append(" — ").append(sourceStatus.detail);
                    text.append('\n');
                }
            }
            for (MeasurementRecord measurement : attempt.measurements) {
                text.append("  ").append(measurement.source).append(" / ").append(measurement.type)
                        .append(": ");
                if (measurement.value == null) text.append(measurement.status);
                else text.append(String.format(java.util.Locale.US, "%.3f", measurement.value))
                        .append(' ').append(measurement.unit);
                text.append(" — ").append(measurement.quality);
                if (measurement.confidence != null) {
                    text.append(", reliability estimate ")
                            .append(String.format(java.util.Locale.US, "%.2f", measurement.confidence));
                }
                if (!measurement.detail.isEmpty()) text.append("; ").append(measurement.detail);
                text.append("; configuration ")
                        .append(attempt.sourceConfigurations.containsKey(measurement.source)
                                ? attempt.sourceConfigurations.get(measurement.source) : "not recorded");
                text.append('\n');
            }
            if (attempt.measurements.isEmpty()) {
                text.append("  No saved measurement rows. Consult the per-source status above; no raw signal traces are stored here.\n");
            }
            text.append('\n');
            if (++displayed >= 5) break;
        }
        if (displayed == 0) text.append("No lesson measurements are available yet.\n");
        if (!attempts.isEmpty()) {
            AttemptMeasurementSet latest = attempts.get(0);
            FusionResult fusion = new MultimodalFusionEngine().assess(latest, 250_000_000L);
            text.append("\nFusion eligibility/status for latest attempt: ").append(fusion.status)
                    .append(". Eligible channels, if any, are not a validated fusion. ")
                    .append(fusion.explanation);
            AttemptMeasurementSet previous = previousForLesson(attempts, latest);
            if (previous != null) {
                text.append("\n\nDescriptive same-lesson record comparison with previous attempt ")
                        .append("(not window/configuration-compatible ablation):\n");
                for (AttemptComparison comparison : new AttemptComparisonEngine().compare(previous, latest)) {
                    text.append(comparison.type).append(": ");
                    if (comparison.difference == null) text.append(comparison.status);
                    else text.append(String.format(java.util.Locale.US, "%+.3f %s",
                            comparison.difference, comparison.unit));
                    text.append(" (quality ").append(comparison.firstQuality)
                            .append(" → ").append(comparison.secondQuality).append(')');
                    text.append(" — ").append(comparison.explanation).append('\n');
                }
            }
        } else {
            text.append("\nFusion status: UNAVAILABLE; there are no saved attempts.");
        }
        String participantId = CalibrationProfileStore.getParticipantId(this);
        if (participantId == null) {
            text.append("\n\nPersonal baseline: unavailable; no local baseline profile exists.");
        } else {
            try {
                List<PersonalBaseline> profiles = CalibrationProfileStore.loadAll(this, participantId);
                text.append("\n\nPersonal baseline profiles for this local participant: ")
                        .append(profiles.size())
                                .append(profiles.isEmpty()
                                        ? ". No baseline is established. Creation requires repeated valid measurements and exact task/configuration matches; historical attempts without observed configuration IDs are ineligible."
                                        : ". Baselines require exact task/configuration matches and expire by validity; attempts without observed configuration IDs are ineligible.");
                if (!attempts.isEmpty()) {
                    AttemptMeasurementSet latest = attempts.get(0);
                    text.append("\nLatest-attempt baseline comparisons for ").append(latest.lessonId).append(":\n");
                    if (latest.measurements.isEmpty()) {
                        text.append("Unavailable; this attempt has no saved measurement rows.\n");
                    }
                    for (MeasurementRecord measurement : latest.measurements) {
                        String configuration = latest.sourceConfigurations.get(measurement.source);
                        BaselineComparison comparison = BaselineComparison.unavailable(
                                "No exact saved source configuration is available for this attempt.");
                        if (configuration != null) {
                            for (PersonalBaseline profile : profiles) {
                                if (profile.source == measurement.source
                                        && profile.taskId.equals(latest.lessonId)) {
                                    comparison = profile.compare(measurement, participantId,
                                            latest.lessonId, configuration, System.currentTimeMillis());
                                    if (comparison.status == MeasurementStatus.AVAILABLE) break;
                                }
                            }
                        }
                        text.append(measurement.source).append(" / ").append(measurement.type)
                                .append(": ");
                        if (comparison.difference == null) text.append(comparison.status);
                        else text.append(String.format(java.util.Locale.US, "%+.3f %s",
                                comparison.difference, comparison.unit));
                        text.append(" — ").append(comparison.explanation).append('\n');
                    }
                }
            } catch (IllegalStateException exception) {
                Log.e(TAG, "Calibration profile history could not be read.", exception);
                text.append("\n\nPersonal baseline profiles: unavailable because saved profile data could not be read.");
            }
        }
        dashboard.setText(text.toString());
    }

    private void updateChannelCards(List<AttemptMeasurementSet> attempts) {
        AttemptMeasurementSet latest = attempts.isEmpty() ? null : attempts.get(0);
        setChannelState(R.id.research_audio_state, latest, MeasurementSource.AUDIO,
                "Not collected in a saved lesson attempt.");
        setChannelState(R.id.research_camera_state, latest, MeasurementSource.CAMERA,
                "Not collected. Camera feedback is user-started and optional.");
        setChannelState(R.id.research_accelerometer_state, latest, MeasurementSource.ACCELEROMETER,
                "Not configured for lesson attempts.");
        setChannelState(R.id.research_semg_state, latest, MeasurementSource.SEMG,
                "Not configured for lesson attempts.");
    }

    private void setChannelState(int viewId, AttemptMeasurementSet attempt,
                                 MeasurementSource source, String emptyMessage) {
        TextView stateView = findViewById(viewId);
        if (attempt == null) {
            stateView.setText("No saved attempt.\n" + emptyMessage);
            return;
        }
        AttemptSourceStatus sourceStatus = attempt.sourceStatuses.get(source);
        if (sourceStatus != null) {
            stateView.setText(sourceStatus.status.replace('_', ' ') + ".\n"
                    + (sourceStatus.detail.isEmpty() ? emptyMessage : sourceStatus.detail));
            return;
        }
        for (MeasurementRecord measurement : attempt.measurements) {
            if (measurement.source == source) {
                stateView.setText(measurement.status == MeasurementStatus.AVAILABLE
                        ? "Collected: " + measurement.type.name().replace('_', ' ')
                        : measurement.status.name().replace('_', ' ') + ".\n" + measurement.detail);
                return;
            }
        }
        stateView.setText("No saved status.\n" + emptyMessage);
    }

    private AttemptMeasurementSet previousForLesson(List<AttemptMeasurementSet> attempts,
                                                    AttemptMeasurementSet latest) {
        for (int i = 1; i < attempts.size(); i++) {
            if (latest.lessonId.equals(attempts.get(i).lessonId)) return attempts.get(i);
        }
        return null;
    }

    private void updateAblation() {
        TextView resultView = findViewById(R.id.research_ablation_result);
        List<AttemptMeasurementSet> attempts;
        try {
            attempts = CourseProgressStore.loadMeasurementAttempts(this);
        } catch (IllegalStateException exception) {
            Log.e(TAG, "Channel ablation could not read saved lesson attempts.", exception);
            resultView.setText("Ablation is unavailable because attempt history could not be read.");
            return;
        }
        if (attempts.isEmpty()) {
            resultView.setText("No saved attempt is available for channel ablation.");
            return;
        }
        Set<MeasurementSource> selected = EnumSet.noneOf(MeasurementSource.class);
        if (((CheckBox) findViewById(R.id.ablation_audio)).isChecked()) selected.add(MeasurementSource.AUDIO);
        if (((CheckBox) findViewById(R.id.ablation_accelerometer)).isChecked()) selected.add(MeasurementSource.ACCELEROMETER);
        if (((CheckBox) findViewById(R.id.ablation_semg)).isChecked()) selected.add(MeasurementSource.SEMG);
        if (((CheckBox) findViewById(R.id.ablation_camera)).isChecked()) selected.add(MeasurementSource.CAMERA);
        AttemptMeasurementSet latest = attempts.get(0);
        AttemptMeasurementSet previous = previousForLesson(attempts, latest);
        if (previous == null) {
            resultView.setText("Comparison cannot be performed: there is no second saved attempt for the latest lesson. Individual measurements remain available in the dashboard.");
            return;
        }
        ChannelAblationResult ablation = new ChannelAblationEngine().compare(previous, latest, selected);
        StringBuilder text = new StringBuilder("Equivalent task ").append(latest.lessonId)
                .append(": ").append(previous.attemptId).append(" → ").append(latest.attemptId).append('\n');
        for (MeasurementSource source : MeasurementSource.values()) {
            List<WindowVariationResult> comparisons = ablation.comparisons.get(source);
            if (comparisons != null) {
                for (WindowVariationResult comparison : comparisons) {
                    text.append(source).append(" / ").append(comparison.type).append(": ");
                    if (comparison.status == MeasurementStatus.AVAILABLE) {
                        text.append(String.format(java.util.Locale.US, "%+.3f %s",
                                comparison.difference, comparison.unit));
                    } else {
                        text.append(comparison.status);
                    }
                    text.append(" (quality ").append(comparison.earlierQuality).append(" → ")
                            .append(comparison.laterQuality).append("; task ")
                            .append(comparison.taskId.isEmpty() ? "unavailable" : comparison.taskId)
                            .append("; configuration ")
                            .append(comparison.configurationId.isEmpty()
                                    ? "unavailable" : comparison.configurationId)
                            .append(") — ").append(comparison.explanation).append('\n');
                }
            }
            String exclusion = ablation.excluded.get(source);
            if (exclusion != null) text.append(source).append(": ").append(exclusion).append('\n');
        }
        text.append(ablation.limitation);
        resultView.setText(text.toString());
    }

    private void exportCsv() {
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("text/csv");
        create.putExtra(Intent.EXTRA_TITLE, "speechbridge_measurements.csv");
        startActivityForResult(create, EXPORT_REQUEST);
    }

    private void updateRecommendation() {
        List<AttemptMeasurementSet> attempts;
        try {
            attempts = CourseProgressStore.loadMeasurementAttempts(this);
        } catch (IllegalStateException exception) {
            Log.e(TAG, "Recommendation could not read saved lesson attempts.", exception);
            ((TextView) findViewById(R.id.research_recommendation_result))
                    .setText("No recommendation: saved attempt history is unavailable.");
            return;
        }
        if (attempts.isEmpty()) {
            ((TextView) findViewById(R.id.research_recommendation_result))
                    .setText("No recommendation: there is no saved attempt to review.");
            return;
        }
        int checkedId = ((RadioGroup) findViewById(R.id.research_user_report)).getCheckedRadioButtonId();
        RuleBasedRecommendationEngine.UserReport report;
        if (checkedId == R.id.research_report_comfortable) {
            report = RuleBasedRecommendationEngine.UserReport.COMFORTABLE;
        } else if (checkedId == R.id.research_report_difficult) {
            report = RuleBasedRecommendationEngine.UserReport.DIFFICULT;
        } else if (checkedId == R.id.research_report_discomfort) {
            report = RuleBasedRecommendationEngine.UserReport.DISCOMFORT;
        } else {
            report = RuleBasedRecommendationEngine.UserReport.UNKNOWN;
        }
        AttemptMeasurementSet latest = attempts.get(0);
        if (report != RuleBasedRecommendationEngine.UserReport.UNKNOWN) {
            CourseProgressStore.updateLessonResearchComfortReport(this, latest.attemptId, report.name());
            try {
                attempts = CourseProgressStore.loadMeasurementAttempts(this);
            } catch (IllegalStateException exception) {
                Log.e(TAG, "Saved attempt history could not be reloaded after recording comfort.", exception);
            }
        }
        PracticeRecommendation recommendation =
                new RuleBasedRecommendationEngine().recommendFromHistory(attempts, report);
        ((TextView) findViewById(R.id.research_recommendation_result))
                .setText(recommendation.action + ": " + recommendation.reason);
    }

    private void updateBaselines() {
        TextView result = findViewById(R.id.research_baseline_result);
        try {
            List<AttemptMeasurementSet> attempts = CourseProgressStore.loadMeasurementAttempts(this);
            String participantId = CalibrationProfileStore.getOrCreateParticipantId(this);
            List<String> outcomes = BaselineIntegrationService.integrate(this, attempts, participantId,
                    System.currentTimeMillis(), 90L * 24L * 60L * 60L * 1000L);
            StringBuilder text = new StringBuilder();
            for (String outcome : outcomes) text.append(outcome).append('\n');
            result.setText(text.toString());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            Log.e(TAG, "Personal baselines could not be integrated from saved attempts.", exception);
            result.setText("Baseline integration is unavailable because attempt or profile history could not be read or validated.");
        }
    }

    private MeasurementQuality qualityFor(AttemptMeasurementSet attempt) {
        boolean hasUsableMeasurement = false;
        boolean hasLimited = false;
        for (MeasurementRecord measurement : attempt.measurements) {
            if (measurement.status != MeasurementStatus.AVAILABLE) continue;
            if (measurement.quality == MeasurementQuality.POOR) return MeasurementQuality.POOR;
            if (measurement.quality == MeasurementQuality.GOOD
                    || measurement.quality == MeasurementQuality.EXCELLENT) hasUsableMeasurement = true;
            if (measurement.quality == MeasurementQuality.LIMITED) hasLimited = true;
        }
        if (hasUsableMeasurement) return MeasurementQuality.GOOD;
        if (hasLimited) return MeasurementQuality.LIMITED;
        return MeasurementQuality.UNKNOWN;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REQUEST || resultCode != RESULT_OK || data == null) return;
        Uri destination = data.getData();
        if (destination == null) {
            Toast.makeText(this, "No export destination was selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            List<AttemptMeasurementSet> attempts = CourseProgressStore.loadMeasurementAttempts(this);
            String participantId = CalibrationProfileStore.getOrCreateParticipantId(this);
            try (OutputStream stream = getContentResolver().openOutputStream(destination);
                 OutputStreamWriter writer = stream == null ? null
                         : new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                if (writer == null) throw new java.io.IOException("The selected document could not be opened.");
                writer.write(CsvResearchExporter.toCsv(participantId, attempts));
                writer.flush();
            }
            Toast.makeText(this, "Measurement CSV exported.", Toast.LENGTH_SHORT).show();
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            Log.e(TAG, "Could not export measurement CSV.", exception);
            Toast.makeText(this, "CSV export failed. Check the selected destination and try again.", Toast.LENGTH_LONG).show();
        }
    }

    private static String format(double value) {
        if (Double.isNaN(value)) return "N/A";
        return String.format(java.util.Locale.US, "%.1f", value);
    }
}
