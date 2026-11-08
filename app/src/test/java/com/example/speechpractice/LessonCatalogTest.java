package com.example.speechpractice;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LessonCatalogTest {
    @Test
    public void catalogContainsNineAreasAndExactlySixtyLessons() {
        assertEquals(9, LessonCatalog.getAreas().size());
        assertEquals(60, LessonCatalog.getLessonCount());

        Set<String> ids = new HashSet<>();
        for (LessonCatalog.LessonArea area : LessonCatalog.getAreas()) {
            assertEquals(area.key, "mouth_articulation".equals(area.key) ? 20 : 5, area.lessons.size());
            for (LessonDefinition lesson : area.lessons) {
                assertTrue(lesson.id, ids.add(lesson.id));
                assertNotNull(lesson.activityType);
                assertFalse(lesson.activityType.trim().isEmpty());
                assertFalse(lesson.instructions.trim().isEmpty());
                assertFalse(lesson.prompts.isEmpty());
                assertFalse(lesson.prompts.contains(""));
                assertEquals(area.key, lesson.areaKey);
                assertNotNull(lesson.id, LessonCatalog.getLesson(lesson.id));
                assertNotNull(lesson.measurementRequirements);
                assertNotNull(lesson.measurementRequirements.forSource(MeasurementSource.AUDIO));
                assertNotNull(lesson.measurementRequirements.forSource(MeasurementSource.ACCELEROMETER));
                assertNotNull(lesson.measurementRequirements.forSource(MeasurementSource.SEMG));
                assertNotNull(lesson.measurementRequirements.forSource(MeasurementSource.CAMERA));
                assertFalse(lesson.measurementRequirements.hasRequiredMeasurements());
            }
        }
        assertEquals(60, ids.size());
    }

    @Test
    public void originalFortyLessonIdsRemainInTheirOriginalOrder() {
        List<String> expected = new ArrayList<>();
        String[] originalAreas = {
                "breath", "phonation", "pitch", "loudness",
                "articulation", "syllables", "pacing", "connected"
        };
        for (String area : originalAreas) {
            for (int lessonNumber = 1; lessonNumber <= 5; lessonNumber++) {
                expected.add(area + "_" + lessonNumber);
            }
        }

        List<String> actual = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            for (LessonDefinition lesson : LessonCatalog.getAreas().get(i).lessons) {
                actual.add(lesson.id);
            }
        }
        assertEquals(expected, actual);
        for (String id : expected) assertNotNull(id, LessonCatalog.getLesson(id));
    }

    @Test
    public void mouthMovementAreaHasExactTitlesOrderAndGroups() {
        LessonCatalog.LessonArea area = LessonCatalog.getArea("mouth_articulation");
        assertNotNull(area);
        assertEquals("Mouth Movement and Articulation", area.title);
        String[] titles = {
                "Relaxed Lip Position", "Gentle Lip Closure", "Smile and Relax", "Lip Rounding",
                "Spread and Round", "Comfortable Mouth Opening", "Gentle Jaw Closing",
                "Small and Wide Openings", "Controlled Jaw Movement", "Lip and Jaw Coordination",
                "The Sounds P and B", "The Sound M", "Lip-to-Teeth Sounds F and V",
                "Combining Visible Lip Sounds", "Repeating Lip-Based Syllables", "Syllable Transitions",
                "Short Lip-Sound Words", "Words in Short Phrases",
                "Speaking with Natural Mouth Movement", "Everyday Speaking Practice"
        };
        String[] groups = {
                "Group A — Lip Mobility and Control",
                "Group B — Jaw Movement and Control",
                "Group C — Visible Speech Movements",
                "Group D — Mouth Movement in Speech"
        };
        assertEquals(titles.length, area.lessons.size());
        for (int i = 0; i < titles.length; i++) {
            LessonDefinition lesson = area.lessons.get(i);
            assertEquals("mouth_" + (i + 1), lesson.id);
            assertEquals(titles[i], lesson.title);
            assertEquals(groups[i / 5], lesson.groupTitle);
            assertFalse(lesson.educationalExplanation.trim().isEmpty());
        }
    }

    @Test
    public void mouthMovementCameraTypesAreOptionalAndDoNotClaimTongueOrPronunciationScoring() {
        for (int i = 1; i <= 20; i++) {
            LessonDefinition lesson = LessonCatalog.getLesson("mouth_" + i);
            LessonMeasurementRequirements.ChannelRequirement camera =
                    lesson.measurementRequirements.forSource(MeasurementSource.CAMERA);
            assertFalse(camera.limitation.trim().isEmpty());
            for (MeasurementType type : camera.relevantTypes) {
                assertTrue("Unsupported camera type mapped to " + lesson.id + ": " + type,
                        CameraMeasurementSemantics.isSupported(type)
                                || type == MeasurementType.VISIBLE_LIP_TEETH_POSITION);
            }
        }

        assertAreaNineCameraMapping("mouth_1");
        assertAreaNineCameraMapping("mouth_2", MeasurementType.LIP_CLOSURE_RATIO,
                MeasurementType.LIP_SEPARATION_RATIO);
        assertAreaNineCameraMapping("mouth_3", MeasurementType.LIP_SPREAD_WIDTH_RATIO);
        assertAreaNineCameraMapping("mouth_4", MeasurementType.LIP_ROUNDING_PROXY);
        assertAreaNineCameraMapping("mouth_5", MeasurementType.LIP_SPREAD_WIDTH_RATIO,
                MeasurementType.LIP_ROUNDING_PROXY);
        assertAreaNineCameraMapping("mouth_6", MeasurementType.MOUTH_OPENING_RATIO);
        assertAreaNineCameraMapping("mouth_7", MeasurementType.LOWER_FACE_DISPLACEMENT_PROXY);
        assertAreaNineCameraMapping("mouth_8", MeasurementType.MOUTH_OPENING_RATIO);
        assertAreaNineCameraMapping("mouth_9", MeasurementType.LOWER_FACE_DISPLACEMENT_PROXY);
        assertAreaNineCameraMapping("mouth_10");
        assertAreaNineCameraMapping("mouth_11", MeasurementType.LIP_CLOSURE_RATIO,
                MeasurementType.LIP_SEPARATION_RATIO);
        assertAreaNineCameraMapping("mouth_12", MeasurementType.LIP_CLOSURE_RATIO);
        assertAreaNineCameraMapping("mouth_13", MeasurementType.VISIBLE_LIP_TEETH_POSITION);
        assertAreaNineCameraMapping("mouth_14", MeasurementType.LIP_CLOSURE_RATIO,
                MeasurementType.LIP_SEPARATION_RATIO);
        for (int i = 15; i <= 20; i++) assertAreaNineCameraMapping("mouth_" + i);

        LessonMeasurementRequirements.ChannelRequirement tongueLessonCamera =
                LessonCatalog.getLesson("articulation_2").measurementRequirements
                        .forSource(MeasurementSource.CAMERA);
        assertEquals(LessonMeasurementRequirements.Need.NOT_APPLICABLE, tongueLessonCamera.need);
        assertFalse(tongueLessonCamera.cameraAssistanceApplicable);
        assertFalse(tongueLessonCamera.limitation.trim().isEmpty());
        for (MeasurementType type : MeasurementType.values()) {
            assertFalse(type.name().toLowerCase().contains("tongue"));
            assertFalse(type.name().toLowerCase().contains("pronunciation"));
        }
    }

    @Test
    public void pAndBCameraMappingMeasuresLipClosureAndReleaseOnly() {
        Set<MeasurementType> types = LessonCatalog.getLesson("mouth_11").measurementRequirements
                .forSource(MeasurementSource.CAMERA).relevantTypes;
        assertEquals(new HashSet<>(java.util.Arrays.asList(
                MeasurementType.LIP_CLOSURE_RATIO, MeasurementType.LIP_SEPARATION_RATIO)), types);
    }

    @Test
    public void mCameraMappingMeasuresLipClosureOnlyAndFvRetainsLipToTeethPosition() {
        Set<MeasurementType> mTypes = LessonCatalog.getLesson("mouth_12").measurementRequirements
                .forSource(MeasurementSource.CAMERA).relevantTypes;
        assertEquals(java.util.Collections.singleton(MeasurementType.LIP_CLOSURE_RATIO), mTypes);

        Set<MeasurementType> fvTypes = LessonCatalog.getLesson("mouth_13").measurementRequirements
                .forSource(MeasurementSource.CAMERA).relevantTypes;
        assertEquals(java.util.Collections.singleton(MeasurementType.VISIBLE_LIP_TEETH_POSITION), fvTypes);
        assertFalse(CameraMeasurementSemantics.isSupported(MeasurementType.VISIBLE_LIP_TEETH_POSITION));
    }

    @Test
    public void olderCameraEnabledLessonsOnlyRequestSupportedContourProxies() {
        assertEquals(new HashSet<>(java.util.Arrays.asList(
                        MeasurementType.LIP_CLOSURE_RATIO, MeasurementType.LIP_SEPARATION_RATIO)),
                LessonCatalog.getLesson("articulation_1").measurementRequirements
                        .forSource(MeasurementSource.CAMERA).relevantTypes);
        String[] nonVisualSequenceLessons = {
                "articulation_3", "articulation_4", "articulation_5",
                "syllables_1", "syllables_2", "syllables_3", "syllables_4", "syllables_5",
                "connected_1", "connected_2", "connected_3", "connected_4", "connected_5"
        };
        for (String id : nonVisualSequenceLessons) assertCameraTypes(id);
    }

    private static void assertCameraTypes(String lessonId) {
        LessonMeasurementRequirements.ChannelRequirement requirement =
                LessonCatalog.getLesson(lessonId).measurementRequirements
                        .forSource(MeasurementSource.CAMERA);
        assertEquals(LessonMeasurementRequirements.Need.NOT_APPLICABLE, requirement.need);
        assertTrue(requirement.relevantTypes.isEmpty());
        assertFalse(requirement.cameraAssistanceApplicable);
    }

    private static void assertAreaNineCameraMapping(String lessonId, MeasurementType... expectedTypes) {
        LessonMeasurementRequirements.ChannelRequirement requirement =
                LessonCatalog.getLesson(lessonId).measurementRequirements
                        .forSource(MeasurementSource.CAMERA);
        Set<MeasurementType> expected = expectedTypes.length == 0
                ? java.util.Collections.emptySet()
                : new HashSet<>(java.util.Arrays.asList(expectedTypes));
        assertEquals(expected, requirement.relevantTypes);
        assertEquals(expected.isEmpty() ? LessonMeasurementRequirements.Need.NOT_APPLICABLE
                        : LessonMeasurementRequirements.Need.OPTIONAL,
                requirement.need);
    }
}
