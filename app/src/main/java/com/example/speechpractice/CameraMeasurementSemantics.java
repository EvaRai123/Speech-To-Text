package com.example.speechpractice;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

final class CameraMeasurementSemantics {
    private static final Set<MeasurementType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(EnumSet.of(
                    MeasurementType.LIP_CLOSURE_RATIO,
                    MeasurementType.LIP_SEPARATION_RATIO,
                    MeasurementType.LIP_SPREAD_WIDTH_RATIO,
                    MeasurementType.LIP_ROUNDING_PROXY,
                    MeasurementType.MOUTH_OPENING_RATIO,
                    MeasurementType.LOWER_FACE_DISPLACEMENT_PROXY));

    private CameraMeasurementSemantics() {
    }

    static Set<MeasurementType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    static boolean isSupported(MeasurementType type) {
        return SUPPORTED_TYPES.contains(type);
    }

    static String unsupportedDetail(MeasurementType type) {
        if (type == MeasurementType.VISIBLE_LIP_TEETH_POSITION) {
            return "The detected facial contours cannot establish lip-to-teeth contact.";
        }
        if (type == MeasurementType.RESTING_LIP_POSITION) {
            return "The available contours do not establish a resting lip position.";
        }
        if (type == MeasurementType.LIP_JAW_TIMING) {
            return "This provider does not calculate timestamped lip-to-jaw coordination.";
        }
        if (type == MeasurementType.LIP_MOVEMENT_TRANSITION
                || type == MeasurementType.REPEATED_LIP_MOVEMENT
                || type == MeasurementType.LIP_SHAPE_CHANGE
                || type == MeasurementType.MOUTH_CONFIGURATION_CHANGE
                || type == MeasurementType.VISIBLE_MOUTH_MOVEMENT) {
            return "This provider does not calculate a temporal movement or shape-change measurement.";
        }
        if (type == MeasurementType.JAW_MOVEMENT) {
            return "The available contours cannot isolate jaw movement; only relative lower-face displacement is supported.";
        }
        return "The available facial contours do not support this measurement.";
    }

    static MeasurementRecord unavailableRecord(MeasurementType type) {
        return MeasurementRecord.unavailable(MeasurementSource.CAMERA, type,
                MeasurementStatus.UNSUPPORTED, unsupportedDetail(type));
    }
}
