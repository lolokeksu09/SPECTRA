package com.spectra.twin.render

/** Filament's direction points along the emitted light rays, not toward the lamp. */
internal data class DirectionalLightSpec(
    val directionX: Float,
    val directionY: Float,
    val directionZ: Float,
    val intensityLux: Float
)

internal object SceneLighting {
    // The phone's bottom edge faces -Y, so upward-travelling rays (+Y)
    // are required to illuminate it; top-only lights leave the edge black.
    val underside = DirectionalLightSpec(
        directionX = 0.06f,
        directionY = 0.95f,
        directionZ = -0.26f,
        intensityLux = 26_000f
    )
}
