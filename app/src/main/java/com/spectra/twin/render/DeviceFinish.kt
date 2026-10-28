package com.spectra.twin.render

/** A visual preset only; Android-reported hardware remains untouched. */
enum class DeviceFinish(val id: String, val title: String) {
    WHITE("white", "Белый"),
    BLACK("black", "Чёрный");

    companion object {
        fun fromId(id: String?): DeviceFinish = entries.firstOrNull { it.id == id } ?: WHITE
    }
}
