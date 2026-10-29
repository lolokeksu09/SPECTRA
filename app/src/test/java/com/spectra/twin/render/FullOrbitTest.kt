package com.spectra.twin.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullOrbitTest {
    @Test fun fullVerticalTurnReturnsToStartingOrientation() {
        val start = FullOrbit.camera(0.4f, 0f, 4f, -4.0)
        val fullTurn = FullOrbit.camera(0.4f, (2.0 * PI).toFloat(), 4f, -4.0)
        assertEquals(start.eyeX, fullTurn.eyeX, 0.00001)
        assertEquals(start.eyeY, fullTurn.eyeY, 0.00001)
        assertEquals(start.eyeZ, fullTurn.eyeZ, 0.00001)
        assertEquals(start.upY, fullTurn.upY, 0.00001)
    }

    @Test fun cameraCanReachTopBottomAndUpsideDownPositions() {
        val top = FullOrbit.camera(0.3f, (PI / 2).toFloat(), 4f, -4.0)
        val bottom = FullOrbit.camera(0.3f, (-PI / 2).toFloat(), 4f, -4.0)
        val upsideDown = FullOrbit.camera(0f, PI.toFloat(), 4f, -4.0)
        assertTrue(top.eyeY > 3.99)
        assertTrue(bottom.eyeY < -3.99)
        assertTrue(upsideDown.upY < -0.99)
    }

    @Test fun upVectorNeverParallelsViewDirectionEvenAtPoles() {
        val r = 4.2
        for (yaw in listOf(-2.0, 0.0, 1.3, PI)) {
            for (pitch in listOf(-PI, -PI / 2, 0.0, PI / 2, PI)) {
                val pose = FullOrbit.camera(yaw.toFloat(), pitch.toFloat(), r.toFloat(), -4.0)
                val dx = pose.eyeX
                val dy = pose.eyeY
                val dz = pose.eyeZ + 4.0
                val dot = dx * pose.upX + dy * pose.upY + dz * pose.upZ
                val upLength = sqrt(pose.upX * pose.upX +
                    pose.upY * pose.upY + pose.upZ * pose.upZ)
                assertTrue("camera up/view must be orthogonal", abs(dot) < 0.0001)
                assertEquals(1.0, upLength, 0.0001)
            }
        }
    }

    @Test fun repeatedSpinsRemainFiniteAndUnrestricted() {
        var angle = 0f
        repeat(50_000) { angle = FullOrbit.wrap(angle + 0.41f) }
        assertTrue(angle.isFinite())
        assertTrue(angle in -PI.toFloat()..PI.toFloat())
        assertEquals(0f, FullOrbit.wrap(Float.NaN), 0f)
    }

    @Test fun halfTurnAroundHorizontalAxisFacesTheOppositeSide() {
        val rear = FullOrbit.camera(0f, 0f, 4f, -4.0)
        val front = FullOrbit.camera(PI.toFloat(), 0f, 4f, -4.0)
        assertEquals(-rear.eyeX, front.eyeX, 0.0001)
        assertEquals(-(rear.eyeZ + 4.0), front.eyeZ + 4.0, 0.0001)
        assertEquals(1.0, front.upY, 0.0001)
    }
}
