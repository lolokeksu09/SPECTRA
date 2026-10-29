package com.spectra.twin.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A full 360-degree two-axis orbit around the model's centre.
 * The up-vector rotates with pitch, so passing directly over the poles
 * does not make Filament Camera.lookAt singular or suddenly flip.
 */
object FullOrbit {
    private val HALF_TURN = PI.toFloat()
    private val FULL_TURN = (2.0 * PI).toFloat()

    /** Keep accumulated gesture angles bounded without limiting rotation. */
    fun wrap(angle: Float): Float {
        if (!angle.isFinite()) return 0f
        val result = angle % FULL_TURN
        return when {
            result > HALF_TURN -> result - FULL_TURN
            result < -HALF_TURN -> result + FULL_TURN
            else -> result
        }
    }

    fun camera(yaw: Float, pitch: Float, radius: Float, focusZ: Double): OrbitPose {
        val horizontal = wrap(yaw).toDouble()
        val vertical = wrap(pitch).toDouble()
        val c = cos(vertical)
        val s = sin(vertical)
        val hSin = sin(horizontal)
        val hCos = cos(horizontal)
        val r = radius.coerceIn(2.9f, 8f).toDouble()
        return OrbitPose(
            eyeX = r * c * hSin,
            eyeY = r * s,
            eyeZ = focusZ + r * c * hCos,
            upX = -s * hSin,
            upY = c,
            upZ = -s * hCos
        )
    }
}

/** Camera coordinates are double precision because Filament's lookAt uses doubles. */
data class OrbitPose(
    val eyeX: Double,
    val eyeY: Double,
    val eyeZ: Double,
    val upX: Double,
    val upY: Double,
    val upZ: Double
)
