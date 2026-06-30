package com.sadvakassov.filament.kmp

import androidx.compose.ui.window.ComposeUIViewController
import com.sadvakassov.filament.kmp.scene.CardSceneBridge
import com.sadvakassov.filament.kmp.scene.IosCardScene

/**
 * iOS entry point, called from `ContentView.swift`.
 *
 * Pass a [bridge] (a Swift object backing the native Filament view) to render real 3D; pass
 * `null` (the default) to fall back to the 2D placeholder. Storing it here — rather than
 * threading it through `App()` — keeps the injection in `iosMain` and leaves `commonMain`
 * unaware of the platform surface.
 */
fun MainViewController(bridge: CardSceneBridge? = null) = ComposeUIViewController {
    IosCardScene.bridge = bridge
    App()
}