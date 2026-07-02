package com.sadvakassov.filament.kmp.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Thin StateFlow holder for app navigation, mirroring
 * [com.sadvakassov.filament.kmp.scene.CardController].
 *
 * The UI (App.kt) collects [state] and feeds `backStack` to Nav3's `NavDisplay`; Nav3 back events
 * route back in through [onBack]. This reducer stays the single source of truth — Nav3 never owns
 * a competing copy of the navigation state.
 */
class AppController {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun onSplashComplete() = _state.update(AppReducer::splashComplete)
    fun onOpen() = _state.update(AppReducer::openReveal)
    fun onBack() = _state.update(AppReducer::back)
}
