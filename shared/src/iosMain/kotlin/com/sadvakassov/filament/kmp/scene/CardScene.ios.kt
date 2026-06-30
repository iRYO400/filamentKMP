package com.sadvakassov.filament.kmp.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.isActive
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGAffineTransformScale
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * iOS `actual` scene. Two paths, chosen at runtime by whether Swift injected a [CardSceneBridge]
 * (see [com.sadvakassov.filament.kmp.MainViewController]):
 *
 *  - **A2 real Filament** — when a bridge is present: host the native Metal/Filament [UIView] in
 *    `UIKitView` and push the latest transform once per frame (no Compose recompose per frame,
 *    matching Android's Choreographer-driven [FilamentRenderer]). The shim's own `CADisplayLink`
 *    does the drawing.
 *  - **2D placeholder** — when no bridge is wired yet: the original A1 `UIView` that rotates via
 *    `CGAffineTransform`. Keeps the app runnable while the native side is being set up.
 *
 * Either way `interactionMode = null` makes the native view passive so touches fall through to the
 * Compose gesture detector in `CardStage`.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CardScene(controller: CardController, modifier: Modifier) {
    val bridge = IosCardScene.bridge
    if (bridge != null) {
        FilamentCardScene(controller, bridge, modifier)
    } else {
        PlaceholderCardScene(controller, modifier)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun FilamentCardScene(controller: CardController, bridge: CardSceneBridge, modifier: Modifier) {
    // Push the latest snapshot every frame straight into the shim (cheap setter); the engine's
    // CADisplayLink renders whatever is current. Mirrors the renderer-reads-snapshot model.
    LaunchedEffect(bridge, controller) {
        while (isActive) {
            withFrameNanos { }
            val s = controller.state.value
            bridge.update(s.yaw, s.pitch, s.scale)
        }
    }
    DisposableEffect(bridge) {
        onDispose { bridge.dispose() }
    }
    UIKitView(
        factory = { bridge.makeView() },
        modifier = modifier,
        update = {},
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun PlaceholderCardScene(controller: CardController, modifier: Modifier) {
    val state by controller.state.collectAsState()
    UIKitView(
        factory = {
            UIView().apply {
                backgroundColor = idleColor
                layer.cornerRadius = 28.0
                clipsToBounds = true
            }
        },
        modifier = modifier,
        update = { view ->
            view.backgroundColor = if (state.isPressed) pressedColor else idleColor
            val rotated = CGAffineTransformMakeRotation(state.yaw.toDouble())
            view.transform = CGAffineTransformScale(rotated, state.scale.toDouble(), state.scale.toDouble())
        },
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

private val idleColor = UIColor.colorWithRed(red = 0.36, green = 0.36, blue = 0.84, alpha = 1.0)
private val pressedColor = UIColor.colorWithRed(red = 0.91, green = 0.35, blue = 0.68, alpha = 1.0)
