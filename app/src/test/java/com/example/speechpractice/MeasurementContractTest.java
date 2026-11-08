package com.example.speechpractice;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class MeasurementContractTest {
    @Test
    public void unavailableMeasurementsHaveNoNumericValue() {
        MeasurementRecord measurement = MeasurementRecord.unavailable(
                MeasurementSource.AUDIO, MeasurementType.SPEECH_ACTIVITY,
                MeasurementStatus.UNAVAILABLE, "No usable recording.");

        assertEquals(MeasurementStatus.UNAVAILABLE, measurement.status);
        assertNull(measurement.value);
        assertNull(measurement.unit);
        assertNull(measurement.acquiredAtMonotonicNanos);
        assertNull(measurement.receivedAtWallClockMs);
        try {
            MeasurementRecord.unavailable(MeasurementSource.AUDIO, MeasurementType.SPEECH_ACTIVITY,
                    MeasurementStatus.AVAILABLE, "Invalid available status.");
            fail("An available measurement must include a numeric value and unit.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void availableMeasurementRejectsNonFiniteValues() {
        try {
            MeasurementRecord.available(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                    Double.NaN, "Hz", null, 1L, MeasurementQuality.GOOD, 0.9, "");
            fail("Non-finite measurement values must be rejected.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void measurementSourceTypeAndCanonicalUnitMustMatch() {
        assertAvailable(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                120.0, "Hz");
        assertAvailable(MeasurementSource.ACCELEROMETER, MeasurementType.MECHANICAL_VIBRATION,
                0.25, "m/s^2");
        assertAvailable(MeasurementSource.SEMG, MeasurementType.SURFACE_MUSCLE_ACTIVITY,
                14.0, "uV");
        assertAvailable(MeasurementSource.CAMERA, MeasurementType.LIP_CLOSURE,
                0.8, "ratio");
        assertAvailable(MeasurementSource.CAMERA, MeasurementType.LIP_CLOSURE_RATIO,
                0.8, "ratio");
        assertAvailable(MeasurementSource.CAMERA, MeasurementType.LOWER_FACE_DISPLACEMENT_PROXY,
                0.2, "ratio");

        assertInvalid(MeasurementSource.AUDIO, MeasurementType.LIP_CLOSURE, 0.8, "ratio");
        assertInvalid(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY, 120.0, "kHz");
        assertInvalid(MeasurementSource.SEMG, MeasurementType.SURFACE_MUSCLE_ACTIVITY, 14.0, "mV");
        assertInvalid(MeasurementSource.ACCELEROMETER, MeasurementType.MECHANICAL_VIBRATION, 0.25, "g");
        assertInvalid(MeasurementSource.CAMERA, MeasurementType.LIP_CLOSURE, 1.2, "ratio");
        assertInvalid(MeasurementSource.CAMERA, MeasurementType.LIP_ROUNDING_PROXY, 1.2, "ratio");
        assertInvalid(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY, 0.0, "Hz");
        assertInvalid(MeasurementSource.CAMERA, MeasurementType.LIP_JAW_TIMING, -1.0, "ms");
        assertAvailable(MeasurementSource.AUDIO, MeasurementType.VOICED_DURATION, 0.0, "ms");
    }

    @Test
    public void unavailableAndAvailableZeroReadingsRemainDistinct() {
        MeasurementRecord zeroDuration = MeasurementRecord.available(
                MeasurementSource.AUDIO, MeasurementType.SPEECH_ACTIVITY, 0.0, "ms",
                null, 1234L, MeasurementQuality.GOOD, null, "A valid measured zero.");
        MeasurementRecord unavailable = MeasurementRecord.unavailable(
                MeasurementSource.AUDIO, MeasurementType.SPEECH_ACTIVITY,
                MeasurementStatus.POOR_SIGNAL, "No reliable estimate.");

        assertEquals(MeasurementStatus.AVAILABLE, zeroDuration.status);
        assertEquals(Double.valueOf(0.0), zeroDuration.value);
        assertNull(unavailable.value);
        assertNull(unavailable.unit);
    }

    @Test
    public void acquisitionTimestampMayBeMissingWithoutReplacingItWithReceiptTime() {
        MeasurementRecord measurement = MeasurementRecord.available(
                MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                120.0, "Hz", null, 987654L, MeasurementQuality.GOOD, 0.9, "");

        assertNull(measurement.acquiredAtMonotonicNanos);
        assertEquals(Long.valueOf(987654L), measurement.receivedAtWallClockMs);
    }

    @Test
    public void unavailableRecordsAlsoRejectMismatchedSources() {
        try {
            MeasurementRecord.unavailable(MeasurementSource.CAMERA, MeasurementType.FUNDAMENTAL_FREQUENCY,
                    MeasurementStatus.UNAVAILABLE, "Invalid source/type pair.");
            fail("Invalid source/type pairs must not enter measurement history.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void requiredMeasurementRejectsMissingUnavailableMismatchedAndPoorQualityRecords() {
        LessonMeasurementRequirements requirements = LessonMeasurementRequirements.builder()
                .set(MeasurementSource.AUDIO, LessonMeasurementRequirements.Need.REQUIRED,
                        MeasurementQuality.GOOD, "A clear audio estimate is needed.",
                        MeasurementType.FUNDAMENTAL_FREQUENCY)
                .build();

        assertNotNull(requirements.getRequiredMeasurementBlocker(Collections.emptyList()));
        assertNotNull(requirements.getRequiredMeasurementBlocker(Collections.singletonList(
                MeasurementRecord.unavailable(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                        MeasurementStatus.POOR_SIGNAL, "Signal is too noisy."))));
        assertNotNull(requirements.getRequiredMeasurementBlocker(Collections.singletonList(
                available(MeasurementSource.AUDIO, MeasurementType.SPEECH_ACTIVITY,
                        MeasurementQuality.EXCELLENT))));
        assertNotNull(requirements.getRequiredMeasurementBlocker(Collections.singletonList(
                available(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                        MeasurementQuality.LIMITED))));
        assertNull(requirements.getRequiredMeasurementBlocker(Collections.singletonList(
                available(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                        MeasurementQuality.GOOD))));
    }

    @Test
    public void aGoodDuplicateRecordCanSatisfyARequirementAfterAnUnavailableAttempt() {
        LessonMeasurementRequirements requirements = LessonMeasurementRequirements.builder()
                .set(MeasurementSource.AUDIO, LessonMeasurementRequirements.Need.REQUIRED,
                        MeasurementQuality.GOOD, "A clear audio estimate is needed.",
                        MeasurementType.FUNDAMENTAL_FREQUENCY)
                .build();
        assertNull(requirements.getRequiredMeasurementBlocker(Arrays.asList(
                MeasurementRecord.unavailable(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                        MeasurementStatus.POOR_SIGNAL, "The first attempt was too noisy."),
                available(MeasurementSource.AUDIO, MeasurementType.FUNDAMENTAL_FREQUENCY,
                        MeasurementQuality.GOOD))));
    }

    @Test
    public void optionalAndNotApplicableMeasurementsNeverBlockCompletion() {
        LessonMeasurementRequirements requirements = LessonMeasurementRequirements.builder()
                .set(MeasurementSource.AUDIO, LessonMeasurementRequirements.Need.OPTIONAL,
                        MeasurementQuality.LIMITED, "Optional audio.", MeasurementType.SPEECH_ACTIVITY)
                .build();
        assertNull(requirements.getRequiredMeasurementBlocker(Collections.emptyList()));
        assertNull(requirements.getRequiredMeasurementBlocker(Arrays.asList(
                MeasurementRecord.unavailable(MeasurementSource.AUDIO, MeasurementType.SPEECH_ACTIVITY,
                        MeasurementStatus.UNAVAILABLE, "Audio was skipped."))));
        assertEquals(LessonMeasurementRequirements.Need.NOT_APPLICABLE,
                requirements.forSource(MeasurementSource.CAMERA).need);
    }

    @Test
    public void cameraRequirementsMatchPAndBMAndMVisibleMovements() {
        LessonMeasurementRequirements pAndB = LessonMeasurementRequirements.forLesson(
                "mouth_articulation", "mouth_11", LessonDefinition.ACTIVITY_PHRASE, false);
        LessonMeasurementRequirements m = LessonMeasurementRequirements.forLesson(
                "mouth_articulation", "mouth_12", LessonDefinition.ACTIVITY_PHRASE, false);
        LessonMeasurementRequirements lipTeeth = LessonMeasurementRequirements.forLesson(
                "mouth_articulation", "mouth_13", LessonDefinition.ACTIVITY_PHRASE, false);

        assertEquals(java.util.EnumSet.of(MeasurementType.LIP_CLOSURE_RATIO,
                        MeasurementType.LIP_SEPARATION_RATIO),
                pAndB.forSource(MeasurementSource.CAMERA).relevantTypes);
        assertEquals(java.util.EnumSet.of(MeasurementType.LIP_CLOSURE_RATIO),
                m.forSource(MeasurementSource.CAMERA).relevantTypes);
        assertEquals(LessonMeasurementRequirements.Need.OPTIONAL,
                lipTeeth.forSource(MeasurementSource.CAMERA).need);
        assertEquals(java.util.EnumSet.of(MeasurementType.VISIBLE_LIP_TEETH_POSITION),
                lipTeeth.forSource(MeasurementSource.CAMERA).relevantTypes);
    }

    private static MeasurementRecord available(MeasurementSource source, MeasurementType type,
                                               MeasurementQuality quality) {
        return MeasurementRecord.available(source, type, 1.0, type.canonicalUnit, null, 1L,
                quality, 0.9, "");
    }

    private static void assertAvailable(MeasurementSource source, MeasurementType type,
                                        double value, String unit) {
        MeasurementRecord record = MeasurementRecord.available(source, type, value, unit, null,
                1L, MeasurementQuality.GOOD, null, "");
        assertEquals(source, record.source);
        assertEquals(type, record.type);
        assertEquals(unit, record.unit);
    }

    private static void assertInvalid(MeasurementSource source, MeasurementType type,
                                      double value, String unit) {
        try {
            MeasurementRecord.available(source, type, value, unit, null, 1L,
                    MeasurementQuality.GOOD, null, "");
            fail("Invalid measurement combinations must be rejected.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
