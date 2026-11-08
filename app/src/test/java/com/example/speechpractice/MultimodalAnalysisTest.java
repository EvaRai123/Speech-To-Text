package com.example.speechpractice;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MultimodalAnalysisTest {
    @Test
    public void fusionRequiresSynchronizedWindowsAndNeverCreatesAnUnsupportedScore() {
        AttemptMeasurementSet synced = attempt("a", "mouth_2", Arrays.asList(
                window(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY, 180.0, 1_000_000_000L, 2_000_000_000L),
                window(MeasurementSource.CAMERA, MeasurementType.LIP_CLOSURE_RATIO, 0.4, 1_050_000_000L, 2_050_000_000L),
                window(MeasurementSource.ACCELEROMETER, MeasurementType.ACCELERATION_RMS, 0.2, 1_010_000_000L, 2_010_000_000L),
                window(MeasurementSource.SEMG, MeasurementType.SEMG_ENVELOPE_RMS, 3.0, 1_020_000_000L, 2_020_000_000L)));
        FusionResult result = new MultimodalFusionEngine().assess(synced, 100_000_000L);
        assertEquals(MeasurementStatus.UNAVAILABLE, result.status);
        assertEquals(4, result.eligibleSources.size());
        assertTrue(result.contributingSources.isEmpty());
        assertEquals(4, result.channelMeasurements.size());
        assertTrue(result.explanation.contains("no validated mapping"));
    }

    @Test
    public void fusionRejectsMissingAcquisitionWindows() {
        MeasurementRecord point = MeasurementRecord.available(MeasurementSource.AUDIO,
                MeasurementType.FUNDAMENTAL_FREQUENCY, 180.0, "Hz", null, 50L,
                MeasurementQuality.GOOD, 0.8, "Estimated pitch.");
        AttemptMeasurementSet attempt = attempt("b", "phonation_1", Arrays.asList(point,
                window(MeasurementSource.CAMERA, MeasurementType.LIP_CLOSURE_RATIO, 0.5,
                        1_000L, 2_000L)));

        FusionResult result = new MultimodalFusionEngine().assess(attempt, 100L);
        assertEquals(MeasurementStatus.UNAVAILABLE, result.status);
        assertTrue(result.exclusions.toString().contains("timestamped acquisition window is unavailable"));
    }

    @Test
    public void ablationRecordsSelectedAndExcludedChannelsWithoutAggregatingThem() {
        AttemptMeasurementSet attempt = attempt("c", "lesson_1", Arrays.asList(
                window(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY, 180.0, 1_000L, 2_000L),
                window(MeasurementSource.CAMERA, MeasurementType.LIP_CLOSURE_RATIO, 0.3, 1_000L, 2_000L)));
        ChannelAblationResult result = new ChannelAblationEngine().compare(attempt,
                EnumSet.of(MeasurementSource.AUDIO, MeasurementSource.ACCELEROMETER));
        assertEquals(1, result.included.get(MeasurementSource.AUDIO).size());
        assertTrue(result.excluded.get(MeasurementSource.ACCELEROMETER).contains("No measurements"));
        assertTrue(result.excluded.get(MeasurementSource.CAMERA).contains("Excluded by"));
        assertTrue(result.limitation.contains("combined score"));
    }

    @Test
    public void crossAttemptAblationRequiresEquivalentTaskWindowAndConfiguration() {
        Map<MeasurementSource, String> configs = new EnumMap<>(MeasurementSource.class);
        configs.put(MeasurementSource.ACCELEROMETER, "observed-placement-a");
        AttemptMeasurementSet earlier = attemptWithConfigs("old", "same-task",
                Arrays.asList(window(MeasurementSource.ACCELEROMETER,
                        MeasurementType.ACCELERATION_RMS, 0.2, 1_000L, 2_000L)), configs);
        AttemptMeasurementSet later = attemptWithConfigs("new", "same-task",
                Arrays.asList(window(MeasurementSource.ACCELEROMETER,
                        MeasurementType.ACCELERATION_RMS, 0.4, 3_000L, 4_000L)), configs);

        ChannelAblationResult result = new ChannelAblationEngine().compare(earlier, later,
                EnumSet.of(MeasurementSource.ACCELEROMETER));
        assertEquals(MeasurementStatus.AVAILABLE,
                result.comparisons.get(MeasurementSource.ACCELEROMETER).get(0).status);
        assertEquals(0.2, result.comparisons.get(MeasurementSource.ACCELEROMETER)
                .get(0).difference, 0.0001);
        assertEquals("same-task", result.comparisons.get(MeasurementSource.ACCELEROMETER)
                .get(0).taskId);
        assertEquals("observed-placement-a",
                result.comparisons.get(MeasurementSource.ACCELEROMETER).get(0).configurationId);
        assertEquals(MeasurementQuality.GOOD,
                result.comparisons.get(MeasurementSource.ACCELEROMETER).get(0).earlierQuality);

        AttemptMeasurementSet withoutConfig = attempt("unconfigured", "same-task",
                Arrays.asList(window(MeasurementSource.ACCELEROMETER,
                        MeasurementType.ACCELERATION_RMS, 0.4, 3_000L, 4_000L)));
        ChannelAblationResult blocked = new ChannelAblationEngine().compare(earlier,
                withoutConfig, EnumSet.of(MeasurementSource.ACCELEROMETER));
        assertTrue(blocked.excluded.get(MeasurementSource.ACCELEROMETER).contains("configuration"));
    }

    @Test
    public void attemptComparisonsRequireSameLessonAndDoNotInterpretChange() {
        MeasurementRecord first = window(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                180.0, 1_000L, 2_000L);
        MeasurementRecord second = window(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                190.0, 3_000L, 4_000L);
        List<AttemptComparison> comparisons = new AttemptComparisonEngine().compare(
                attempt("1", "same", Arrays.asList(first)), attempt("2", "same", Arrays.asList(second)));
        assertEquals(1, comparisons.size());
        assertEquals(10.0, comparisons.get(0).difference, 0.001);
        assertTrue(comparisons.get(0).explanation.contains("not established"));
    }

    @Test
    public void windowVariationRequiresMatchingTaskConfigurationAndOrderedWindows() {
        MeasurementRecord earlier = window(MeasurementSource.ACCELEROMETER,
                MeasurementType.ACCELERATION_RMS, 0.2, 1_000L, 2_000L);
        MeasurementRecord later = window(MeasurementSource.ACCELEROMETER,
                MeasurementType.ACCELERATION_RMS, 0.4, 3_000L, 4_000L);
        SignalWindowComparator comparator = new SignalWindowComparator();
        WindowVariationResult result = comparator.compare(earlier, later, "vowel", "vowel",
                "neck-placement-a", "neck-placement-a");
        assertEquals(MeasurementStatus.AVAILABLE, result.status);
        assertEquals(0.2, result.difference, 0.0001);
        assertEquals(MeasurementStatus.UNAVAILABLE, comparator.compare(earlier, later, "vowel",
                "vowel", "neck-placement-a", "neck-placement-b").status);
    }

    @Test(expected = IllegalArgumentException.class)
    public void attemptComparisonRejectsDifferentLessons() {
        new AttemptComparisonEngine().compare(attempt("1", "lesson_1", new ArrayList<>()),
                attempt("2", "lesson_2", new ArrayList<>()));
    }

    private AttemptMeasurementSet attempt(String id, String lesson, List<MeasurementRecord> records) {
        return new AttemptMeasurementSet(id, lesson, "completed", 1L, 2L, records);
    }

    private AttemptMeasurementSet attemptWithConfigs(String id, String lesson,
                                                     List<MeasurementRecord> records,
                                                     Map<MeasurementSource, String> configs) {
        return new AttemptMeasurementSet(id, lesson, "completed", 1L, 2L,
                "", "", "", 1, 1, 0, configs, records);
    }

    private MeasurementRecord window(MeasurementSource source, MeasurementType type, double value,
                                     long start, long end) {
        return MeasurementRecord.availableWindow(source, type, value, type.canonicalUnit,
                start, end, 100L, MeasurementQuality.GOOD, 0.8,
                "Test fixture only; no live sensor data.");
    }
}
