package com.sadvakassov.filament.kmp.reveal

import kotlin.math.PI
import kotlin.math.sin

/**
 * All [RevealState] transitions + the derived render channels, as pure functions — no engine, no
 * Compose, no I/O. Mirrors [com.sadvakassov.filament.kmp.scene.CardReducer]: time-stepping in
 * [step], everything unit-testable on commonTest. Every knob is a named constant so the "feel"
 * lives in one place.
 */
object RevealReducer {
    // --- Beat durations (seconds). The whole choreography's timing lives here. ---
    private const val IDLE: Float = 0.6f
    private const val ANTICIPATION: Float = 0.5f
    private const val CRACK: Float = 0.6f
    private const val POP: Float = 0.25f
    private const val SPLIT: Float = 0.5f
    private const val RISE: Float = 0.6f
    private const val SPIN: Float = 0.9f
    private const val SETTLE: Float = 0.3f

    // --- Visual tunables ---
    private const val BOB_FREQ: Float = 3.0f
    private const val BOB_AMP: Float = 0.03f
    private const val SHAKE_FREQ: Float = 42.0f
    private const val SHAKE_AMP: Float = 0.05f
    private const val POP_OVERSHOOT: Float = 0.18f
    private val TWO_PI: Float = (2.0 * PI).toFloat()
    private val HALF_PI_BUMP: Float = PI.toFloat() // sin bump for the pop overshoot

    /** Duration of a phase; [RevealPhase.Inspect] is terminal (infinite). */
    fun durationOf(phase: RevealPhase): Float = when (phase) {
        RevealPhase.Idle -> IDLE
        RevealPhase.Anticipation -> ANTICIPATION
        RevealPhase.Crack -> CRACK
        RevealPhase.Pop -> POP
        RevealPhase.Split -> SPLIT
        RevealPhase.Rise -> RISE
        RevealPhase.Spin -> SPIN
        RevealPhase.Settle -> SETTLE
        RevealPhase.Inspect -> Float.POSITIVE_INFINITY
    }

    fun begin(): RevealState = RevealState()

    fun isInspect(s: RevealState): Boolean = s.phase == RevealPhase.Inspect

    /**
     * Advance time by [dt] seconds, rolling through as many beats as [dt] covers and **carrying the
     * remainder** into the next phase so timing never drifts on frame boundaries. [RevealPhase.Inspect]
     * is absorbing. [dt] is clamped upstream (0.05, like `CardStage`).
     */
    fun step(s: RevealState, dt: Float): RevealState {
        if (dt <= 0f || s.phase == RevealPhase.Inspect) return s
        var phase = s.phase
        var elapsed = s.phaseElapsed + dt
        while (phase != RevealPhase.Inspect) {
            val d = durationOf(phase)
            if (!d.isFinite() || elapsed < d) break
            elapsed -= d
            phase = RevealPhase.entries[phase.ordinal + 1]
        }
        return s.copy(phase = phase, phaseElapsed = elapsed)
    }

    /** Pure mapping from (phase, elapsed) to render channels. Both renderers read this each frame. */
    fun visuals(s: RevealState): RevealVisuals {
        val phase = s.phase
        val d = durationOf(phase)
        val p = if (d.isFinite() && d > 0f) (s.phaseElapsed / d).coerceIn(0f, 1f) else 1f
        val t = s.phaseElapsed
        val ord = phase.ordinal

        // Box body: split + fade happen from Split onward.
        val (boxSplit, boxOpacity) = when {
            ord < RevealPhase.Split.ordinal -> 0f to 1f
            phase == RevealPhase.Split -> smoothstep(p) to (1f - p)
            else -> 1f to 0f
        }

        // Seam light-leak: grows in Crack, holds through Pop, dies as the box opens in Split.
        val seamGlow = when {
            ord < RevealPhase.Crack.ordinal -> 0f
            phase == RevealPhase.Crack -> smoothstep(p)
            phase == RevealPhase.Pop -> 1f
            phase == RevealPhase.Split -> 1f - p
            else -> 0f
        }

        // Card: visible + fully risen from Rise onward; rises during Rise.
        val cardVisible = ord >= RevealPhase.Rise.ordinal
        val cardRise = when {
            ord < RevealPhase.Rise.ordinal -> 0f
            phase == RevealPhase.Rise -> smoothstep(p)
            else -> 1f
        }

        // Auto-spin: exactly one turn during Spin. Before/after it's 0 (2π ≡ 0 → seamless handoff).
        val cardYaw = if (phase == RevealPhase.Spin) smoothstep(p) * TWO_PI else 0f

        return RevealVisuals(
            boxBobY = if (phase == RevealPhase.Idle) sin(t * BOB_FREQ) * BOB_AMP else 0f,
            shakeX = if (phase == RevealPhase.Anticipation) sin(t * SHAKE_FREQ) * SHAKE_AMP * p else 0f,
            boxScale = if (phase == RevealPhase.Pop) 1f + sin(p * HALF_PI_BUMP) * POP_OVERSHOOT else 1f,
            boxSplit = boxSplit,
            seamGlow = seamGlow,
            boxOpacity = boxOpacity,
            cardVisible = cardVisible,
            cardRise = cardRise,
            cardYaw = cardYaw,
            inspect = phase == RevealPhase.Inspect,
        )
    }

    /** Smoothstep easing (ease-in-out), 0..1. */
    private fun smoothstep(p: Float): Float = p * p * (3f - 2f * p)
}
