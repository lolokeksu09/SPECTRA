package com.spectra.twin.ui

internal enum class PassportFocus(val heading: String, val field: String) {
    ALL("", ""),
    CAMERAS("Камеры, доступные Android", "Android · Камеры"),
    DISPLAY("Режимы дисплея Android", "Android · Режимы дисплея")
}
