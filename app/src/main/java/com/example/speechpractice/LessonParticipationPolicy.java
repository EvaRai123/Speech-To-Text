package com.example.speechpractice;

public final class LessonParticipationPolicy {
    private LessonParticipationPolicy() {
    }

    public static boolean canComplete(LessonDefinition lesson, int completedSteps, int skippedSteps) {
        if (lesson == null) return false;
        if (!"mouth_articulation".equals(lesson.areaKey)) return true;
        return Math.max(0, completedSteps) > Math.max(0, skippedSteps);
    }
}
