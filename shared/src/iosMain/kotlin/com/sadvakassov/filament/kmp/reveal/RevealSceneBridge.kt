package com.sadvakassov.filament.kmp.reveal

import platform.UIKit.UIView

/**
 * iOS seam to the native Filament reveal renderer — the reveal counterpart of
 * [com.sadvakassov.filament.kmp.scene.CardSceneBridge]. Filament on iOS is C++/Metal, so the engine
 * lives in an Objective-C++ shim (`RevealCardView`) on the Swift side; Swift implements this
 * interface and injects it via [com.sadvakassov.filament.kmp.MainViewController].
 *
 * Kotlin stays Filament-unaware: each frame it computes the shared [RevealReducer.visuals] (so the
 * easing matches Android exactly) and pushes the resulting channels through [update]; the shim just
 * maps them onto transforms + material colours. `commonMain` is untouched.
 */
interface RevealSceneBridge {
    /**
     * Build (or return) the Metal/Filament-backed view and start its internal render loop. [onReady]
     * is invoked once, on the main thread, after the shim draws its first frame (readiness signal).
     */
    fun makeView(onReady: () -> Unit): UIView

    /**
     * Push the latest derived channels (cheap setter; drawn on the shim's next `CADisplayLink`
     * tick). Box channels are normalized 0..1 where noted; the shim owns the geometry distances.
     * When [inspect] is true the card is driven by [cardYaw]/[cardPitch]/[cardScale] (A3 physics),
     * otherwise by [cardRise]/[cardSpinYaw] (choreography).
     */
    fun update(
        shakeX: Float,
        boxBobY: Float,
        boxScale: Float,
        boxSplit: Float,
        seamGlow: Float,
        boxOpacity: Float,
        cardVisible: Boolean,
        inspect: Boolean,
        cardRise: Float,
        cardSpinYaw: Float,
        cardYaw: Float,
        cardPitch: Float,
        cardScale: Float,
    )

    /** Stop the render loop and tear the Filament engine down. Called when the scene leaves. */
    fun dispose()
}

/**
 * Hand-off slot for the Swift-provided [RevealSceneBridge], mirroring `IosCardScene`. Set by
 * `MainViewController(...)`, read by the iOS `actual RevealScene`. Null ⇒ the 2D placeholder
 * renders, keeping the app runnable before the native side is wired.
 */
object IosRevealScene {
    var bridge: RevealSceneBridge? = null
}
