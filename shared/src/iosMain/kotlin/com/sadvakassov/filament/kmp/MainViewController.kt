package com.sadvakassov.filament.kmp

import androidx.compose.ui.window.ComposeUIViewController
import com.sadvakassov.filament.kmp.reveal.IosRevealScene
import com.sadvakassov.filament.kmp.reveal.RevealSceneBridge
import com.sadvakassov.filament.kmp.scene.CardSceneBridge
import com.sadvakassov.filament.kmp.scene.IosCardScene

/**
 * iOS entry point, called from `ContentView.swift`.
 *
 * Pass Swift bridges backing the native Filament views to render real 3D; pass `null` (the
 * default) for either to fall back to that scene's 2D placeholder. [cardBridge] backs the card
 * scene, [revealBridge] backs the box-open reveal. Storing them here — rather than threading them
 * through `App()` — keeps the injection in `iosMain` and leaves `commonMain` unaware of the surface.
 */
fun MainViewController(
    cardBridge: CardSceneBridge? = null,
    revealBridge: RevealSceneBridge? = null,
) = ComposeUIViewController {
    IosCardScene.bridge = cardBridge
    IosRevealScene.bridge = revealBridge
    App()
}