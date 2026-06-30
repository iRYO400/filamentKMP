package com.sadvakassov.filament.kmp.scene

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive

/**
 * Hosts the [CardScene] and owns the two shared concerns of the interaction pipeline:
 *
 *  1. **Gestures** — captured here (not in the native view) so one [CardController] drives
 *     both the 3D surface and the Compose UI. drag → rotate, tap → pulse.
 *  2. **Frame loop** — `withFrameNanos` ticks [CardController.tick] every frame for the
 *     spring/recenter physics. This is the shared clock; the native renderer additionally
 *     has its own vsync loop (Choreographer / CADisplayLink) for drawing.
 *
 * The card keeps a fixed portrait aspect ratio; gestures are captured over the whole stage.
 */
@Composable
fun CardStage(controller: CardController, modifier: Modifier = Modifier) {
    LaunchedEffect(controller) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000.0).toFloat()
                    // clamp to avoid a big jump after a stall (e.g. returning from background)
                    controller.tick(dt.coerceAtMost(0.05f))
                }
                last = now
            }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(controller) {
                detectTapGestures(onTap = { controller.onTap() })
            }
            .pointerInput(controller) {
                detectDragGestures(
                    onDragStart = { controller.onPressStart() },
                    onDragEnd = { controller.onRelease() },
                    onDragCancel = { controller.onRelease() },
                    onDrag = { change, drag ->
                        change.consume()
                        controller.onDrag(drag.x, drag.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Card-shaped slot — the native view fills this, so rotating it reads as a card.
        Box(Modifier.fillMaxHeight(0.6f).aspectRatio(0.71f)) {
            CardScene(controller, Modifier.fillMaxSize())
        }
    }
}
