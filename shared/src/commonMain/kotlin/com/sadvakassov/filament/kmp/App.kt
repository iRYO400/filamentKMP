package com.sadvakassov.filament.kmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.sadvakassov.filament.kmp.app.AppController
import com.sadvakassov.filament.kmp.app.Screen
import com.sadvakassov.filament.kmp.reveal.RevealScreen
import com.sadvakassov.filament.kmp.ui.HomeScreen
import com.sadvakassov.filament.kmp.ui.SplashScreen

/**
 * Composition root. Hosts Navigation 3's [NavDisplay], driven by [AppController]'s back stack —
 * the single source of truth. Nav3 owns no competing navigation state: it renders our list and
 * routes its back events straight back into the reducer via [AppController.onBack].
 *
 * Each screen is mounted only while it's on the back stack, so the (future) Filament engine on the
 * Reveal screen is created on entry and destroyed on exit — lazy start/teardown for free.
 */
@Composable
@Preview
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val appController = remember { AppController() }
        val appState by appController.state.collectAsState()

        NavDisplay(
            backStack = appState.backStack,
            onBack = { appController.onBack() },
            entryProvider = { screen ->
                NavEntry(screen) {
                    when (screen) {
                        Screen.Splash -> SplashScreen(onDone = appController::onSplashComplete)
                        Screen.Home -> HomeScreen(onOpen = appController::onOpen)
                        Screen.Reveal -> RevealScreen(onBack = appController::onBack)
                    }
                }
            },
        )
    }
}
