package com.spectra.twin.ui

import com.spectra.twin.core.DeviceCapabilities
import com.spectra.twin.render.ExternalPart

internal fun ExternalPart.relatedDiagnostics(): List<DiagnosticKind> = when (this) {
    ExternalPart.SCREEN -> listOf(DiagnosticKind.TOUCH, DiagnosticKind.COLORS)
    ExternalPart.SELFIE, ExternalPart.MAIN_CAMERA, ExternalPart.ULTRAWIDE -> listOf(DiagnosticKind.CAMERA)
    ExternalPart.SPEAKER -> listOf(DiagnosticKind.SPEAKER)
    ExternalPart.MICROPHONE -> listOf(DiagnosticKind.MIC)
    ExternalPart.VOLUME -> listOf(DiagnosticKind.BUTTONS)
    else -> emptyList()
}

internal fun cameraSummary(part: ExternalPart, data: DeviceCapabilities?): String {
    if (data == null || data.cameras == null) return "Android · камеры: недоступно"
    val facing = if (part == ExternalPart.SELFIE) "фронтальная" else "задняя"
    val found = data.cameras.filter { it.facing == facing }
    if (found.isEmpty()) return "Android · камеры (${facing}): не сообщаются"
    return found.joinToString("\n") {
        "Android · ID ${it.id}, ${it.facing}; максимальный JPEG: ${it.sizes?.firstOrNull() ?: "недоступно"}"
    }
}
