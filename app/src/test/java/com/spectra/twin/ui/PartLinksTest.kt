package com.spectra.twin.ui

import com.spectra.twin.core.CameraCapability
import com.spectra.twin.core.DeviceCapabilities
import com.spectra.twin.render.ExternalPart
import org.junit.Assert.*
import org.junit.Test

class PartLinksTest {
    @Test fun externalPartsLinkOnlyToRelevantManualChecks() {
        assertEquals(listOf(DiagnosticKind.TOUCH, DiagnosticKind.COLORS), ExternalPart.SCREEN.relatedDiagnostics())
        assertEquals(listOf(DiagnosticKind.CAMERA), ExternalPart.MAIN_CAMERA.relatedDiagnostics())
        assertEquals(listOf(DiagnosticKind.BUTTONS), ExternalPart.VOLUME.relatedDiagnostics())
        assertTrue(ExternalPart.USB.relatedDiagnostics().isEmpty())
    }

    @Test fun cameraSummaryShowsGroupWithoutAssigningCameraIdToAnOptic() {
        val data = DeviceCapabilities(null, listOf(
            CameraCapability("0", "задняя", listOf("4000 × 3000"), null),
            CameraCapability("1", "фронтальная", null, null)
        ), null)
        assertTrue(cameraSummary(ExternalPart.MAIN_CAMERA, data).contains("ID 0"))
        assertEquals(cameraSummary(ExternalPart.MAIN_CAMERA, data), cameraSummary(ExternalPart.ULTRAWIDE, data))
        assertTrue(cameraSummary(ExternalPart.SELFIE, data).contains("недоступно"))
        assertTrue(cameraSummary(ExternalPart.SELFIE, null).contains("недоступно"))
    }
}
