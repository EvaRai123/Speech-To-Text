package com.example.speechpractice;

public enum PracticeExerciseType {
    SUSTAINED_VOWEL("Hold a comfortable sound"),
    CONTROLLED_VOICE_ONSET("Start your voice gently"),
    COMFORTABLE_LOUDNESS("Try gentle voice-level changes");

    private final String label;

    PracticeExerciseType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static PracticeExerciseType fromName(String name) {
        if (name == null) return SUSTAINED_VOWEL;
        for (PracticeExerciseType value : values()) {
            if (value.name().equalsIgnoreCase(name)) return value;
        }
        return SUSTAINED_VOWEL;
    }
}
