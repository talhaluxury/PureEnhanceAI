package com.pureenhance.ai.ai.models

/** File names under assets/models/ (or filesDir/models/ for side-loaded files). See README.md > Models. */
object ModelSpecs {
    /** Real-ESRGAN "general-x4v3" (SRVGGNetCompact), BSD-3-Clause, exported with a static 128×128 input. */
    const val SR_FILE = "realesr_general_x4v3.onnx"
    const val SR_SCALE = 4
    const val TILE = 128

    /** GFPGAN v1.4 clean architecture exported with a static 512×512 input. */
    const val FACE_FILE = "gfpgan_v1_4.onnx"
    const val FACE_SIZE = 512
}
