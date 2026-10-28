package com.spectra.twin.core

import org.junit.Assert.*
import org.junit.Test
import com.spectra.twin.render.DeviceFinish

class MetricsTest {
    @Test fun batteryRejectsUnknownScaleAndInvalidLevels() {
        assertNull(Metrics.batteryPercent(-1, 100))
        assertNull(Metrics.batteryPercent(88, 0))
        assertNull(Metrics.batteryPercent(120, 100))
    }
    @Test fun batteryScalesCorrectlyAndRounds() {
        assertEquals(50, Metrics.batteryPercent(5, 10))
        assertEquals(33, Metrics.batteryPercent(1, 3))
        assertEquals(100, Metrics.batteryPercent(10, 10))
    }
    @Test fun missingAndInvalidMetricIsNotZero() {
        assertEquals("Недоступно", Metrics.temperature(null))
        assertEquals("Недоступно", Metrics.temperature(Double.NaN))
        assertEquals("Недоступно", Metrics.hz(-1f))
        assertEquals("Недоступно", Metrics.percentLabel(null))
    }
    @Test fun memoryRejectsImpossibleInputs() {
        assertNull(Metrics.percent(110, 100))
        assertNull(Metrics.percent(-1, 100))
        assertNull(Metrics.percent(0, 0))
        assertEquals(25, Metrics.percent(75, 100))
    }
    @Test fun thermalStatusIsSeverityNotTemperature() {
        assertEquals("Норма", Metrics.thermalStatus(0))
        assertEquals("Критический нагрев", Metrics.thermalStatus(4))
        assertEquals("Недоступно", Metrics.thermalStatus(null))
        assertEquals("Недоступно", Metrics.thermalStatus(90))
    }
    @Test fun visualFinishDefaultsToWhiteAndHandlesInvalidSavedIds() {
        assertEquals(DeviceFinish.WHITE, DeviceFinish.fromId(null))
        assertEquals(DeviceFinish.WHITE, DeviceFinish.fromId("unknown"))
        assertEquals(DeviceFinish.WHITE, DeviceFinish.fromId("white"))
        assertEquals(DeviceFinish.BLACK, DeviceFinish.fromId("black"))
    }
    @Test fun cameraBookmarksFaceOppositeSides() {
        val rear = com.spectra.twin.render.DeviceView.REAR
        val front = com.spectra.twin.render.DeviceView.FRONT
        assertTrue(kotlin.math.abs(rear.yaw - front.yaw) > 150f)
        assertTrue(rear.pitch in -75f..75f)
        assertTrue(front.pitch in -75f..75f)
    }
}
