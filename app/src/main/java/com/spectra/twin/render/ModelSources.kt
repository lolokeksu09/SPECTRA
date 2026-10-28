package com.spectra.twin.render

/**
 * Offline asset selection for the two user-supplied Legion Y70 finishes.
 * An imported URI takes precedence when present; the file picker is used
 * only if a built-in black asset has not yet been packaged into the APK.
 */
object ModelSources {
    const val WHITE_ASSET = "models/legion_y70_white.glb"
    const val BLACK_ASSET = "models/legion_y70_black.glb"

    fun builtinAsset(finish: DeviceFinish): String =
        if (finish == DeviceFinish.BLACK) BLACK_ASSET else WHITE_ASSET

    fun needsBlackImport(finish: DeviceFinish, hasBundledBlack: Boolean, blackUri: String?): Boolean =
        finish == DeviceFinish.BLACK && !hasBundledBlack && blackUri.isNullOrBlank()
}
