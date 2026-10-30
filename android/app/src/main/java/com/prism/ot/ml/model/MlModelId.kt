package com.prism.ot.ml.model

/**
 * Canonical identifiers for every model in the PRISM cognitive plane.
 * Used for asset lookup, status reporting, and error attribution.
 */
enum class MlModelId(val assetName: String, val displayName: String) {
    M01_DECODABILITY("m01_decodability_int8.tflite", "Decodability Oracle"),
    M02_LOCALISER("m02_localiser_int8.tflite", "Tile Localiser"),
    M03_SUPER_RESOLUTION("m03_sr_fp16.tflite", "Super-Resolution Restorer"),
    M04_CALIBRATOR("m04_calibrator.bin", "Chromatic Calibrator"),
    M05_CLASSIFIER("m05_classifier_int8.bin", "Shape-Colour Classifier"),
    M06_NEURAL_LDPC("m06_neural_ldpc.bin", "Neural Inner Decoder"),
    M07_FOUNTAIN_POLICY("m07_fountain_policy_int8.tflite", "Fountain Degree Optimiser"),
    M08_FACE_TRACKER("m08_face_tracker_int8.tflite", "Face Tracker"),
    M09_DEBLURRER("m09_deblurrer_fp16.tflite", "Motion Deblurrer"),
    M10_MOTOR_POLICY("m10_motor_policy.bin", "Motor Synchroniser"),
    M11_CHANNEL_TRANSFORMER("m11_channel_transformer_int8.tflite", "Channel State Transformer"),
    M12_DEFECT_INSPECTOR("m12_defect_inspector_int8.tflite", "Print Defect Inspector"),
    M13_CHANNEL_GENERATOR("m13_channel_generator_fp16.tflite", "Synthetic Channel Generator"),
    ;

    val isZeroOverhead: Boolean
        get() = this == M04_CALIBRATOR ||
            this == M05_CLASSIFIER ||
            this == M06_NEURAL_LDPC ||
            this == M10_MOTOR_POLICY

    val isLatencyCritical: Boolean
        get() = this == M01_DECODABILITY ||
            this == M05_CLASSIFIER ||
            this == M06_NEURAL_LDPC ||
            this == M11_CHANNEL_TRANSFORMER
}
