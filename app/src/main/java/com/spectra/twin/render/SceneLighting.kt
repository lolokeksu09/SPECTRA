package com.spectra.twin.render

import kotlin.math.sqrt

/** Direction in which Filament's primary directional light rays travel. */
internal data class LightRay(val x: Float, val y: Float, val z: Float)

internal object SceneLighting {
    // Slightly screen-up-biased headlight, avoiding flat full-frontal illumination.
    // One dominant directional light works with Filament's default engine config.
    private const val SCREEN_UP_BIAS = 0.27

    fun keyDirection(camera: OrbitPose, focusZ: Double): LightRay {
        val dx = -camera.eyeX
        val dy = -camera.eyeY
        val dz = focusZ - camera.eyeZ
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        val offset = SCREEN_UP_BIAS * distance
        val x = dx - camera.upX * offset
        val y = dy - camera.upY * offset
        val z = dz - camera.upZ * offset
        val length = sqrt(x * x + y * y + z * z)
        return LightRay((x / length).toFloat(), (y / length).toFloat(), (z / length).toFloat())
    }
}
