package com.example.speechpractice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LessonDefinition implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String ACTIVITY_OBSERVE = "observe";
    public static final String ACTIVITY_BREATHING = "breathing";
    public static final String ACTIVITY_VOICE = "voice";
    public static final String ACTIVITY_PITCH = "pitch";
    public static final String ACTIVITY_LOUDNESS = "loudness";
    public static final String ACTIVITY_SOUND = "sound";
    public static final String ACTIVITY_WORD = "word";
    public static final String ACTIVITY_PHRASE = "phrase";
    public static final String ACTIVITY_READING = "reading";
    public static final String ACTIVITY_FREE_SPEAKING = "free_speaking";

    public final String id;
    public final String areaKey;
    public final String areaTitle;
    public final String title;
    public final String objective;
    public final String instructions;
    public final String focus;
    public final String difficulty;
    public final String estimatedDuration;
    public final String activityType;
    public final List<String> prompts;
    public final boolean recordingRequired;
    public final boolean selfAssessmentRequired;
    public final int timedStepSeconds;
    public final int restSeconds;
    public final String groupTitle;
    public final String educationalExplanation;
    public final LessonMeasurementRequirements measurementRequirements;

    public LessonDefinition(String id, String areaKey, String areaTitle, String title, String objective,
                           String instructions, String focus, String difficulty, String estimatedDuration) {
        this(id, areaKey, areaTitle, title, objective, instructions, focus, difficulty, estimatedDuration,
                ACTIVITY_PHRASE, Collections.singletonList(title), true, false, 0, 5,
                "", "This activity offers a guided opportunity to practise at your own pace.",
                LessonMeasurementRequirements.forLesson(areaKey, id, ACTIVITY_PHRASE, true));
    }

    public LessonDefinition(String id, String areaKey, String areaTitle, String title, String objective,
                            String instructions, String focus, String difficulty, String estimatedDuration,
                            String activityType, List<String> prompts, boolean recordingRequired,
                            boolean selfAssessmentRequired, int timedStepSeconds, int restSeconds) {
        this(id, areaKey, areaTitle, title, objective, instructions, focus, difficulty, estimatedDuration,
                activityType, prompts, recordingRequired, selfAssessmentRequired, timedStepSeconds, restSeconds,
                "", "This activity offers a guided opportunity to practise at your own pace.",
                LessonMeasurementRequirements.forLesson(areaKey, id, activityType, recordingRequired));
    }

    public LessonDefinition(String id, String areaKey, String areaTitle, String title, String objective,
                            String instructions, String focus, String difficulty, String estimatedDuration,
                            String activityType, List<String> prompts, boolean recordingRequired,
                            boolean selfAssessmentRequired, int timedStepSeconds, int restSeconds,
                            String groupTitle, String educationalExplanation,
                            LessonMeasurementRequirements measurementRequirements) {
        if (id == null || areaKey == null || title == null || activityType == null
                || prompts == null || prompts.isEmpty() || measurementRequirements == null) {
            throw new IllegalArgumentException("A lesson requires an ID, area, title, activity, prompts, and measurement requirements.");
        }
        this.id = id;
        this.areaKey = areaKey;
        this.areaTitle = areaTitle;
        this.title = title;
        this.objective = objective;
        this.instructions = instructions;
        this.focus = focus;
        this.difficulty = difficulty;
        this.estimatedDuration = estimatedDuration;
        this.activityType = activityType;
        this.prompts = Collections.unmodifiableList(new ArrayList<>(prompts));
        this.recordingRequired = recordingRequired;
        this.selfAssessmentRequired = selfAssessmentRequired;
        this.timedStepSeconds = Math.max(0, timedStepSeconds);
        this.restSeconds = Math.max(0, restSeconds);
        this.groupTitle = groupTitle == null ? "" : groupTitle;
        this.educationalExplanation = educationalExplanation == null ? "" : educationalExplanation;
        this.measurementRequirements = measurementRequirements;
    }
}
