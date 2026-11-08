package com.example.speechpractice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class AttemptMeasurementSet {
    public final String attemptId;
    public final String lessonId;
    public final String status;
    public final long startedAtWallClockMs;
    public final long updatedAtWallClockMs;
    public final long endedAtWallClockMs;
    public final String cameraMeasurementStatus;
    public final String selfAssessment;
    public final String researchComfortReport;
    public final int completedSteps;
    public final int activityAttempts;
    public final int skippedSteps;
    public final Map<MeasurementSource, String> sourceConfigurations;
    public final Map<MeasurementSource, AttemptSourceStatus> sourceStatuses;
    public final List<MeasurementRecord> measurements;

    public AttemptMeasurementSet(String attemptId, String lessonId, String status,
                                 long startedAtWallClockMs, long updatedAtWallClockMs,
                                 List<MeasurementRecord> measurements) {
        this(attemptId, lessonId, status, startedAtWallClockMs, updatedAtWallClockMs, "", measurements);
    }

    public AttemptMeasurementSet(String attemptId, String lessonId, String status,
                                 long startedAtWallClockMs, long updatedAtWallClockMs,
                                 String cameraMeasurementStatus, List<MeasurementRecord> measurements) {
        this(attemptId, lessonId, status, startedAtWallClockMs, updatedAtWallClockMs,
                cameraMeasurementStatus, "", "", 0, 0, 0, Collections.emptyMap(), measurements);
    }

    public AttemptMeasurementSet(String attemptId, String lessonId, String status,
                                 long startedAtWallClockMs, long updatedAtWallClockMs,
                                 String cameraMeasurementStatus, String selfAssessment,
                                 String researchComfortReport,
                                 int completedSteps, int activityAttempts, int skippedSteps,
                                 Map<MeasurementSource, String> sourceConfigurations,
                                 List<MeasurementRecord> measurements) {
        this(attemptId, lessonId, status, startedAtWallClockMs, updatedAtWallClockMs,
                0L, cameraMeasurementStatus, selfAssessment, researchComfortReport,
                completedSteps, activityAttempts, skippedSteps, sourceConfigurations,
                Collections.emptyMap(), measurements);
    }

    public AttemptMeasurementSet(String attemptId, String lessonId, String status,
                                 long startedAtWallClockMs, long updatedAtWallClockMs,
                                 long endedAtWallClockMs,
                                 String cameraMeasurementStatus, String selfAssessment,
                                 String researchComfortReport,
                                 int completedSteps, int activityAttempts, int skippedSteps,
                                 Map<MeasurementSource, String> sourceConfigurations,
                                 Map<MeasurementSource, AttemptSourceStatus> sourceStatuses,
                                 List<MeasurementRecord> measurements) {
        this.attemptId = attemptId == null ? "" : attemptId;
        this.lessonId = lessonId == null ? "" : lessonId;
        this.status = status == null ? "" : status;
        this.startedAtWallClockMs = startedAtWallClockMs;
        this.updatedAtWallClockMs = updatedAtWallClockMs;
        this.endedAtWallClockMs = Math.max(0L, endedAtWallClockMs);
        this.cameraMeasurementStatus = cameraMeasurementStatus == null ? "" : cameraMeasurementStatus;
        this.selfAssessment = selfAssessment == null ? "" : selfAssessment;
        this.researchComfortReport = researchComfortReport == null ? "" : researchComfortReport;
        this.completedSteps = Math.max(0, completedSteps);
        this.activityAttempts = Math.max(0, activityAttempts);
        this.skippedSteps = Math.max(0, skippedSteps);
        Map<MeasurementSource, String> configurations = new EnumMap<>(MeasurementSource.class);
        if (sourceConfigurations != null) {
            for (Map.Entry<MeasurementSource, String> entry : sourceConfigurations.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                    configurations.put(entry.getKey(), entry.getValue());
                }
            }
        }
        this.sourceConfigurations = Collections.unmodifiableMap(configurations);
        Map<MeasurementSource, AttemptSourceStatus> statuses =
                new EnumMap<>(MeasurementSource.class);
        if (sourceStatuses != null) {
            for (Map.Entry<MeasurementSource, AttemptSourceStatus> entry : sourceStatuses.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    statuses.put(entry.getKey(), entry.getValue());
                }
            }
        }
        this.sourceStatuses = Collections.unmodifiableMap(statuses);
        this.measurements = measurements == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(measurements));
    }
}
