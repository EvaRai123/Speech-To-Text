package com.example.speechpractice;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.EnumMap;

import static org.junit.Assert.assertTrue;

public class CsvResearchExporterTest {
    @Test
    public void exportEscapesCsvAndKeepsUnavailableDistinctFromZero() {
        MeasurementRecord measured = MeasurementRecord.available(MeasurementSource.AUDIO,
                MeasurementType.RELATIVE_INTENSITY, -20.0, "dBFS", null, 400L,
                MeasurementQuality.LIMITED, null, "Relative, \"un-calibrated\"\nlevel");
        MeasurementRecord zero = MeasurementRecord.available(MeasurementSource.CAMERA,
                MeasurementType.LIP_CLOSURE_RATIO, 0.0, "ratio", null, 401L,
                MeasurementQuality.LIMITED, null, "Genuine zero fixture.");
        MeasurementRecord unavailable = MeasurementRecord.unavailable(MeasurementSource.SEMG,
                MeasurementType.SEMG_ENVELOPE_RMS, MeasurementStatus.UNAVAILABLE,
                "No front end, no live sample");
        MeasurementRecord missingCamera = MeasurementRecord.unavailable(MeasurementSource.CAMERA,
                MeasurementType.MOUTH_OPENING_RATIO, MeasurementStatus.UNAVAILABLE,
                "Camera attempt status only.");
        Map<MeasurementSource, String> configs = new EnumMap<>(MeasurementSource.class);
        configs.put(MeasurementSource.AUDIO, "observed-device-fixture");
        AttemptMeasurementSet attempt = new AttemptMeasurementSet("attempt-1", "lesson_1",
                "completed", 100L, 500L, "NOT_COLLECTED", "", "", 4, 5, 1, configs,
                Arrays.asList(measured, zero, unavailable, missingCamera));
        AttemptMeasurementSet noAudio = new AttemptMeasurementSet("attempt-2", "lesson_2",
                "incomplete", 200L, 300L, Collections.emptyList());

        String csv = CsvResearchExporter.toCsv("local-id", Arrays.asList(attempt, noAudio));

        assertTrue(csv.startsWith("participant_id,lesson_id,attempt_id"));
        assertTrue(csv.contains("camera_measurement_status"));
        assertTrue(csv.contains("NOT_COLLECTED"));
        assertTrue(csv.contains("\"Relative, \"\"un-calibrated\"\"\nlevel\""));
        assertTrue(csv.contains("AUDIO,observed-device-fixture,RELATIVE_INTENSITY,-20.0,dBFS"));
        assertTrue(csv.contains(",0.0,ratio,"));
        assertTrue(csv.contains("SEMG,,SEMG_ENVELOPE_RMS,,"));
        assertTrue(csv.contains(",UNKNOWN,,UNAVAILABLE,"));
        assertTrue(csv.contains("ACCELEROMETER,,ACCELERATION_RMS,,"));
        assertTrue(csv.contains("NOT_COLLECTED,CAMERA,,MOUTH_OPENING_RATIO,,"));
        assertTrue(csv.contains("completed_steps,activity_attempts,skipped_steps"));
        assertTrue(csv.contains("ended_at_wall_clock_ms,source_status,source_status_detail"));
        assertTrue(csv.contains("No audio measurement was persisted for this attempt."));
    }

    @Test
    public void exportsAdditiveAttemptEndAndObservedSourceStatus() {
        Map<MeasurementSource, AttemptSourceStatus> statuses =
                new EnumMap<>(MeasurementSource.class);
        statuses.put(MeasurementSource.AUDIO, new AttemptSourceStatus(
                "ROUTE_UNVERIFIED", "External route, \"not verified\"\n"));
        AttemptMeasurementSet attempt = new AttemptMeasurementSet("attempt-3", "lesson_3",
                "interrupted", 10L, 20L, 30L, "", "", "", 0, 0, 0,
                Collections.emptyMap(), statuses, Collections.emptyList());

        String csv = CsvResearchExporter.toCsv("local-id", Collections.singletonList(attempt));

        assertTrue(csv.contains(",30,ROUTE_UNVERIFIED,\"External route, \"\"not verified\"\"\n\""));
    }
}
