package com.spectra.twin.render

import org.junit.Assert.*
import org.junit.Test

class ViewerMotionTest {
    @Test fun manyRotationsStayBounded() {
        var angle = 0f
        repeat(10000) { angle = ViewerMotion.yaw(angle, 34f) }
        assertTrue(angle in -180f..180f)
        assertEquals(0f, ViewerMotion.yaw(170f, 190f), .0001f)
    }

    @Test fun illegalZoomDoesNotChangeDistance() {
        assertEquals(4.7f, ViewerMotion.zoom(4.7f, Float.NaN), .0001f)
        assertEquals(4.7f, ViewerMotion.zoom(4.7f, 0f), .0001f)
        assertEquals(4.7f, ViewerMotion.zoom(4.7f, -1f), .0001f)
    }

    @Test fun pinchDistanceAlwaysClamped() {
        assertEquals(3.2f, ViewerMotion.zoom(4.7f, 10f), .0001f)
        assertEquals(8f, ViewerMotion.zoom(4.7f, .01f), .0001f)
    }

    @Test fun pitchAndInvalidInputHandled() {
        assertEquals(75f, ViewerMotion.pitch(70f, 100f), .0001f)
        assertEquals(-75f, ViewerMotion.pitch(-70f, -100f), .0001f)
        assertEquals(0f, ViewerMotion.yaw(12f, Float.NaN), .0001f)
    }
}
