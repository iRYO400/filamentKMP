package com.sadvakassov.filament.kmp.scene

import com.sadvakassov.filament.kmp.math.approach

/**
 * All [CardState] transitions live here as pure functions — no engine, no Compose, no I/O.
 *
 * Two kinds of transitions:
 *  - input events ([pressStart], [drag], [release], [tap], [nudgeYaw], [reset])
 *  - time stepping ([step]) driven by the per-frame loop for inertia/spring behaviour.
 *
 * Being pure makes the whole interaction model trivially unit-testable on commonTest and
 * keeps [CardController] a thin StateFlow holder. Tunables are exposed as constants so the
 * "feel" can be adjusted in one place.
 */
object CardReducer {
    /** Radians of rotation per pixel of drag. */
    const val DRAG_SENSITIVITY: Float = 0.012f
    /** Max tilt away from neutral, radians (~34°). */
    const val MAX_PITCH: Float = 0.6f
    /** Extra scale applied on tap, eased back to 1. */
    const val TAP_PULSE: Float = 0.14f

    /** How fast pitch recenters to 0 after release (1/seconds). */
    private const val RECENTER_SPEED: Float = 6f
    /** How fast the tap pulse relaxes back to scale 1 (1/seconds). */
    private const val SCALE_SPEED: Float = 10f

    fun pressStart(s: CardState): CardState = s.copy(isPressed = true)

    fun drag(s: CardState, dx: Float, dy: Float): CardState = s.copy(
        yaw = s.yaw + dx * DRAG_SENSITIVITY,
        pitch = (s.pitch + dy * DRAG_SENSITIVITY).coerceIn(-MAX_PITCH, MAX_PITCH),
    )

    fun release(s: CardState): CardState = s.copy(isPressed = false)

    fun tap(s: CardState): CardState = s.copy(scale = 1f + TAP_PULSE)

    fun nudgeYaw(s: CardState, deltaRadians: Float): CardState = s.copy(yaw = s.yaw + deltaRadians)

    fun reset(): CardState = CardState()

    /** Advance time by [dt] seconds: recenter pitch when released, relax tap pulse. */
    fun step(s: CardState, dt: Float): CardState = s.copy(
        pitch = if (s.isPressed) s.pitch else approach(s.pitch, 0f, RECENTER_SPEED, dt),
        scale = approach(s.scale, 1f, SCALE_SPEED, dt),
    )
}
