package com.example.speechpractice;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CalibrationAndRecommendationTest {
    @Test
    public void baselineIsTaskAndConfigurationSpecificAndTracksUpdates() {
        MeasurementRecord initial = available(0.2);
        PersonalBaseline baseline = PersonalBaseline.create("local-test-id",
                MeasurementSource.ACCELEROMETER, "sustained-vowel", "placement-config-1",
                Arrays.asList(initial), 1000L, 10_000L);
        PersonalBaseline updated = baseline.update(Arrays.asList(available(0.4)),
                "local-test-id", MeasurementSource.ACCELEROMETER, "sustained-vowel",
                "placement-config-1", 2000L);

        BaselineComparison comparison = updated.compare(available(0.5), "local-test-id",
                "sustained-vowel", "placement-config-1", 3000L);
        assertEquals(MeasurementStatus.AVAILABLE, comparison.status);
        assertEquals(0.2, comparison.difference, 0.0001);
        assertEquals(2, updated.updateHistory.size());
        assertEquals(MeasurementStatus.UNAVAILABLE,
                updated.compare(available(0.5), "local-test-id", "other-task",
                        "placement-config-1", 3000L).status);
        assertEquals(MeasurementStatus.UNAVAILABLE,
                updated.compare(available(0.5), "local-test-id", "sustained-vowel",
                        "different-placement", 3000L).status);
        assertEquals(MeasurementStatus.UNAVAILABLE,
                updated.compare(available(0.5), "local-test-id", "sustained-vowel",
                        "placement-config-1", 10_000L).status);
    }

    @Test(expected = IllegalArgumentException.class)
    public void baselineRejectsUnavailableOnlyInput() {
        PersonalBaseline.create("id", MeasurementSource.ACCELEROMETER, "task", "config",
                Arrays.asList(MeasurementRecord.unavailable(MeasurementSource.ACCELEROMETER,
                        MeasurementType.ACCELERATION_RMS, MeasurementStatus.UNAVAILABLE,
                        "No hardware data.")), 1L, 100L);
    }

    @Test
    public void savedAttemptsCreateTraceableBaselineOnlyForRepeatedValidMatchingConfiguration() {
        Map<MeasurementSource, String> config = configurations("observed-config-1");
        AttemptMeasurementSet first = attempt("first", "lesson_x", "completed", config,
                available(0.2));
        AttemptMeasurementSet second = attempt("second", "lesson_x", "completed", config,
                available(0.4));
        AttemptMeasurementSet poor = attempt("poor", "lesson_x", "completed", config,
                MeasurementRecord.availableWindow(MeasurementSource.ACCELEROMETER,
                        MeasurementType.ACCELERATION_RMS, 0.9, "m/s^2", 1L, 2L, 3L,
                        MeasurementQuality.POOR, 0.2, "Fixture only."));
        PersonalBaseline baseline = PersonalBaseline.createFromAttempts("participant",
                MeasurementSource.ACCELEROMETER, "lesson_x", "observed-config-1",
                Arrays.asList(first, second, poor), 100L, 10_000L);
        assertEquals(0.3, baseline.values.get(MeasurementType.ACCELERATION_RMS), 0.0001);
        assertEquals(Integer.valueOf(2), baseline.sampleCounts.get(MeasurementType.ACCELERATION_RMS));
        assertEquals("first", baseline.updateHistory.get(0).attemptId);
        assertEquals("second", baseline.updateHistory.get(1).attemptId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void baselineRejectsMissingOrMismatchedAttemptConfiguration() {
        AttemptMeasurementSet first = attempt("first", "lesson_x", "completed",
                configurations("observed-config-1"), available(0.2));
        AttemptMeasurementSet second = attempt("second", "lesson_x", "completed",
                configurations("observed-config-2"), available(0.4));
        PersonalBaseline.createFromAttempts("participant", MeasurementSource.ACCELEROMETER,
                "lesson_x", "observed-config-1", Arrays.asList(first, second), 100L, 10_000L);
    }

    @Test
    public void repeatedBaselineIntegrationDoesNotDoubleCountAttemptHistory() {
        Map<MeasurementSource, String> config = configurations("observed-config-1");
        List<AttemptMeasurementSet> attempts = Arrays.asList(
                attempt("first", "lesson_x", "completed", config, available(0.2)),
                attempt("second", "lesson_x", "completed", config, available(0.4)));
        PersonalBaseline baseline = PersonalBaseline.createFromAttempts("participant",
                MeasurementSource.ACCELEROMETER, "lesson_x", "observed-config-1",
                attempts, 100L, 10_000L);
        try {
            baseline.updateFromAttempts(attempts, "participant", MeasurementSource.ACCELEROMETER,
                    "lesson_x", "observed-config-1", 200L);
            throw new AssertionError("Repeated attempts should not be counted twice.");
        } catch (IllegalArgumentException expected) {
            assertEquals(2, baseline.updateHistory.size());
            assertEquals(Integer.valueOf(2), baseline.sampleCounts.get(MeasurementType.ACCELERATION_RMS));
        }
    }

    @Test
    public void recommendationsAreTransparentAndDoNotUseMissingSignalAsPerformance() {
        RuleBasedRecommendationEngine engine = new RuleBasedRecommendationEngine();
        PracticeRecommendation rest = engine.recommend(false,
                RuleBasedRecommendationEngine.UserReport.DISCOMFORT,
                MeasurementQuality.GOOD, true);
        assertEquals(PracticeRecommendation.Action.REST, rest.action);
        assertNotNull(rest.reason);

        PracticeRecommendation retry = engine.recommend(false,
                RuleBasedRecommendationEngine.UserReport.COMFORTABLE,
                MeasurementQuality.POOR, false);
        assertEquals(PracticeRecommendation.Action.REPEAT_AFTER_SIGNAL_ISSUE, retry.action);

        PracticeRecommendation noAdvice = engine.recommend(true,
                RuleBasedRecommendationEngine.UserReport.UNKNOWN,
                MeasurementQuality.UNKNOWN, false);
        assertEquals(PracticeRecommendation.Action.NO_RECOMMENDATION, noAdvice.action);

        PracticeRecommendation difficulty = engine.recommend(false,
                RuleBasedRecommendationEngine.UserReport.DIFFICULT,
                MeasurementQuality.UNKNOWN, true);
        assertEquals(PracticeRecommendation.Action.REVIEW_WITH_SUPPORT, difficulty.action);
        assertTrue(difficulty.reason.contains("does not establish an easier/harder"));
    }

    @Test
    public void recommendationUsesSavedHistoryAndReportsInsufficientHistory() {
        RuleBasedRecommendationEngine engine = new RuleBasedRecommendationEngine();
        AttemptMeasurementSet one = attempt("one", "lesson_x", "completed",
                Collections.emptyMap(), Collections.emptyList());
        assertEquals(PracticeRecommendation.Action.NO_RECOMMENDATION,
                engine.recommendFromHistory(Collections.singletonList(one),
                        RuleBasedRecommendationEngine.UserReport.COMFORTABLE).action);

        AttemptMeasurementSet newest = attempt("two", "lesson_x", "completed",
                Collections.emptyMap(), Collections.singletonList(MeasurementRecord.unavailable(
                        MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                        MeasurementStatus.UNAVAILABLE, "No input was enabled.")));
        assertEquals(PracticeRecommendation.Action.NO_RECOMMENDATION,
                engine.recommendFromHistory(Arrays.asList(newest, one),
                        RuleBasedRecommendationEngine.UserReport.UNKNOWN).action);
        assertTrue(engine.recommendFromHistory(Arrays.asList(newest, one),
                RuleBasedRecommendationEngine.UserReport.UNKNOWN).reason.contains("unavailable"));
    }

    private MeasurementRecord available(double value) {
        return MeasurementRecord.availableWindow(MeasurementSource.ACCELEROMETER,
                MeasurementType.ACCELERATION_RMS, value, "m/s^2", 1L, 2L, 3L,
                MeasurementQuality.GOOD, 0.9, "Synthetic unit-test fixture only.");
    }

    private AttemptMeasurementSet attempt(String id, String lesson, String status,
                                          Map<MeasurementSource, String> configurations,
                                          MeasurementRecord measurement) {
        return attempt(id, lesson, status, configurations, Collections.singletonList(measurement));
    }

    private AttemptMeasurementSet attempt(String id, String lesson, String status,
                                          Map<MeasurementSource, String> configurations,
                                          List<MeasurementRecord> measurements) {
        return new AttemptMeasurementSet(id, lesson, status, 1L, 2L, "", "", "",
                1, 1, 0, configurations, measurements);
    }

    private Map<MeasurementSource, String> configurations(String configuration) {
        Map<MeasurementSource, String> result = new EnumMap<>(MeasurementSource.class);
        result.put(MeasurementSource.ACCELEROMETER, configuration);
        return result;
    }
}
