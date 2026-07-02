package com.sadvakassov.filament.kmp.reveal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sadvakassov.filament.kmp.scene.CardController

/**
 * Platform seam for the reveal: a native 3D surface that draws the procedural box + the holo card,
 * reading [reveal] (choreography) and [card] (A3 physics, during Inspect) each frame. Mirrors the
 * `CardScene` seam — swapping the backend must not touch commonMain.
 *
 * [onReady] is invoked once, on the main thread, after the renderer draws its first real frame — the
 * readiness signal (a lightweight Phase-D4 contract: just a callback, no engine types leak into the
 * shared state). The host uses it to cross-fade away a "Loading…" cover so the engine's create hitch
 * and blank first frame never show.
 */
@Composable
expect fun RevealScene(
    reveal: RevealController,
    card: CardController,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
)
