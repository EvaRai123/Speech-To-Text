package com.example.speechpractice;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class LessonMeasurementRequirements implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Need {
        REQUIRED,
        OPTIONAL,
        NOT_APPLICABLE
    }

    public static final class ChannelRequirement implements Serializable {
        private static final long serialVersionUID = 1L;
        public final Need need;
        public final Set<MeasurementType> relevantTypes;
        public final MeasurementQuality minimumQuality;
        public final boolean cameraAssistanceApplicable;
        public final String limitation;

        private ChannelRequirement(Need need, Set<MeasurementType> relevantTypes,
                                   MeasurementQuality minimumQuality,
                                   boolean cameraAssistanceApplicable, String limitation) {
            this.need = need;
            this.relevantTypes = Collections.unmodifiableSet(relevantTypes.isEmpty()
                    ? EnumSet.noneOf(MeasurementType.class) : EnumSet.copyOf(relevantTypes));
            this.minimumQuality = minimumQuality;
            this.cameraAssistanceApplicable = cameraAssistanceApplicable;
            this.limitation = limitation == null ? "" : limitation;
            if (need == Need.NOT_APPLICABLE && !this.relevantTypes.isEmpty()) {
                throw new IllegalArgumentException("Not-applicable channels cannot list measurement types.");
            }
            if (need != Need.NOT_APPLICABLE && this.relevantTypes.isEmpty()) {
                throw new IllegalArgumentException("Applicable channels must list relevant measurement types.");
            }
            if (cameraAssistanceApplicable && need == Need.NOT_APPLICABLE) {
                throw new IllegalArgumentException("Camera assistance cannot apply to a not-applicable camera channel.");
            }
        }
    }

    private final Map<MeasurementSource, ChannelRequirement> channels;

    private LessonMeasurementRequirements(Map<MeasurementSource, ChannelRequirement> channels) {
        EnumMap<MeasurementSource, ChannelRequirement> copy = new EnumMap<>(MeasurementSource.class);
        copy.putAll(channels);
        for (MeasurementSource source : MeasurementSource.values()) {
            if (!copy.containsKey(source)) {
                copy.put(source, new ChannelRequirement(Need.NOT_APPLICABLE,
                        EnumSet.noneOf(MeasurementType.class), MeasurementQuality.UNKNOWN, false, ""));
            }
        }
        this.channels = Collections.unmodifiableMap(copy);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ChannelRequirement forSource(MeasurementSource source) {
        return channels.get(source);
    }

    public Map<MeasurementSource, ChannelRequirement> getChannels() {
        return channels;
    }

    public boolean hasRequiredMeasurements() {
        for (ChannelRequirement requirement : channels.values()) {
            if (requirement.need == Need.REQUIRED) return true;
        }
        return false;
    }

    public String getRequiredMeasurementBlocker(Iterable<MeasurementRecord> measurements) {
        for (Map.Entry<MeasurementSource, ChannelRequirement> entry : channels.entrySet()) {
            ChannelRequirement requirement = entry.getValue();
            if (requirement.need != Need.REQUIRED) continue;
            for (MeasurementType type : requirement.relevantTypes) {
                boolean available = false;
                boolean meetsMinimumQuality = false;
                if (measurements != null) {
                    for (MeasurementRecord measurement : measurements) {
                        if (measurement != null && measurement.source == entry.getKey()
                                && measurement.type == type
                                && measurement.status == MeasurementStatus.AVAILABLE) {
                            available = true;
                            if (requirement.minimumQuality == MeasurementQuality.UNKNOWN
                                    || qualityRank(measurement.quality) >= qualityRank(requirement.minimumQuality)) {
                                meetsMinimumQuality = true;
                            }
                        }
                    }
                }
                if (!available) {
                    return "A required " + entry.getKey().name().toLowerCase()
                            + " measurement (" + type.name().toLowerCase().replace('_', ' ')
                            + ") is unavailable.";
                }
                if (!meetsMinimumQuality) {
                    return "The required " + entry.getKey().name().toLowerCase()
                            + " measurement did not meet the minimum signal quality.";
                }
            }
        }
        return null;
    }

    private static int qualityRank(MeasurementQuality quality) {
        switch (quality) {
            case EXCELLENT: return 4;
            case GOOD: return 3;
            case LIMITED: return 2;
            case POOR: return 1;
            case UNKNOWN:
            default: return 0;
        }
    }

    public static LessonMeasurementRequirements forLesson(String areaKey, String lessonId,
                                                           String activityType, boolean recordingSuggested) {
        Builder builder = builder();
        if (recordingSuggested) {
            builder.set(MeasurementSource.AUDIO, Need.OPTIONAL, MeasurementQuality.LIMITED,
                    "Recording feedback is optional and depends on phone and room conditions.",
                    audioTypes(areaKey, lessonId, activityType));
        }

        if ("phonation".equals(areaKey) || "pitch".equals(areaKey) || "loudness".equals(areaKey)) {
            builder.set(MeasurementSource.ACCELEROMETER, Need.OPTIONAL, MeasurementQuality.GOOD,
                    "A future neck sensor measures mechanical vibration only at its mounting point.",
                    MeasurementType.MECHANICAL_VIBRATION);
            builder.set(MeasurementSource.SEMG, Need.OPTIONAL, MeasurementQuality.GOOD,
                    "A future sEMG channel requires skin electrodes and a suitable protected biopotential front end.",
                    MeasurementType.SURFACE_MUSCLE_ACTIVITY);
        }

        Set<MeasurementType> cameraTypes = cameraTypes(areaKey, lessonId);
        if (!cameraTypes.isEmpty()) {
            builder.set(MeasurementSource.CAMERA, Need.OPTIONAL, MeasurementQuality.GOOD,
                    "Optional camera feedback reports relative facial-contour proxies only; it cannot verify tongue placement, temporal speech transitions, or pronunciation.",
                    cameraTypes.toArray(new MeasurementType[0]));
        } else {
            builder.set(MeasurementSource.CAMERA, Need.NOT_APPLICABLE, MeasurementQuality.UNKNOWN,
                    "Camera assistance is not applicable; tongue placement and pronunciation correctness cannot be inferred.");
        }
        return builder.build();
    }

    private static MeasurementType[] audioTypes(String areaKey, String lessonId, String activityType) {
        EnumSet<MeasurementType> types = EnumSet.of(MeasurementType.ATTEMPT_DURATION);
        if ("phonation".equals(areaKey)) {
            types.add(MeasurementType.SPEECH_ACTIVITY);
            types.add(MeasurementType.VOICED_DURATION);
            types.add(MeasurementType.FUNDAMENTAL_FREQUENCY);
            types.add(MeasurementType.PITCH_VARIATION);
        } else if ("pitch".equals(areaKey) || LessonDefinition.ACTIVITY_PITCH.equals(activityType)) {
            types.add(MeasurementType.VOICED_DURATION);
            types.add(MeasurementType.FUNDAMENTAL_FREQUENCY);
            types.add(MeasurementType.PITCH_VARIATION);
        } else if ("loudness".equals(areaKey) || LessonDefinition.ACTIVITY_LOUDNESS.equals(activityType)) {
            types.add(MeasurementType.RELATIVE_INTENSITY);
            types.add(MeasurementType.SPEECH_ACTIVITY);
        } else if ("breath_3".equals(lessonId)) {
            types.add(MeasurementType.SPEECH_ACTIVITY);
        } else {
            types.add(MeasurementType.SPEECH_ACTIVITY);
            types.add(MeasurementType.PAUSE_DURATION);
        }
        return types.toArray(new MeasurementType[0]);
    }

    private static Set<MeasurementType> cameraTypes(String areaKey, String lessonId) {
        if (lessonId == null) return Collections.emptySet();
        boolean areaMatches = ("mouth_articulation".equals(areaKey) && lessonId.startsWith("mouth_"))
                || ("articulation".equals(areaKey) && lessonId.startsWith("articulation_"))
                || ("syllables".equals(areaKey) && lessonId.startsWith("syllables_"))
                || ("connected".equals(areaKey) && lessonId.startsWith("connected_"));
        if (!areaMatches) return Collections.emptySet();
        switch (lessonId) {
            case "articulation_1":
                return EnumSet.of(MeasurementType.LIP_CLOSURE_RATIO,
                        MeasurementType.LIP_SEPARATION_RATIO);
            case "mouth_1":
                return Collections.emptySet();
            case "mouth_2":
                return EnumSet.of(MeasurementType.LIP_CLOSURE_RATIO,
                        MeasurementType.LIP_SEPARATION_RATIO);
            case "mouth_3":
                return EnumSet.of(MeasurementType.LIP_SPREAD_WIDTH_RATIO);
            case "mouth_4":
                return EnumSet.of(MeasurementType.LIP_ROUNDING_PROXY);
            case "mouth_5":
                return EnumSet.of(MeasurementType.LIP_SPREAD_WIDTH_RATIO,
                        MeasurementType.LIP_ROUNDING_PROXY);
            case "mouth_6":
            case "mouth_8":
                return EnumSet.of(MeasurementType.MOUTH_OPENING_RATIO);
            case "mouth_7":
            case "mouth_9":
                return EnumSet.of(MeasurementType.LOWER_FACE_DISPLACEMENT_PROXY);
            case "mouth_10":
                return Collections.emptySet();
            case "mouth_11":
                return EnumSet.of(MeasurementType.LIP_CLOSURE_RATIO,
                        MeasurementType.LIP_SEPARATION_RATIO);
            case "mouth_12":
                return EnumSet.of(MeasurementType.LIP_CLOSURE_RATIO);
            case "mouth_13":
                return EnumSet.of(MeasurementType.VISIBLE_LIP_TEETH_POSITION);
            case "mouth_14":
                return EnumSet.of(MeasurementType.LIP_CLOSURE_RATIO,
                        MeasurementType.LIP_SEPARATION_RATIO);
            default:
                return Collections.emptySet();
        }
    }

    public static final class Builder {
        private final EnumMap<MeasurementSource, ChannelRequirement> channels =
                new EnumMap<>(MeasurementSource.class);

        public Builder set(MeasurementSource source, Need need, MeasurementQuality minimumQuality,
                          String limitation, MeasurementType... relevantTypes) {
            if (source == null || need == null || minimumQuality == null) {
                throw new IllegalArgumentException("Source, need, and minimum quality are required.");
            }
            EnumSet<MeasurementType> types = EnumSet.noneOf(MeasurementType.class);
            if (relevantTypes != null) {
                for (MeasurementType type : relevantTypes) {
                    if (type != null) types.add(type);
                }
            }
            boolean cameraApplies = source == MeasurementSource.CAMERA && need != Need.NOT_APPLICABLE;
            channels.put(source, new ChannelRequirement(need, types, minimumQuality,
                    cameraApplies, limitation));
            return this;
        }

        public LessonMeasurementRequirements build() {
            return new LessonMeasurementRequirements(channels);
        }
    }
}
