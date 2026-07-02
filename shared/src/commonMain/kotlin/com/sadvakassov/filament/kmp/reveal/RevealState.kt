package com.sadvakassov.filament.kmp.reveal

/** The beats of the box-open choreography, in order. [Inspect] is terminal (user-driven). */
enum class RevealPhase {
    Idle,          // gentle bob, settle-in
    Anticipation,  // shake ramps up
    Crack,         // seam light-leak grows
    Pop,           // quick scale overshoot
    Split,         // halves separate, box fades
    Rise,          // card fades in and rises out
    Spin,          // card auto-spins exactly one full turn
    Settle,        // ease to rest
    Inspect,       // hand off to CardController (drag / flick-to-spin)
}

/**
 * Immutable snapshot of the reveal choreography. Deliberately tiny: just the current [phase] and
 * seconds elapsed within it. All render channels are *derived* from this by
 * [RevealReducer.visuals], so both platform renderers stay dumb and identical.
 *
 * Mirrors [com.sadvakassov.filament.kmp.scene.CardState]: pure data, safe to read from a render
 * thread via `StateFlow.value`.
 */
data class RevealState(
    val phase: RevealPhase = RevealPhase.Idle,
    val phaseElapsed: Float = 0f,
)
