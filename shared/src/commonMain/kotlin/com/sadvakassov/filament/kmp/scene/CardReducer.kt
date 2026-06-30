package com.sadvakassov.filament.kmp.scene

import com.sadvakassov.filament.kmp.math.approach
import kotlin.math.abs

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

    /** Upper bound on flick speed (rad/s) so a fast swipe can't launch the card into a blur. */
    const val MAX_SPIN: Float = 12f
    /** How fast yaw inertia bleeds off after release (1/seconds): higher = stops sooner. */
    private const val SPIN_FRICTION: Float = 2.6f
    /** Below this |velocity| (rad/s) inertia snaps to rest, so the card doesn't crawl forever. */
    private const val MIN_SPIN: Float = 0.05f

    fun pressStart(s: CardState): CardState =
        // Grabbing the card catches it: stop any inertia and seed the velocity estimator at the
        // current angle so the first frame of dragging can't read a bogus jump as huge speed.
        s.copy(isPressed = true, yawVelocity = 0f, prevYaw = s.yaw)

    fun drag(s: CardState, dx: Float, dy: Float): CardState = s.copy(
        yaw = s.yaw + dx * DRAG_SENSITIVITY,
        pitch = (s.pitch + dy * DRAG_SENSITIVITY).coerceIn(-MAX_PITCH, MAX_PITCH),
    )

    fun release(s: CardState): CardState = s.copy(isPressed = false)

    fun tap(s: CardState): CardState = s.copy(scale = 1f + TAP_PULSE)

    fun nudgeYaw(s: CardState, deltaRadians: Float): CardState = s.copy(yaw = s.yaw + deltaRadians)

    fun reset(): CardState = CardState()

    /**
     * Advance time by [dt] seconds. Two regimes:
     *  - **pressed:** the finger owns yaw, so we only *measure* how fast it's turning (for the
     *    eventual flick) and recompute [CardState.prevYaw]; pitch is held, the pulse still relaxes.
     *  - **released:** yaw coasts on [CardState.yawVelocity] with exponential friction, pitch
     *    recenters to 0, the pulse relaxes.
     */
    fun step(s: CardState, dt: Float): CardState {
        if (dt <= 0f) return s
        val scale = approach(s.scale, 1f, SCALE_SPEED, dt)
        return if (s.isPressed) {
            val measured = ((s.yaw - s.prevYaw) / dt).coerceIn(-MAX_SPIN, MAX_SPIN)
            s.copy(scale = scale, yawVelocity = measured, prevYaw = s.yaw)
        } else {
            val decayed = approach(s.yawVelocity, 0f, SPIN_FRICTION, dt)
            val velocity = if (abs(decayed) < MIN_SPIN) 0f else decayed
            val yaw = s.yaw + velocity * dt
            s.copy(
                yaw = yaw,
                pitch = approach(s.pitch, 0f, RECENTER_SPEED, dt),
                scale = scale,
                yawVelocity = velocity,
                prevYaw = yaw,
            )
        }
    }
}
