package com.sadvakassov.filament.kmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.sadvakassov.filament.kmp.scene.CardController
import com.sadvakassov.filament.kmp.scene.CardStage
import com.sadvakassov.filament.kmp.ui.CardHud

/**
 * Root of the demo. Owns the single [CardController] and composes:
 *  - [CardStage]  — the 3D surface + shared gestures + frame loop,
 *  - [CardHud]    — the Compose overlay bound to the same state.
 *
 * Everything here is shared (commonMain); only [CardStage]'s `CardScene` differs per platform.
 */
@Composable
@Preview
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val controller = remember { CardController() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0E14)),
        ) {
            CardStage(controller, Modifier.fillMaxSize())
            CardHud(
                controller = controller,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeContentPadding(),
            )
        }
    }
}
