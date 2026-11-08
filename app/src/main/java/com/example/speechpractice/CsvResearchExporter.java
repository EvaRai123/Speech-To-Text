package com.example.speechpractice;

import java.util.List;

public final class CsvResearchExporter {
    private static final String HEADER = "participant_id,lesson_id,attempt_id,attempt_status,completed_steps,activity_attempts,skipped_steps,started_at_wall_clock_ms,updated_at_wall_clock_ms,camera_measurement_status,source,source_configuration,type,value,unit,acquired_at_monotonic_ns,window_start_monotonic_ns,window_end_monotonic_ns,received_at_wall_clock_ms,quality,confidence,status,calibration_reference,limitations,fusion_configuration,contributing_channels,ended_at_wall_clock_ms,source_status,source_status_detail";

    private CsvResearchExporter() {
    }

    public static String toCsv(String participantId, List<AttemptMeasurementSet> attempts) {
        if (participantId == null || participantId.trim().isEmpty() || attempts == null) {
            throw new IllegalArgumentException("A local participant identifier and attempt list are required.");
        }
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (AttemptMeasurementSet attempt : attempts) {
            if (attempt == null) continue;
            for (MeasurementRecord measurement : attempt.measurements) {
                csv.append(attemptRow(attempt, measurement.source,
                        participantId,
                        attempt.lessonId,
                        attempt.attemptId,
                        attempt.status,
                        Integer.toString(attempt.completedSteps),
                        Integer.toString(attempt.activityAttempts),
                        Integer.toString(attempt.skippedSteps),
                        Long.toString(attempt.startedAtWallClockMs),
                        Long.toString(attempt.updatedAtWallClockMs),
                        attempt.cameraMeasurementStatus,
                        measurement.source.name(),
                        attempt.sourceConfigurations.get(measurement.source),
                        measurement.type.name(),
                        measurement.value == null ? "" : Double.toString(measurement.value),
                        measurement.unit,
                        number(measurement.acquiredAtMonotonicNanos),
                        number(measurement.windowStartMonotonicNanos),
                        number(measurement.windowEndMonotonicNanos),
                        number(measurement.receivedAtWallClockMs),
                        measurement.quality.name(),
                        measurement.confidence == null ? "" : Double.toString(measurement.confidence),
                        measurement.status.name(),
                        "",
                        measurement.detail,
                        "",
                        ""
                )).append("\r\n");
            }
            if (!hasSource(attempt, MeasurementSource.ACCELEROMETER)) {
                csv.append(attemptRow(attempt, MeasurementSource.ACCELEROMETER,
                participantId, attempt.lessonId, attempt.attemptId, attempt.status,
                Integer.toString(attempt.completedSteps), Integer.toString(attempt.activityAttempts),
                Integer.toString(attempt.skippedSteps),
                Long.toString(attempt.startedAtWallClockMs), Long.toString(attempt.updatedAtWallClockMs),
                attempt.cameraMeasurementStatus, MeasurementSource.ACCELEROMETER.name(),
                attempt.sourceConfigurations.get(MeasurementSource.ACCELEROMETER),
                MeasurementType.ACCELERATION_RMS.name(), "", "", "", "", "", "",
                        MeasurementQuality.UNKNOWN.name(), "", MeasurementStatus.UNAVAILABLE.name(),
                        "", sourceStatusDetail(attempt, MeasurementSource.ACCELEROMETER,
                                "No accelerometer measurement was persisted for this attempt."),
                        "", "")).append("\r\n");
            }
            if (!hasSource(attempt, MeasurementSource.AUDIO)) {
                csv.append(attemptRow(attempt, MeasurementSource.AUDIO,
                        participantId, attempt.lessonId, attempt.attemptId, attempt.status,
                        Integer.toString(attempt.completedSteps), Integer.toString(attempt.activityAttempts),
                        Integer.toString(attempt.skippedSteps),
                        Long.toString(attempt.startedAtWallClockMs), Long.toString(attempt.updatedAtWallClockMs),
                        attempt.cameraMeasurementStatus, MeasurementSource.AUDIO.name(),
                        attempt.sourceConfigurations.get(MeasurementSource.AUDIO), "", "", "", "", "", "", "",
                        MeasurementQuality.UNKNOWN.name(), "", "",
                        "", sourceStatusDetail(attempt, MeasurementSource.AUDIO,
                                "No audio measurement was persisted for this attempt."),
                        "", "")).append("\r\n");
            }
            if (!hasSource(attempt, MeasurementSource.SEMG)) {
                csv.append(attemptRow(attempt, MeasurementSource.SEMG,
                participantId, attempt.lessonId, attempt.attemptId, attempt.status,
                Integer.toString(attempt.completedSteps), Integer.toString(attempt.activityAttempts),
                Integer.toString(attempt.skippedSteps),
                Long.toString(attempt.startedAtWallClockMs), Long.toString(attempt.updatedAtWallClockMs),
                attempt.cameraMeasurementStatus, MeasurementSource.SEMG.name(),
                attempt.sourceConfigurations.get(MeasurementSource.SEMG),
                MeasurementType.SEMG_ENVELOPE_RMS.name(), "", "", "", "", "", "",
                        MeasurementQuality.UNKNOWN.name(), "", MeasurementStatus.UNAVAILABLE.name(),
                        "", sourceStatusDetail(attempt, MeasurementSource.SEMG,
                                "No sEMG measurement was persisted for this attempt."),
                        "", "")).append("\r\n");
            }
            if (!hasSource(attempt, MeasurementSource.CAMERA)
                    && (!attempt.cameraMeasurementStatus.isEmpty()
                    || attempt.sourceStatuses.containsKey(MeasurementSource.CAMERA))) {
                csv.append(attemptRow(attempt, MeasurementSource.CAMERA,
                        participantId, attempt.lessonId, attempt.attemptId, attempt.status,
                        Integer.toString(attempt.completedSteps), Integer.toString(attempt.activityAttempts),
                        Integer.toString(attempt.skippedSteps),
                        Long.toString(attempt.startedAtWallClockMs), Long.toString(attempt.updatedAtWallClockMs),
                        attempt.cameraMeasurementStatus, MeasurementSource.CAMERA.name(),
                        attempt.sourceConfigurations.get(MeasurementSource.CAMERA), "", "", "", "", "", "", "",
                        MeasurementQuality.UNKNOWN.name(), "", MeasurementStatus.UNAVAILABLE.name(),
                        "", sourceStatusDetail(attempt, MeasurementSource.CAMERA,
                                "Attempt-level camera status " + attempt.cameraMeasurementStatus
                                        + "; no camera measurement was persisted."),
                        "", "")).append("\r\n");
            }
        }
        return csv.toString();
    }

    private static boolean hasSource(AttemptMeasurementSet attempt, MeasurementSource source) {
        for (MeasurementRecord measurement : attempt.measurements) {
            if (measurement.source == source) return true;
        }
        return false;
    }

    private static String attemptRow(AttemptMeasurementSet attempt, MeasurementSource source,
                                     String... values) {
        StringBuilder result = new StringBuilder(row(values));
        result.append(',').append(attempt.endedAtWallClockMs > 0L
                ? Long.toString(attempt.endedAtWallClockMs) : "");
        AttemptSourceStatus sourceStatus = attempt.sourceStatuses.get(source);
        result.append(',').append(escape(sourceStatus == null ? "NOT_RECORDED" : sourceStatus.status));
        result.append(',').append(escape(sourceStatus == null ? "" : sourceStatus.detail));
        return result.toString();
    }

    private static String sourceStatusDetail(AttemptMeasurementSet attempt, MeasurementSource source,
                                             String fallback) {
        AttemptSourceStatus sourceStatus = attempt.sourceStatuses.get(source);
        return sourceStatus == null || sourceStatus.detail.isEmpty()
                ? fallback : sourceStatus.detail;
    }

    private static String number(Number value) {
        return value == null ? "" : value.toString();
    }

    private static String row(String... values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(',');
            result.append(escape(values[i] == null ? "" : values[i]));
        }
        return result.toString();
    }

    static String escape(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
