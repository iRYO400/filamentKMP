package com.sadvakassov.filament.kmp

import com.sadvakassov.filament.kmp.reveal.RevealPhase
import com.sadvakassov.filament.kmp.reveal.RevealReducer
import com.sadvakassov.filament.kmp.reveal.RevealState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The reveal choreography is pure data + time-stepping — guard its beats and derived channels. */
class RevealReducerTest {

    private val twoPi = (2.0 * PI).toFloat()

    /** Total time to reach Inspect = sum of all finite beat durations. */
    private fun totalFiniteDuration(): Float =
        RevealPhase.entries.map { RevealReducer.durationOf(it) }.filter { it.isFinite() }.sum()

    @Test
    fun begins_idle() {
        assertEquals(RevealPhase.Idle, RevealReducer.begin().phase)
        assertEquals(0f, RevealReducer.begin().phaseElapsed)
    }

    @Test
    fun crossing_a_beat_carries_the_remainder() {
        val idle = RevealReducer.durationOf(RevealPhase.Idle)
        val s = RevealReducer.step(RevealState(), idle + 0.1f)
        assertEquals(RevealPhase.Anticipation, s.phase)
        assertTrue(abs(s.phaseElapsed - 0.1f) < 1e-4f, "remainder should carry into next phase")
    }

    @Test
    fun a_single_big_step_rolls_through_multiple_beats() {
        // Idle + Anticipation + a bit lands in Crack.
        val d = RevealReducer.durationOf(RevealPhase.Idle) +
            RevealReducer.durationOf(RevealPhase.Anticipation) + 0.2f
        val s = RevealReducer.step(RevealState(), d)
        assertEquals(RevealPhase.Crack, s.phase)
        assertTrue(abs(s.phaseElapsed - 0.2f) < 1e-4f)
    }

    @Test
    fun reaches_inspect_and_is_absorbing() {
        var s = RevealReducer.step(RevealState(), totalFiniteDuration() + 0.5f)
        assertEquals(RevealPhase.Inspect, s.phase)
        assertTrue(RevealReducer.isInspect(s))
        // Further steps do nothing.
        val before = s
        s = RevealReducer.step(s, 1.0f)
        assertEquals(before, s)
    }

    @Test
    fun many_small_steps_reach_inspect_like_one_big_step() {
        var s = RevealState()
        repeat(1000) { s = RevealReducer.step(s, 0.016f) } // ~16s of 60fps frames
        assertEquals(RevealPhase.Inspect, s.phase)
    }

    @Test
    fun visuals_invariants_across_beats() {
        fun at(phase: RevealPhase, frac: Float) =
            RevealReducer.visuals(RevealState(phase, RevealReducer.durationOf(phase) * frac))

        // Idle: no glow, box intact.
        at(RevealPhase.Idle, 0.5f).let {
            assertEquals(0f, it.seamGlow)
            assertEquals(1f, it.boxOpacity)
            assertTrue(!it.cardVisible)
        }
        // End of Rise: card fully risen and visible.
        at(RevealPhase.Rise, 0.999f).let {
            assertTrue(it.cardVisible)
            assertTrue(abs(it.cardRise - 1f) < 0.02f)
        }
        // After Split (e.g. during Rise) the box is gone.
        assertEquals(0f, at(RevealPhase.Rise, 0f).boxOpacity)
        // End of Spin: one full turn.
        assertTrue(abs(at(RevealPhase.Spin, 0.999f).cardYaw - twoPi) < 0.05f)
        // Inspect: terminal flag set, card present.
        RevealReducer.visuals(RevealState(RevealPhase.Inspect, 0f)).let {
            assertTrue(it.inspect)
            assertTrue(it.cardVisible)
        }
    }
}
