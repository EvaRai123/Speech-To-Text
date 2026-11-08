package com.example.speechpractice;

public enum ResearchCondition {
    MICROPHONE_ONLY("A: Microphone only", false, false),
    MICROPHONE_ACCELEROMETER("B: Microphone + accelerometer", true, false),
    MICROPHONE_ACCELEROMETER_SEMG("C: Microphone + accelerometer + sEMG", true, true);

    public final String label;
    public final boolean requiresAccelerometer;
    public final boolean requiresSemg;

    ResearchCondition(String label, boolean requiresAccelerometer, boolean requiresSemg) {
        this.label = label;
        this.requiresAccelerometer = requiresAccelerometer;
        this.requiresSemg = requiresSemg;
    }

    public static ResearchCondition fromName(String name) {
        if (name == null) return MICROPHONE_ONLY;
        for (ResearchCondition condition : values()) {
            if (condition.name().equalsIgnoreCase(name) || condition.label.equalsIgnoreCase(name)) {
                return condition;
            }
        }
        return MICROPHONE_ONLY;
    }
}
