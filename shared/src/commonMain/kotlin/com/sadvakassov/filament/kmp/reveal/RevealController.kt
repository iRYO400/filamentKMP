package com.sadvakassov.filament.kmp.reveal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin StateFlow holder for the reveal choreography, mirroring
 * [com.sadvakassov.filament.kmp.scene.CardController]. The frame loop (in `RevealStage`) calls
 * [step] each frame until [RevealReducer.isInspect]; the renderer reads [state] each frame.
 */
class RevealController {
    private val _state = MutableStateFlow(RevealReducer.begin())
    val state: StateFlow<RevealState> = _state.asStateFlow()

    fun step(dtSeconds: Float) {
        _state.value = RevealReducer.step(_state.value, dtSeconds)
    }

    /** Replay from the top ("Open again") — no engine churn, just reset the state. */
    fun restart() {
        _state.value = RevealReducer.begin()
    }
}
