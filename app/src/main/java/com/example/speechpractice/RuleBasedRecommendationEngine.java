package com.example.speechpractice;

import java.util.ArrayList;
import java.util.List;

public final class RuleBasedRecommendationEngine {
    public enum UserReport {
        COMFORTABLE,
        DIFFICULT,
        DISCOMFORT,
        UNKNOWN
    }

    public PracticeRecommendation recommend(boolean participationComplete, UserReport userReport,
                                            MeasurementQuality signalQuality,
                                            boolean easierLessonPreviouslyCompleted) {
        if (userReport == UserReport.DISCOMFORT) {
            return new PracticeRecommendation(PracticeRecommendation.Action.REST,
                    "You reported discomfort. Rest or stop; this application does not prescribe treatment.");
        }
        if (signalQuality == MeasurementQuality.POOR) {
            return new PracticeRecommendation(PracticeRecommendation.Action.REPEAT_AFTER_SIGNAL_ISSUE,
                    "The measurement signal was poor, so no performance-based recommendation is made. You may retry when conditions are suitable.");
        }
        if (userReport == UserReport.DIFFICULT) {
            return new PracticeRecommendation(PracticeRecommendation.Action.REVIEW_WITH_SUPPORT,
                    "You reported difficulty. Reviewing this lesson or asking a qualified person for support is optional; the curriculum does not establish an easier/harder lesson relationship.");
        }
        if (participationComplete && userReport == UserReport.COMFORTABLE) {
            return new PracticeRecommendation(PracticeRecommendation.Action.CONTINUE,
                    "Participation is complete and you reported comfort. Continuing is optional and does not imply skill improvement.");
        }
        if (userReport == UserReport.UNKNOWN || signalQuality == null
                || signalQuality == MeasurementQuality.UNKNOWN) {
            return new PracticeRecommendation(PracticeRecommendation.Action.NO_RECOMMENDATION,
                    "User-reported comfort or usable signal information is missing; no adaptive recommendation is supported.");
        }
        return new PracticeRecommendation(PracticeRecommendation.Action.REVIEW_WITH_SUPPORT,
                "The attempt does not support a measurement-based decision. Consider your comfort and seek qualified support for clinical questions.");
    }

    public PracticeRecommendation recommendFromHistory(List<AttemptMeasurementSet> attempts,
                                                       UserReport currentReport) {
        if (attempts == null || attempts.isEmpty() || attempts.get(0) == null) {
            return noHistory();
        }
        AttemptMeasurementSet latest = attempts.get(0);
        List<AttemptMeasurementSet> sameTask = new ArrayList<>();
        for (AttemptMeasurementSet attempt : attempts) {
            if (attempt != null && latest.lessonId.equals(attempt.lessonId)) sameTask.add(attempt);
        }
        UserReport report = currentReport == null ? UserReport.UNKNOWN : currentReport;
        if (report == UserReport.UNKNOWN) report = reportFromStored(latest);
        if (report == UserReport.DISCOMFORT) {
            return new PracticeRecommendation(PracticeRecommendation.Action.REST,
                    "You reported discomfort for this saved attempt. Rest or stop; this application does not prescribe treatment.");
        }
        if (sameTask.size() < 2) {
            return new PracticeRecommendation(PracticeRecommendation.Action.NO_RECOMMENDATION,
                    "There is insufficient saved history for this lesson to support a recommendation.");
        }

        int unavailableAttempts = 0;
        int poorQualityAttempts = 0;
        int inspected = Math.min(3, sameTask.size());
        for (int i = 0; i < inspected; i++) {
            AttemptMeasurementSet attempt = sameTask.get(i);
            boolean unavailable = attempt.measurements.isEmpty()
                    || "NOT_COLLECTED".equals(attempt.cameraMeasurementStatus);
            boolean poor = false;
            for (MeasurementRecord measurement : attempt.measurements) {
                if (measurement.status != MeasurementStatus.AVAILABLE) unavailable = true;
                if (measurement.status == MeasurementStatus.AVAILABLE
                        && measurement.quality == MeasurementQuality.POOR) poor = true;
            }
            if (unavailable) unavailableAttempts++;
            if (poor) poorQualityAttempts++;
        }
        if (unavailableAttempts >= 2) {
            return new PracticeRecommendation(PracticeRecommendation.Action.NO_RECOMMENDATION,
                    "Measurements were unavailable in repeated attempts. You may enable an optional source if you want measurements; missing data is not evidence about speech performance.");
        }
        if (poorQualityAttempts >= 2) {
            return new PracticeRecommendation(PracticeRecommendation.Action.REPEAT_AFTER_SIGNAL_ISSUE,
                    "Signal quality was poor in repeated attempts. Any retry is optional and concerns measurement quality only, not speech performance.");
        }
        if (report == UserReport.DIFFICULT) {
            return new PracticeRecommendation(PracticeRecommendation.Action.REVIEW_WITH_SUPPORT,
                    "You reported difficulty. Consider reviewing this lesson or asking a qualified person for support; no easier/harder lesson relationship is inferred.");
        }
        if (report == UserReport.COMFORTABLE && "completed".equals(latest.status)) {
            return new PracticeRecommendation(PracticeRecommendation.Action.CONTINUE,
                    "You reported comfort and have repeated saved participation history. Continuing is optional and does not imply skill improvement.");
        }
        return new PracticeRecommendation(PracticeRecommendation.Action.NO_RECOMMENDATION,
                "The saved history and user report do not support a specific optional next step.");
    }

    private UserReport reportFromStored(AttemptMeasurementSet attempt) {
        String report = attempt.researchComfortReport;
        if (report.isEmpty()) report = attempt.selfAssessment;
        if ("COMFORTABLE".equalsIgnoreCase(report) || "Comfortable".equalsIgnoreCase(report)) {
            return UserReport.COMFORTABLE;
        }
        if ("DIFFICULT".equalsIgnoreCase(report) || "A little effort".equalsIgnoreCase(report)) {
            return UserReport.DIFFICULT;
        }
        if ("DISCOMFORT".equalsIgnoreCase(report)
                || "I want to change or stop".equalsIgnoreCase(report)) {
            return UserReport.DISCOMFORT;
        }
        return UserReport.UNKNOWN;
    }

    private PracticeRecommendation noHistory() {
        return new PracticeRecommendation(PracticeRecommendation.Action.NO_RECOMMENDATION,
                "There is no saved attempt history to support a recommendation.");
    }
}
