package com.sadvakassov.filament.kmp

import com.sadvakassov.filament.kmp.app.AppReducer
import com.sadvakassov.filament.kmp.app.AppState
import com.sadvakassov.filament.kmp.app.Screen
import kotlin.test.Test
import kotlin.test.assertEquals

/** Navigation is pure data — these guard the back-stack transitions on commonTest. */
class AppReducerTest {

    @Test
    fun start_is_splash() {
        assertEquals(listOf(Screen.Splash), AppState().backStack)
    }

    @Test
    fun splash_complete_replaces_stack_with_home() {
        val s = AppReducer.splashComplete(AppState())
        assertEquals(listOf(Screen.Home), s.backStack)
    }

    @Test
    fun splash_complete_only_fires_from_splash() {
        val home = AppState(listOf(Screen.Home))
        assertEquals(home, AppReducer.splashComplete(home))
    }

    @Test
    fun open_pushes_reveal_only_from_home() {
        val home = AppState(listOf(Screen.Home))
        assertEquals(listOf(Screen.Home, Screen.Reveal), AppReducer.openReveal(home).backStack)
        // From splash it's a no-op (guarded).
        assertEquals(AppState(), AppReducer.openReveal(AppState()))
    }

    @Test
    fun back_pops_top_but_never_empties() {
        val onReveal = AppState(listOf(Screen.Home, Screen.Reveal))
        assertEquals(listOf(Screen.Home), AppReducer.back(onReveal).backStack)
        // Root is never popped.
        val root = AppState(listOf(Screen.Home))
        assertEquals(root, AppReducer.back(root))
    }

    @Test
    fun full_flow_splash_to_reveal_and_back() {
        var s = AppState()
        s = AppReducer.splashComplete(s)
        s = AppReducer.openReveal(s)
        assertEquals(Screen.Reveal, s.current)
        s = AppReducer.back(s)
        assertEquals(Screen.Home, s.current)
    }
}
