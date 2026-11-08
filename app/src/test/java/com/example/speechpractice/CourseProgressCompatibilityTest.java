package com.example.speechpractice;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CourseProgressCompatibilityTest {
    @Test
    public void completionPreferenceAndExistingIdsRemainCompatible() throws Exception {
        assertEquals("speechbridge_course_progress", privateString("PREFS"));
        assertEquals("completed_lessons", privateString("MASTER_KEY"));
        assertEquals("cameraMeasurementStatus", privateString("CAMERA_MEASUREMENT_STATUS_KEY"));
        assertEquals("lesson_attempts", privateString("ATTEMPTS_KEY"));

        String[] originalAreaKeys = {
                "breath", "phonation", "pitch", "loudness",
                "articulation", "syllables", "pacing", "connected"
        };
        for (String areaKey : originalAreaKeys) {
            for (int i = 1; i <= 5; i++) {
                assertNotNull(areaKey + "_" + i, LessonCatalog.getLesson(areaKey + "_" + i));
            }
        }
        for (int i = 1; i <= 20; i++) {
            assertNotNull("mouth_" + i, LessonCatalog.getLesson("mouth_" + i));
        }

        AttemptMeasurementSet legacy = new AttemptMeasurementSet("existing-attempt", "breath_1",
                "completed", 10L, 20L, java.util.Collections.emptyList());
        assertTrue(legacy.sourceConfigurations.isEmpty());
        assertEquals("", legacy.selfAssessment);
        assertEquals(0, legacy.completedSteps);
        assertEquals(0, legacy.skippedSteps);
        assertEquals(0L, legacy.endedAtWallClockMs);
        assertTrue(legacy.sourceStatuses.isEmpty());
    }

    private static String privateString(String fieldName) throws Exception {
        Field field = CourseProgressStore.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
