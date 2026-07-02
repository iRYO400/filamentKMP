package com.sadvakassov.filament.kmp.app

/**
 * App-level navigation state — the back stack as plain data.
 *
 * This is Navigation 3's core idea ("the back stack is your state") expressed in the same
 * immutable-state + reducer style the rest of the app uses ([com.sadvakassov.filament.kmp.scene.CardState]).
 * The last element is the visible screen. Owned by [AppController]; only [AppReducer] mutates it.
 */
data class AppState(
    val backStack: List<Screen> = listOf(Screen.Splash),
) {
    /** The currently visible screen — the top of the back stack. */
    val current: Screen get() = backStack.last()
}
