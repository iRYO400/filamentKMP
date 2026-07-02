package com.sadvakassov.filament.kmp.app

/**
 * Pure navigation transitions, mirroring [com.sadvakassov.filament.kmp.scene.CardReducer]'s
 * discipline: no Compose, no side effects, trivially unit-testable on commonTest.
 *
 * Transitions are guarded so an event fired from the wrong screen is a no-op (defensive against
 * double-taps / races), keeping the back stack always valid.
 */
object AppReducer {
    /** Splash finished → replace the stack with Home (splash is not kept on the back stack). */
    fun splashComplete(s: AppState): AppState =
        if (s.current == Screen.Splash) AppState(listOf(Screen.Home)) else s

    /** Home → push the Reveal screen on top. */
    fun openReveal(s: AppState): AppState =
        if (s.current == Screen.Home) s.copy(backStack = s.backStack + Screen.Reveal) else s

    /** Pop the top screen (no-op when only the root remains, so the stack is never empty). */
    fun back(s: AppState): AppState =
        if (s.backStack.size > 1) s.copy(backStack = s.backStack.dropLast(1)) else s
}
