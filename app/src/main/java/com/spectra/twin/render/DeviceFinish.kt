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
    FRONT(-18f, -10f)
}

/** Physical GLB uses +Z for the rear and -Z for the screen. */
fun DeviceView.filamentYaw(): Float = when(this) {
    DeviceView.REAR -> 0f
    DeviceView.FRONT -> kotlin.math.PI.toFloat()
}
