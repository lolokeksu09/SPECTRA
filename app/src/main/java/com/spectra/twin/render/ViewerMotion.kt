package com.spectra.twin.render

/**
 * Small, pure guard for gestures. Prevents invalid multi-touch scales and
 * unbounded yaw accumulation from destabilising the 3D camera over time.
 */
object ViewerMotion {
    fun yaw(current: Float, delta: Float): Float {
        if (!current.isFinite() || !delta.isFinite()) return 0f
        val angle = (current + delta) % 360f
        return when {
            angle > 180f -> angle - 360f
            angle < -180f -> angle + 360f
            else -> angle
        }
    }

    fun pitch(current: Float, delta: Float): Float {
        if (!current.isFinite() || !delta.isFinite()) return 0f
        return (current + delta).coerceIn(-75f, 75f)
    }

    fun zoom(current: Float, factor: Float): Float {
        if (!current.isFinite()) return 4.7f
        if (!factor.isFinite() || factor <= 0f) return current.coerceIn(3.2f,8f)
        return (current / factor).coerceIn(3.2f,8f)
    }
}
