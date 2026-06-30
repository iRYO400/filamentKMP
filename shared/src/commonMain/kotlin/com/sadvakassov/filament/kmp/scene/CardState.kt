package com.sadvakassov.filament.kmp.scene

/**
 * Immutable snapshot of the card's visual state.
 *
 * This is the single contract between the shared layer and the platform renderers:
 * Compose gestures/UI produce new [CardState] values via [CardController]/[CardReducer],
 * and each platform renderer reads the latest snapshot once per frame and maps it onto
 * its scene (transform + material uniforms). Keeping it a plain immutable data class means
 * it is safe to read from a render thread (`StateFlow.value` is an atomic snapshot).
 *
 * Angles are in radians. In A1 the geometry is a placeholder; the same fields will drive
 * the Filament `TransformManager` (yaw/pitch/scale) and material uniforms later.
 */
data class CardState(
    /** Rotation around the vertical axis (turntable), radians. */
    val yaw: Float = 0f,
    /** Rotation around the horizontal axis (tilt), radians. Clamped by [CardReducer.MAX_PITCH]. */
    val pitch: Float = 0f,
    /** Uniform scale multiplier — used by the tap "pulse" feedback; eases back to 1. */
    val scale: Float = 1f,
    /** True while the user is actively dragging. Renderers may use it for feedback. */
    val isPressed: Boolean = false,
)
