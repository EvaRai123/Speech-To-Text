package com.example.speechpractice;

import android.content.Context;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BaselineIntegrationService {
    private BaselineIntegrationService() {
    }

    public static List<String> integrate(Context context, List<AttemptMeasurementSet> attempts,
                                         String participantId, long nowWallClockMs,
                                         long validityDurationMs) {
        if (context == null || attempts == null || participantId == null
                || participantId.trim().isEmpty() || validityDurationMs <= 0L) {
            throw new IllegalArgumentException("Saved attempts, local participant, context, and validity are required.");
        }
        Map<String, Map<MeasurementSource, Map<String, List<AttemptMeasurementSet>>>> grouped =
                new HashMap<>();
        Map<String, Set<MeasurementSource>> unconfigured = new HashMap<>();
        for (AttemptMeasurementSet attempt : attempts) {
            if (attempt == null || !"completed".equals(attempt.status)) continue;
            Set<MeasurementSource> seen = new HashSet<>();
            for (MeasurementRecord measurement : attempt.measurements) {
                if (!isUsable(measurement)) continue;
                seen.add(measurement.source);
            }
            for (MeasurementSource source : seen) {
                String configuration = attempt.sourceConfigurations.get(source);
                if (configuration == null || configuration.trim().isEmpty()) {
                    unconfigured.computeIfAbsent(attempt.lessonId, ignored -> new HashSet<>()).add(source);
                    continue;
                }
                grouped.computeIfAbsent(attempt.lessonId, ignored -> new EnumMap<>(MeasurementSource.class))
                        .computeIfAbsent(source, ignored -> new HashMap<>())
                        .computeIfAbsent(configuration, ignored -> new ArrayList<>()).add(attempt);
            }
        }

        List<String> outcomes = new ArrayList<>();
        for (Map.Entry<String, Set<MeasurementSource>> entry : unconfigured.entrySet()) {
            for (MeasurementSource source : entry.getValue()) {
                outcomes.add(entry.getKey() + " / " + source
                        + ": unavailable; saved attempts lack an observed source configuration identifier.");
            }
        }
        for (Map.Entry<String, Map<MeasurementSource, Map<String, List<AttemptMeasurementSet>>>> task
                : grouped.entrySet()) {
            for (Map.Entry<MeasurementSource, Map<String, List<AttemptMeasurementSet>>> source
                    : task.getValue().entrySet()) {
                for (Map.Entry<String, List<AttemptMeasurementSet>> configuration
                        : source.getValue().entrySet()) {
                    List<AttemptMeasurementSet> compatibleAttempts = configuration.getValue();
                    Set<String> attemptIds = new HashSet<>();
                    for (AttemptMeasurementSet attempt : compatibleAttempts) attemptIds.add(attempt.attemptId);
                    if (attemptIds.size() < 2) {
                        outcomes.add(task.getKey() + " / " + source.getKey()
                                + ": unavailable; fewer than two compatible completed attempts.");
                        continue;
                    }
                    List<PersonalBaseline> profiles =
                            CalibrationProfileStore.loadAll(context, participantId);
                    PersonalBaseline existing = null;
                    for (PersonalBaseline profile : profiles) {
                        if (profile.source == source.getKey() && profile.taskId.equals(task.getKey())
                                && profile.configurationId.equals(configuration.getKey())
                                && profile.isCompatible(participantId, source.getKey(), task.getKey(),
                                configuration.getKey(), nowWallClockMs)) {
                            existing = profile;
                            break;
                        }
                    }
                    if (existing != null) {
                        boolean historyCanBeDeduplicated = true;
                        for (PersonalBaseline.BaselineUpdate update : existing.updateHistory) {
                            if (update.attemptId.isEmpty()) {
                                historyCanBeDeduplicated = false;
                                break;
                            }
                        }
                        if (!historyCanBeDeduplicated) {
                            outcomes.add(task.getKey() + " / " + source.getKey()
                                    + ": update unavailable; existing baseline history has no saved attempt IDs for safe deduplication.");
                            continue;
                        }
                    }
                    try {
                        PersonalBaseline baseline;
                        if (existing == null) {
                            baseline = PersonalBaseline.createFromAttempts(participantId,
                                    source.getKey(), task.getKey(), configuration.getKey(),
                                    compatibleAttempts, nowWallClockMs, nowWallClockMs + validityDurationMs);
                        } else {
                            baseline = existing.updateFromAttempts(compatibleAttempts, participantId,
                                    source.getKey(), task.getKey(), configuration.getKey(), nowWallClockMs);
                        }
                        CalibrationProfileStore.save(context, baseline);
                        outcomes.add(task.getKey() + " / " + source.getKey()
                                + ": baseline " + (existing == null ? "created" : "updated")
                                + " from compatible saved attempt measurements.");
                    } catch (IllegalArgumentException exception) {
                        outcomes.add(task.getKey() + " / " + source.getKey()
                                + ": unavailable; " + exception.getMessage());
                    }
                }
            }
        }
        if (outcomes.isEmpty()) outcomes.add("No completed saved attempts contain usable measurements for baselines.");
        return outcomes;
    }

    private static boolean isUsable(MeasurementRecord measurement) {
        return measurement != null && measurement.status == MeasurementStatus.AVAILABLE
                && measurement.quality != MeasurementQuality.POOR
                && measurement.quality != MeasurementQuality.UNKNOWN
                && measurement.value != null && Double.isFinite(measurement.value)
                && measurement.type.accepts(measurement.source, measurement.unit, measurement.value);
    }
}
