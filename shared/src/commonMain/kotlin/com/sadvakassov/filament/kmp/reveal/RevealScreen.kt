package com.sadvakassov.filament.kmp.reveal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sadvakassov.filament.kmp.scene.CardController
import kotlinx.coroutines.delay

/**
 * The full-bleed reveal screen. Owns one [RevealController] (choreography) + one [CardController]
 * (inspect physics), both remembered here so "Open again" resets them **in place** without
 * unmounting the Filament engine. [onBack] pops to Home, which unmounts [RevealScene] and destroys
 * the engine.
 *
 * Load smoothing (Phase-D4-lite): the native engine is created synchronously when [RevealStage]
 * mounts (a main-thread hitch + a blank first frame). To hide it, an opaque "Loading…" cover sits on
 * top and **cross-fades out** only once the renderer reports its first frame ([RevealScene]'s
 * `onReady`) **and** a minimum [MIN_LOADER_MS] have passed (so a fast load doesn't flash the loader).
 * The choreography clock is held until then, so the box-open always plays from the top on screen.
 */
@Composable
fun RevealScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val reveal = remember { RevealController() }
    val card = remember { CardController() }
    val state by reveal.state.collectAsState()

    var firstFrame by remember { mutableStateOf(false) }
    var minElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MIN_LOADER_MS)
        minElapsed = true
    }
    val ready = firstFrame && minElapsed

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0E0E14))) {
        RevealStage(
            reveal = reveal,
            card = card,
            running = ready,
            onReady = { firstFrame = true },
            modifier = Modifier.fillMaxSize(),
        )

        // Opaque cover over the surface until it's ready; cross-fades out to reveal the scene.
        AnimatedVisibility(
            visible = !ready,
            enter = fadeIn(),
            exit = fadeOut(tween(FADE_MS)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0E0E14)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB0B0C0),
                )
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).safeContentPadding().padding(16.dp),
        ) {
            Text("Back")
        }

        // "Open again" only once the card is out and inspectable.
        if (RevealReducer.isInspect(state)) {
            Button(
                onClick = { reveal.restart(); card.reset() },
                modifier = Modifier.align(Alignment.BottomCenter).safeContentPadding().padding(24.dp),
            ) {
                Text("Open again")
            }
        }
    }
}

/** Minimum time the loader stays up, so a fast engine start doesn't flash it in and out. */
private const val MIN_LOADER_MS = 400L
private const val FADE_MS = 300
