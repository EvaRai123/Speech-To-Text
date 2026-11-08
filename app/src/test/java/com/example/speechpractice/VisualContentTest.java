package com.example.speechpractice;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VisualContentTest {
    @Test
    public void allNineAreasAndSixtyLessonsHaveLocalIllustrations() {
        assertTrue(LessonCatalog.getAreas().size() == 9);
        assertTrue(LessonCatalog.getLessonCount() == 60);
        for (LessonCatalog.LessonArea area : LessonCatalog.getAreas()) {
            assertNotEquals(0, VisualContent.areaIllustration(area.key));
            for (LessonDefinition lesson : area.lessons) {
                assertNotEquals(0, VisualContent.lessonIllustration(lesson));
                assertNotNull(VisualContent.lessonIllustrationDescription(lesson));
            }
        }
    }

    @Test
    public void areaNineCoreSoundIllustrationsRemainDistinctAndClearlyIllustrative() {
        LessonDefinition pb = LessonCatalog.getLesson("mouth_11");
        LessonDefinition em = LessonCatalog.getLesson("mouth_12");
        LessonDefinition fv = LessonCatalog.getLesson("mouth_13");

        assertNotEquals(VisualContent.lessonIllustration(pb), VisualContent.lessonIllustration(em));
        assertNotEquals(VisualContent.lessonIllustration(em), VisualContent.lessonIllustration(fv));
        assertTrue(VisualContent.lessonIllustrationDescription(pb).contains("not a camera measurement"));
        assertTrue(VisualContent.lessonIllustrationDescription(em).contains("not a camera measurement"));
        assertTrue(VisualContent.lessonIllustrationDescription(fv).contains("not a camera measurement"));
    }
}
