package com.spectra.twin.render

import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLightingTest {
    @Test fun undersideReceivesDiffuseFillAtLowViewingAngles() {
        val fill = SceneLighting.underside
        val length = sqrt(fill.directionX * fill.directionX +
            fill.directionY * fill.directionY + fill.directionZ * fill.directionZ)
        // Filament light direction is the direction of travel; the surface
        // normal of the bottom end cap is (0, -1, 0).
        val bottomDiffuse = fill.directionY / length
        assertTrue("The bottom end cap must receive fill light", bottomDiffuse > 0.90f)
        assertTrue("Fill should remain secondary to the main key light",
            fill.intensityLux in 10_000f..35_000f)
    }
}
