package com.sadvakassov.filament.kmp.reveal

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.sadvakassov.filament.kmp.scene.CardController
import kotlinx.coroutines.isActive

/**
 * Hosts [RevealScene] and owns the one continuous frame loop for the whole reveal, fusing the two
 * regimes:
 *  - **choreography** (pre-inspect): ticks [RevealController.step] to advance the beats.
 *  - **inspect**: hands the clock to [CardController.tick] so the A3 spring/inertia takes over.
 *
 * Gestures are captured here (shared, not in the native view) but **gated on inspect** — drags do
 * nothing while the box is opening, then engage flick-to-spin once the card is free. This reuses
 * the exact `CardStage` gesture wiring.
 *
 * The choreography clock only advances while [running] is true — the host keeps it false until the
 * engine is ready and the loading cover has faded, so the animation always plays from the top once
 * the user can actually see it (rather than silently advancing behind the loader).
 */
@Composable
fun RevealStage(
    reveal: RevealController,
    card: CardController,
    running: Boolean,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (running) {
        LaunchedEffect(reveal, card) {
            var last = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
                        if (RevealReducer.isInspect(reveal.state.value)) card.tick(dt) else reveal.step(dt)
                    }
                    last = now
                }
            }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(reveal, card) {
                detectTapGestures(onTap = {
                    if (RevealReducer.isInspect(reveal.state.value)) card.onTap()
                })
            }
            .pointerInput(reveal, card) {
                detectDragGestures(
                    onDragStart = { if (RevealReducer.isInspect(reveal.state.value)) card.onPressStart() },
                    onDragEnd = { if (RevealReducer.isInspect(reveal.state.value)) card.onRelease() },
                    onDragCancel = { if (RevealReducer.isInspect(reveal.state.value)) card.onRelease() },
                    onDrag = { change, drag ->
                        if (RevealReducer.isInspect(reveal.state.value)) {
                            change.consume()
                            card.onDrag(drag.x, drag.y)
                        }
                    },
                )
            },
    ) {
        RevealScene(reveal, card, onReady, Modifier.fillMaxSize())
    }
}
