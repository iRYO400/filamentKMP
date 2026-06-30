package com.sadvakassov.filament.kmp.scene

import com.sadvakassov.filament.kmp.math.toRadians
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The single source of truth for the card, shared by UI and renderer.
 *
 * - Compose UI and gesture handlers call the input methods.
 * - The per-frame loop (see `CardStage`) calls [tick] every frame.
 * - Platform renderers read [state] (`.value` is a safe atomic snapshot from any thread).
 *
 * It holds no platform or engine references, so it is fully shared and survives swapping the
 * rendering backend (placeholder → Filament) without changes.
 */
class CardController {
    private val _state = MutableStateFlow(CardState())
    val state: StateFlow<CardState> = _state.asStateFlow()

    // --- Gesture input (from Compose pointerInput) ---
    fun onPressStart() = _state.update(CardReducer::pressStart)
    fun onDrag(dx: Float, dy: Float) = _state.update { CardReducer.drag(it, dx, dy) }
    fun onRelease() = _state.update(CardReducer::release)
    fun onTap() = _state.update(CardReducer::tap)

    // --- UI input (buttons/sliders) — demonstrates UI → 3D direction ---
    fun nudgeYaw(degrees: Float) = _state.update { CardReducer.nudgeYaw(it, degrees.toRadians()) }
    fun reset() { _state.value = CardReducer.reset() }

    // --- Time stepping (per-frame) ---
    fun tick(dtSeconds: Float) = _state.update { CardReducer.step(it, dtSeconds) }
}
