package com.example.speechpractice;

import androidx.annotation.DrawableRes;

public final class VisualContent {
    private VisualContent() {
    }

    @DrawableRes
    public static int areaIllustration(String areaKey) {
        if ("breath".equals(areaKey)) return R.drawable.illustration_breathing;
        if ("phonation".equals(areaKey) || "pitch".equals(areaKey)) return R.drawable.illustration_speaking;
        if ("loudness".equals(areaKey)) return R.drawable.illustration_microphone;
        if ("articulation".equals(areaKey) || "mouth_articulation".equals(areaKey)) return R.drawable.illustration_mouth;
        if ("syllables".equals(areaKey)) return R.drawable.illustration_book;
        if ("pacing".equals(areaKey)) return R.drawable.illustration_ball;
        if ("connected".equals(areaKey)) return R.drawable.illustration_chair;
        return R.drawable.illustration_feedback;
    }

    @DrawableRes
    public static int lessonIllustration(LessonDefinition lesson) {
        if (lesson == null) return R.drawable.illustration_speaking;
        if ("mouth_articulation".equals(lesson.areaKey)) {
            switch (lesson.id) {
                case "mouth_1":
                    return R.drawable.illustration_mouth_relaxed;
                case "mouth_2":
                case "mouth_12":
                    return R.drawable.illustration_mouth_closed;
                case "mouth_3":
                    return R.drawable.illustration_mouth_smile;
                case "mouth_4":
                case "mouth_5":
                    return R.drawable.illustration_mouth_rounded;
                case "mouth_6":
                case "mouth_8":
                    return R.drawable.illustration_mouth_open;
                case "mouth_7":
                case "mouth_9":
                case "mouth_10":
                    return R.drawable.illustration_mouth_controlled;
                case "mouth_11":
                    return R.drawable.illustration_mouth_pb;
                case "mouth_13":
                    return R.drawable.illustration_mouth_fv;
                default:
                    return R.drawable.illustration_mouth_syllable;
            }
        }
        switch (lesson.areaKey) {
            case "breath":
                return R.drawable.illustration_breathing;
            case "phonation":
            case "pitch":
                return R.drawable.illustration_speaking;
            case "loudness":
                return R.drawable.illustration_microphone;
            case "articulation":
                return R.drawable.illustration_mouth;
            case "syllables":
                return R.drawable.illustration_book;
            case "pacing":
                return R.drawable.illustration_ball;
            case "connected":
                return R.drawable.illustration_chair;
            default:
                return R.drawable.illustration_speaking;
        }
    }

    public static String lessonIllustrationDescription(LessonDefinition lesson) {
        if (lesson == null) return "Practice illustration. It is not a sensor measurement.";
        if ("mouth_13".equals(lesson.id)) {
            return "Illustration of lips near the teeth for F and V. This is not a camera measurement.";
        }
        if ("mouth_11".equals(lesson.id)) {
            return "Illustration of lips coming together for P and B. This is not a camera measurement.";
        }
        if ("mouth_12".equals(lesson.id)) {
            return "Illustration of lips together for M. This is not a camera measurement.";
        }
        if ("mouth_articulation".equals(lesson.areaKey)) {
            return "Mouth movement illustration. It does not show a camera measurement or tongue position.";
        }
        return "Practice illustration. It is not a sensor measurement.";
    }
}
