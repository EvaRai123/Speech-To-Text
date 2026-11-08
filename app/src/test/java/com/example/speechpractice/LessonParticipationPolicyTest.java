package com.example.speechpractice;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LessonParticipationPolicyTest {
    @Test
    public void areaNineCannotBeCompletedBySkippingEveryPracticeTurn() {
        LessonDefinition lesson = LessonCatalog.getLesson("mouth_1");
        int allSteps = lesson.prompts.size();

        assertFalse(LessonParticipationPolicy.canComplete(lesson, allSteps, allSteps));
        assertFalse(LessonParticipationPolicy.canComplete(lesson, 0, 0));
    }

    @Test
    public void areaNineAllowsCompletionAfterOnePracticedTurnAndPreservesSkips() {
        LessonDefinition lesson = LessonCatalog.getLesson("mouth_1");

        assertTrue(LessonParticipationPolicy.canComplete(lesson, 3, 2));
        assertTrue(LessonParticipationPolicy.canComplete(lesson, 3, 0));
    }

    @Test
    public void existingLessonCompletionPolicyIsUnchanged() {
        LessonDefinition lesson = LessonCatalog.getLesson("breath_1");

        assertTrue(LessonParticipationPolicy.canComplete(lesson, 0, 0));
    }
}
