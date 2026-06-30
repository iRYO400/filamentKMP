package com.sadvakassov.filament.kmp.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGAffineTransformScale
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * A1 iOS placeholder. Embeds a native [UIView] via `UIKitView` and feeds it the latest
 * [CardState] through the `update` lambda. `isInteractive = false` lets touches pass through
 * to the Compose gesture detector in `CardStage` (the native view is passive display here).
 *
 * Only stable, low-risk UIKit/CoreGraphics calls are used. In A2 this `factory` is replaced
 * by a Metal-backed Filament view supplied from Swift (injected via DI), driven by the same
 * [controller].
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CardScene(controller: CardController, modifier: Modifier) {
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
        properties = UIKitInteropProperties(isInteractive = false),
    )
}

private val idleColor = UIColor.colorWithRed(red = 0.36, green = 0.36, blue = 0.84, alpha = 1.0)
private val pressedColor = UIColor.colorWithRed(red = 0.91, green = 0.35, blue = 0.68, alpha = 1.0)
