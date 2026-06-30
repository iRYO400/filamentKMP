package com.sadvakassov.filament.kmp.scene

import platform.UIKit.UIView

/**
 * The iOS seam to native Filament (A2). Filament on iOS is C++/Metal and Kotlin/Native can't
 * cinterop C++ directly, so the engine lives on the Swift side (an Objective-C++ shim that owns
 * a Metal-backed Filament `Engine` + `CADisplayLink`). Swift implements this interface and injects
 * it via [com.sadvakassov.filament.kmp.MainViewController]; the iOS `actual CardScene` then hosts
 * the produced [UIView] in a Compose `UIKitView`.
 *
 * This mirrors Android's split (Compose owns gestures/state, the engine owns drawing) but moves
 * the engine across the language boundary. Kotlin stays Filament-unaware: it only creates the
 * view, pushes the latest transform once per frame, and disposes. `commonMain` is untouched —
 * all of this lives in `iosMain`.
 */
interface CardSceneBridge {
    /** Build (or return) the Metal/Filament-backed view and start its internal render loop. */
    fun makeView(): UIView

    /**
     * Push the latest card transform. The shim stores it and renders it on its next
     * `CADisplayLink` tick — so this is a cheap setter, not a draw call. Angles are radians.
     */
    fun update(yaw: Float, pitch: Float, scale: Float)

    /** Stop the render loop and tear the Filament engine down. Called when the scene leaves. */
    fun dispose()
}

/**
 * Hand-off slot for the Swift-provided [CardSceneBridge]. Set by `MainViewController(bridge)` and
 * read by the iOS `actual CardScene`, so the bridge reaches the composable without threading it
 * through `commonMain` (which would break the platform seam). Null ⇒ the 2D placeholder renders,
 * which keeps the app runnable before the native side is wired.
 */
object IosCardScene {
    var bridge: CardSceneBridge? = null
}
