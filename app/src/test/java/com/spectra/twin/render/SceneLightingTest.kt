package com.spectra.twin.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLightingTest {
    private val focusZ = -4.0

    @Test fun keyLightIlluminatesVisibleSurfacesAroundFullOrbit() {
        // Camera-facing normals must receive light from every view, including
        // the previously unlit -Y bottom cap at -90 degrees pitch.
        for (yaw in listOf(0.0, 0.8, PI, -PI / 2)) {
            for (pitch in listOf(-PI, -PI / 2, -0.3, 0.0, PI / 2, PI)) {
                val pose = FullOrbit.camera(yaw.toFloat(), pitch.toFloat(), 4.2f, focusZ)
                val dir = SceneLighting.keyDirection(pose, focusZ)
                val distance = sqrt(pose.eyeX * pose.eyeX + pose.eyeY * pose.eyeY +
                    (pose.eyeZ - focusZ) * (pose.eyeZ - focusZ))
                val surfaceToCameraX = pose.eyeX / distance
                val surfaceToCameraY = pose.eyeY / distance
                val surfaceToCameraZ = (pose.eyeZ - focusZ) / distance
                val diffuse = -(surfaceToCameraX * dir.x + surfaceToCameraY * dir.y +
                    surfaceToCameraZ * dir.z)
                assertTrue("Visible-facing surface needs light at yaw=$yaw pitch=$pitch",
                    diffuse > 0.95)
                assertTrue("Direction should be normalized", abs(sqrt(dir.x * dir.x +
                    dir.y * dir.y + dir.z * dir.z) - 1.0) < 0.001)
            }
        }
    }

    @Test fun bottomAndTopReceiveLightAtTheirRespectiveCameraAngles() {
        val below = SceneLighting.keyDirection(
            FullOrbit.camera(0f, (-PI / 2).toFloat(), 4.2f, focusZ), focusZ)
        val above = SceneLighting.keyDirection(
            FullOrbit.camera(0f, (PI / 2).toFloat(), 4.2f, focusZ), focusZ)
        assertTrue("Underside requires rays travelling upward", below.y > 0.95)
        assertTrue("Top edge requires rays travelling downward", above.y < -0.95)
    }
    @Test fun closeUpLightingPointsAtFocusedBottomEndNotSceneOrigin() {
        val preset = DeviceView.BOTTOM
        val pose = FullOrbit.camera(
            preset.filamentYaw(), preset.filamentPitch(),
            preset.filamentDistance(), focusZ, preset.filamentFocusY()
        )
        val light = SceneLighting.keyDirection(pose, focusZ)
        assertTrue("bottom face must be lit from below", light.y > .95f)
        val relativeY = pose.eyeY - pose.focusY
        val relativeZ = pose.eyeZ - focusZ
        val distance = sqrt(pose.eyeX*pose.eyeX + relativeY*relativeY + relativeZ*relativeZ)
        val ndotl = -(pose.eyeX*light.x + relativeY*light.y + relativeZ*light.z)/distance
        assertTrue("lighting must follow the focused part", ndotl > .95)
    }
}
