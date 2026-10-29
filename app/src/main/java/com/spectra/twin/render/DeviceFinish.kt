package com.spectra.twin.render

/** A visual preset only; Android-reported hardware remains untouched. */
enum class DeviceFinish(val id: String, val title: String) {
    WHITE("white", "Белый"),
    BLACK("black", "Чёрный");

    companion object {
        fun fromId(id: String?): DeviceFinish = entries.firstOrNull { it.id == id } ?: WHITE
    }
}

/** Camera bookmarks. Gestures remain free after selecting a bookmark. */
enum class DeviceView(val yaw: Float, val pitch: Float) {
    REAR(155f, -11f),
    FRONT(-18f, -10f),
    BOTTOM(0f, -90f),
    TOP(0f, 90f)
}

/** Physical GLB uses +Z for the rear and -Z for the screen. */
fun DeviceView.filamentYaw(): Float = when(this) {
    DeviceView.REAR -> 0f
    DeviceView.FRONT -> kotlin.math.PI.toFloat()
    DeviceView.BOTTOM, DeviceView.TOP -> 0f
}

/**
 * A small diagonal angle exposes the chassis lip as well as its end face.
 * FullOrbit supplies the moving up vector, so a direct top/bottom view is safe.
 */
fun DeviceView.filamentPitch(): Float = when (this) {
    DeviceView.REAR, DeviceView.FRONT -> -.10f
    DeviceView.BOTTOM -> -1.47f
    DeviceView.TOP -> 1.47f
}

/** Normalised GLB length is ~2 after transformToUnitCube(). */
fun DeviceView.filamentFocusY(): Double = when (this) {
    DeviceView.BOTTOM -> -.88
    DeviceView.TOP -> .88
    DeviceView.REAR, DeviceView.FRONT -> 0.0
}

fun DeviceView.filamentDistance(): Float = when (this) {
    DeviceView.BOTTOM, DeviceView.TOP -> 1.95f
    DeviceView.REAR, DeviceView.FRONT -> 4.2f
}
