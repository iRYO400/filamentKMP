package com.sadvakassov.filament.kmp

import com.sadvakassov.filament.kmp.scene.CardReducer
import com.sadvakassov.filament.kmp.scene.CardState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A3 feel — the interaction physics lives in [CardReducer] as pure functions, so we can verify
 * the "flick to spin, friction, catch to stop" behaviour without an engine or a device.
 */
class SharedCommonTest {

    private val dt = 1f / 60f // one 60fps frame

    /** Helper: simulate a press + steady horizontal drag, returning the state at release. */
    private fun draggedState(yawPerFrame: Float, frames: Int): CardState {
        var s = CardReducer.pressStart(CardState())
        repeat(frames) {
            // The gesture layer mutates yaw, then the frame step measures its speed.
            s = CardReducer.drag(s, dx = yawPerFrame / CardReducer.DRAG_SENSITIVITY, dy = 0f)
            s = CardReducer.step(s, dt)
        }
        return s
    }

    @Test
    fun drag_builds_yaw_velocity_for_a_flick() {
        val s = draggedState(yawPerFrame = 0.05f, frames = 8)
        // 0.05 rad per (1/60)s ≈ 3 rad/s, under the clamp.
        assertTrue(s.yawVelocity > 1f, "expected positive spin velocity, was ${s.yawVelocity}")
        assertTrue(s.yawVelocity <= CardReducer.MAX_SPIN, "velocity must respect MAX_SPIN")
    }

    @Test
    fun release_coasts_then_friction_brings_it_to_rest() {
        val released = draggedState(yawPerFrame = 0.05f, frames = 8).let(CardReducer::release)
        val yawAtRelease = released.yaw

        var s = released
        repeat(8) { s = CardReducer.step(s, dt) } // a few frames of inertia
        assertTrue(s.yaw > yawAtRelease, "card should keep spinning after release")

        repeat(600) { s = CardReducer.step(s, dt) } // ~10s later
        assertEquals(0f, s.yawVelocity, "friction must settle velocity to exactly 0")
    }

    @Test
    fun grabbing_a_spinning_card_catches_it() {
        val spinning = draggedState(yawPerFrame = 0.05f, frames = 8).let(CardReducer::release)
        assertTrue(abs(spinning.yawVelocity) > 0f)
        val caught = CardReducer.pressStart(spinning)
        assertEquals(0f, caught.yawVelocity, "press should kill inertia immediately")
    }

    @Test
    fun idle_state_is_stable() {
        var s = CardState()
        repeat(120) { s = CardReducer.step(s, dt) }
        assertEquals(CardState(), s, "an untouched card must not drift")
    }

    @Test
    fun pitch_recenters_after_release_but_holds_while_pressed() {
        val tilted = CardReducer.drag(CardReducer.pressStart(CardState()), dx = 0f, dy = 200f)
        val held = CardReducer.step(tilted, dt)
        assertEquals(tilted.pitch, held.pitch, "pitch is held while dragging")

        var s = CardReducer.release(tilted)
        repeat(300) { s = CardReducer.step(s, dt) }
        assertTrue(abs(s.pitch) < 1e-3f, "pitch should recenter to ~0 once released")
    }
}
