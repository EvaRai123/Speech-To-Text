package com.example.speechpractice;

public final class PracticeRecommendation {
    public enum Action {
        REST,
        REPEAT_AFTER_SIGNAL_ISSUE,
        TRY_EASIER_ACTIVITY,
        CONTINUE,
        REVIEW_WITH_SUPPORT,
        NO_RECOMMENDATION
    }

    public final Action action;
    public final String reason;

    PracticeRecommendation(Action action, String reason) {
        this.action = action;
        this.reason = reason;
    }
}
