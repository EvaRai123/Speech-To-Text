package com.example.speechpractice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PersonalBaseline {
    public final String baselineId;
    public final String participantId;
    public final MeasurementSource source;
    public final String taskId;
    public final String configurationId;
    public final long createdAtWallClockMs;
    public final long updatedAtWallClockMs;
    public final long expiresAtWallClockMs;
    public final Map<MeasurementType, Double> values;
    public final Map<MeasurementType, Integer> sampleCounts;
    public final List<BaselineUpdate> updateHistory;

    private PersonalBaseline(String baselineId, String participantId, MeasurementSource source,
                             String taskId, String configurationId, long createdAtWallClockMs,
                             long updatedAtWallClockMs, long expiresAtWallClockMs,
                             Map<MeasurementType, Double> values, Map<MeasurementType, Integer> sampleCounts,
                             List<BaselineUpdate> updateHistory) {
        this.baselineId = baselineId;
        this.participantId = participantId;
        this.source = source;
        this.taskId = taskId;
        this.configurationId = configurationId;
        this.createdAtWallClockMs = createdAtWallClockMs;
        this.updatedAtWallClockMs = updatedAtWallClockMs;
        this.expiresAtWallClockMs = expiresAtWallClockMs;
        this.values = Collections.unmodifiableMap(new EnumMap<>(values));
        this.sampleCounts = Collections.unmodifiableMap(new EnumMap<>(sampleCounts));
        this.updateHistory = Collections.unmodifiableList(new ArrayList<>(updateHistory));
    }

    public static PersonalBaseline create(String participantId, MeasurementSource source,
                                          String taskId, String configurationId,
                                          List<MeasurementRecord> measurements,
                                          long createdAtWallClockMs, long expiresAtWallClockMs) {
        requireIdentity(participantId, source, taskId, configurationId, createdAtWallClockMs, expiresAtWallClockMs);
        if (measurements == null || measurements.isEmpty()) {
            throw new IllegalArgumentException("A baseline requires at least one usable measurement.");
        }
        Map<MeasurementType, Double> values = new EnumMap<>(MeasurementType.class);
        Map<MeasurementType, Integer> counts = new EnumMap<>(MeasurementType.class);
        List<BaselineUpdate> history = new ArrayList<>();
        for (MeasurementRecord measurement : measurements) {
            if (measurement == null || measurement.source != source
                    || measurement.status != MeasurementStatus.AVAILABLE
                    || measurement.quality == MeasurementQuality.POOR
                    || measurement.quality == MeasurementQuality.UNKNOWN
                    || measurement.value == null || !Double.isFinite(measurement.value)
                    || !measurement.type.accepts(source, measurement.unit, measurement.value)) continue;
            values.merge(measurement.type, measurement.value, Double::sum);
            counts.merge(measurement.type, 1, Integer::sum);
            history.add(new BaselineUpdate(measurement.type, measurement.value, createdAtWallClockMs));
        }
        if (values.isEmpty()) throw new IllegalArgumentException("No available non-poor measurements match this baseline channel.");
        for (MeasurementType type : new ArrayList<>(values.keySet())) {
            values.put(type, values.get(type) / counts.get(type));
        }
        return new PersonalBaseline(UUID.randomUUID().toString(), participantId, source, taskId,
                configurationId, createdAtWallClockMs, createdAtWallClockMs, expiresAtWallClockMs,
                values, counts, history);
    }

    public static PersonalBaseline createFromAttempts(String participantId, MeasurementSource source,
                                                      String taskId, String configurationId,
                                                      List<AttemptMeasurementSet> attempts,
                                                      long createdAtWallClockMs,
                                                      long expiresAtWallClockMs) {
        requireIdentity(participantId, source, taskId, configurationId,
                createdAtWallClockMs, expiresAtWallClockMs);
        Map<MeasurementType, List<BaselineObservation>> observations =
                eligibleObservations(attempts, source, taskId, configurationId, Collections.emptyList());
        Map<MeasurementType, Double> values = new EnumMap<>(MeasurementType.class);
        Map<MeasurementType, Integer> counts = new EnumMap<>(MeasurementType.class);
        List<BaselineUpdate> history = new ArrayList<>();
        for (Map.Entry<MeasurementType, List<BaselineObservation>> entry : observations.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            double sum = 0.0;
            for (BaselineObservation observation : entry.getValue()) {
                sum += observation.value;
                counts.merge(entry.getKey(), 1, Integer::sum);
                history.add(new BaselineUpdate(entry.getKey(), observation.value,
                        observation.attempt.updatedAtWallClockMs, observation.attempt.attemptId));
            }
            values.put(entry.getKey(), sum / entry.getValue().size());
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("At least two distinct completed attempts with valid matching measurements are required.");
        }
        return new PersonalBaseline(UUID.randomUUID().toString(), participantId, source, taskId,
                configurationId, createdAtWallClockMs, createdAtWallClockMs, expiresAtWallClockMs,
                values, counts, history);
    }

    public PersonalBaseline updateFromAttempts(List<AttemptMeasurementSet> attempts,
                                              String participant, MeasurementSource updateSource,
                                              String updateTask, String updateConfiguration,
                                              long updatedAtWallClockMs) {
        if (!isCompatible(participant, updateSource, updateTask, updateConfiguration, updatedAtWallClockMs)
                || updatedAtWallClockMs < this.updatedAtWallClockMs) {
            throw new IllegalArgumentException("Baseline update has incompatible or expired identity/configuration.");
        }
        Map<MeasurementType, Double> updatedValues = new EnumMap<>(values);
        Map<MeasurementType, Integer> updatedCounts = new EnumMap<>(sampleCounts);
        List<BaselineUpdate> history = new ArrayList<>(updateHistory);
        Map<MeasurementType, List<BaselineObservation>> observations =
                eligibleObservations(attempts, source, taskId, configurationId, history);
        int acceptedAttempts = 0;
        for (Map.Entry<MeasurementType, List<BaselineObservation>> entry : observations.entrySet()) {
            for (BaselineObservation observation : entry.getValue()) {
                int count = updatedCounts.getOrDefault(entry.getKey(), 0);
                double mean = updatedValues.getOrDefault(entry.getKey(), 0.0);
                updatedValues.put(entry.getKey(), (mean * count + observation.value) / (count + 1));
                updatedCounts.put(entry.getKey(), count + 1);
                history.add(new BaselineUpdate(entry.getKey(), observation.value,
                        observation.attempt.updatedAtWallClockMs, observation.attempt.attemptId));
                acceptedAttempts++;
            }
        }
        if (acceptedAttempts == 0) {
            throw new IllegalArgumentException("No new completed attempt contains valid matching measurements.");
        }
        return new PersonalBaseline(baselineId, participantId, source, taskId, configurationId,
                createdAtWallClockMs, updatedAtWallClockMs, expiresAtWallClockMs,
                updatedValues, updatedCounts, history);
    }

    static PersonalBaseline restore(String baselineId, String participantId, MeasurementSource source,
                                   String taskId, String configurationId, long createdAtWallClockMs,
                                   long updatedAtWallClockMs, long expiresAtWallClockMs,
                                   Map<MeasurementType, Double> values,
                                   Map<MeasurementType, Integer> sampleCounts,
                                   List<BaselineUpdate> updateHistory) {
        requireIdentity(participantId, source, taskId, configurationId,
                createdAtWallClockMs, expiresAtWallClockMs);
        if (baselineId == null || baselineId.trim().isEmpty()
                || updatedAtWallClockMs < createdAtWallClockMs
                || values == null || sampleCounts == null || values.isEmpty()) {
            throw new IllegalArgumentException("Stored baseline is incomplete.");
        }
        for (Map.Entry<MeasurementType, Double> entry : values.entrySet()) {
            Integer count = sampleCounts.get(entry.getKey());
            if (entry.getKey().source != source || !Double.isFinite(entry.getValue())
                    || count == null || count <= 0) {
                throw new IllegalArgumentException("Stored baseline contains an invalid channel measurement.");
            }
        }
        return new PersonalBaseline(baselineId, participantId, source, taskId, configurationId,
                createdAtWallClockMs, updatedAtWallClockMs, expiresAtWallClockMs,
                values, sampleCounts, updateHistory == null ? Collections.emptyList() : updateHistory);
    }

    public PersonalBaseline update(List<MeasurementRecord> measurements, String participant,
                                   MeasurementSource updateSource, String updateTask,
                                   String updateConfiguration, long updatedAtWallClockMs) {
        if (!isCompatible(participant, updateSource, updateTask, updateConfiguration, updatedAtWallClockMs)) {
            throw new IllegalArgumentException("Baseline update has incompatible participant, channel, task, configuration, or validity.");
        }
        if (updatedAtWallClockMs < this.updatedAtWallClockMs) {
            throw new IllegalArgumentException("Baseline updates must not move backward in time.");
        }
        Map<MeasurementType, Double> updatedValues = new EnumMap<>(values);
        Map<MeasurementType, Integer> updatedCounts = new EnumMap<>(sampleCounts);
        List<BaselineUpdate> history = new ArrayList<>(updateHistory);
        if (measurements == null) throw new IllegalArgumentException("Baseline update measurements are required.");
        int accepted = 0;
        for (MeasurementRecord measurement : measurements) {
            if (measurement == null || measurement.source != source
                    || measurement.status != MeasurementStatus.AVAILABLE
                    || measurement.quality == MeasurementQuality.POOR
                    || measurement.quality == MeasurementQuality.UNKNOWN
                    || measurement.value == null || !Double.isFinite(measurement.value)
                    || !measurement.type.accepts(source, measurement.unit, measurement.value)) continue;
            int count = updatedCounts.getOrDefault(measurement.type, 0);
            double oldMean = updatedValues.getOrDefault(measurement.type, 0.0);
            updatedValues.put(measurement.type, (oldMean * count + measurement.value) / (count + 1));
            updatedCounts.put(measurement.type, count + 1);
            history.add(new BaselineUpdate(measurement.type, measurement.value, updatedAtWallClockMs));
            accepted++;
        }
        if (accepted == 0) throw new IllegalArgumentException("Baseline update contains no usable matching measurements.");
        return new PersonalBaseline(baselineId, participantId, source, taskId, configurationId,
                createdAtWallClockMs, updatedAtWallClockMs, expiresAtWallClockMs,
                updatedValues, updatedCounts, history);
    }

    public boolean isCompatible(String participant, MeasurementSource candidateSource, String candidateTask,
                                String candidateConfiguration, long atWallClockMs) {
        return atWallClockMs >= createdAtWallClockMs
                && atWallClockMs >= updatedAtWallClockMs
                && atWallClockMs < expiresAtWallClockMs
                && participantId.equals(participant)
                && source == candidateSource
                && taskId.equals(candidateTask)
                && configurationId.equals(candidateConfiguration);
    }

    public BaselineComparison compare(MeasurementRecord measurement, String participant,
                                      String candidateTask, String candidateConfiguration,
                                      long atWallClockMs) {
        if (measurement == null || !isCompatible(participant,
                measurement == null ? null : measurement.source, candidateTask,
                candidateConfiguration, atWallClockMs)) {
            return BaselineComparison.unavailable("No valid baseline matches participant, channel, task, configuration, and validity period.");
        }
        if (measurement.status != MeasurementStatus.AVAILABLE || measurement.quality == MeasurementQuality.POOR
                || measurement.quality == MeasurementQuality.UNKNOWN || measurement.value == null) {
            return BaselineComparison.unavailable("Measurement is unavailable or poor quality.");
        }
        Double baseline = values.get(measurement.type);
        if (baseline == null || !measurement.type.canonicalUnit.equals(measurement.unit)) {
            return BaselineComparison.unavailable("No compatible personal reference exists for this measurement type and unit.");
        }
        return new BaselineComparison(MeasurementStatus.AVAILABLE, measurement.value - baseline,
                measurement.unit, "Difference from this user's personal engineering baseline; not a clinical target or improvement score.");
    }

    private static void requireIdentity(String participantId, MeasurementSource source, String taskId,
                                        String configurationId, long createdAt, long expiresAt) {
        if (participantId == null || participantId.trim().isEmpty() || source == null
                || taskId == null || taskId.trim().isEmpty()
                || configurationId == null || configurationId.trim().isEmpty()
                || createdAt < 0L || expiresAt <= createdAt) {
            throw new IllegalArgumentException("Baseline identity, configuration, and validity interval are required.");
        }
    }

    private static Map<MeasurementType, List<BaselineObservation>> eligibleObservations(
            List<AttemptMeasurementSet> attempts, MeasurementSource source, String taskId,
            String configurationId, List<BaselineUpdate> existingHistory) {
        if (attempts == null) throw new IllegalArgumentException("Saved attempts are required.");
        java.util.Set<String> alreadyIncluded = new java.util.HashSet<>();
        for (BaselineUpdate update : existingHistory) {
            if (update.attemptId != null && !update.attemptId.isEmpty()) {
                alreadyIncluded.add(update.type.name() + ":" + update.attemptId);
            }
        }
        Map<MeasurementType, Map<String, BaselineObservation>> byAttempt =
                new EnumMap<>(MeasurementType.class);
        for (AttemptMeasurementSet attempt : attempts) {
            if (attempt == null || !taskId.equals(attempt.lessonId)
                    || !"completed".equals(attempt.status)
                    || attempt.attemptId.trim().isEmpty()
                    || !configurationId.equals(attempt.sourceConfigurations.get(source))
                    ) continue;
            for (MeasurementRecord measurement : attempt.measurements) {
                if (measurement == null || measurement.source != source
                        || measurement.status != MeasurementStatus.AVAILABLE
                        || measurement.quality == MeasurementQuality.POOR
                        || measurement.quality == MeasurementQuality.UNKNOWN
                        || measurement.value == null || !Double.isFinite(measurement.value)
                        || !measurement.type.accepts(source, measurement.unit, measurement.value)) continue;
                String historyKey = measurement.type.name() + ":" + attempt.attemptId;
                if (alreadyIncluded.contains(historyKey)) continue;
                Map<String, BaselineObservation> typeAttempts =
                        byAttempt.computeIfAbsent(measurement.type, ignored -> new java.util.HashMap<>());
                BaselineObservation observation = typeAttempts.get(attempt.attemptId);
                if (observation == null) {
                    typeAttempts.put(attempt.attemptId, new BaselineObservation(attempt, measurement.value));
                } else {
                    observation.add(measurement.value);
                }
            }
        }
        Map<MeasurementType, List<BaselineObservation>> observations =
                new EnumMap<>(MeasurementType.class);
        for (Map.Entry<MeasurementType, Map<String, BaselineObservation>> entry : byAttempt.entrySet()) {
            observations.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return observations;
    }

    private static final class BaselineObservation {
        final AttemptMeasurementSet attempt;
        double value;
        int samples = 1;
        BaselineObservation(AttemptMeasurementSet attempt, double value) {
            this.attempt = attempt;
            this.value = value;
        }
        void add(double nextValue) {
            value = (value * samples + nextValue) / (samples + 1);
            samples++;
        }
    }

    public static final class BaselineUpdate {
        public final MeasurementType type;
        public final double value;
        public final long recordedAtWallClockMs;
        public final String attemptId;

        BaselineUpdate(MeasurementType type, double value, long recordedAtWallClockMs) {
            this(type, value, recordedAtWallClockMs, "");
        }

        BaselineUpdate(MeasurementType type, double value, long recordedAtWallClockMs, String attemptId) {
            this.type = type;
            this.value = value;
            this.recordedAtWallClockMs = recordedAtWallClockMs;
            this.attemptId = attemptId == null ? "" : attemptId;
        }
    }
}
