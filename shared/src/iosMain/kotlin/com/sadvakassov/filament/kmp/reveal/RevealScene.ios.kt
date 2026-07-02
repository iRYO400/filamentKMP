package com.sadvakassov.filament.kmp.reveal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.sadvakassov.filament.kmp.scene.CardController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.isActive
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGAffineTransformScale
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * iOS `actual` for the reveal. Two paths, chosen by whether Swift injected a [RevealSceneBridge]
 * (see [com.sadvakassov.filament.kmp.MainViewController]), mirroring `CardScene.ios.kt`:
 *  - **real Filament** — host the native Metal shim in `UIKitView` and push the shared
 *    [RevealReducer.visuals] channels each frame (easing computed once, in Kotlin, so it matches
 *    Android exactly); the shim's `MTKView` loop draws.
 *  - **2D placeholder** — a coloured rounded rect standing in for the card, kept until a bridge
 *    is wired.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun RevealScene(
    reveal: RevealController,
    card: CardController,
    onReady: () -> Unit,
    modifier: Modifier,
) {
    val bridge = IosRevealScene.bridge
    if (bridge != null) {
        FilamentRevealScene(reveal, card, onReady, bridge, modifier)
    } else {
        // The placeholder has no engine to wait for — report ready immediately so the cover fades.
        LaunchedEffect(Unit) { onReady() }
        PlaceholderRevealScene(reveal, card, modifier)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun FilamentRevealScene(
    reveal: RevealController,
    card: CardController,
    onReady: () -> Unit,
    bridge: RevealSceneBridge,
    modifier: Modifier,
) {
    LaunchedEffect(bridge, reveal, card) {
        while (isActive) {
            withFrameNanos { }
            val v = RevealReducer.visuals(reveal.state.value)
            val c = card.state.value
            bridge.update(
                shakeX = v.shakeX,
                boxBobY = v.boxBobY,
                boxScale = v.boxScale,
                boxSplit = v.boxSplit,
                seamGlow = v.seamGlow,
                boxOpacity = v.boxOpacity,
                cardVisible = v.cardVisible,
                inspect = v.inspect,
                cardRise = v.cardRise,
                cardSpinYaw = v.cardYaw,
                cardYaw = c.yaw,
                cardPitch = c.pitch,
                cardScale = c.scale,
            )
        }
    }
    DisposableEffect(bridge) { onDispose { bridge.dispose() } }
    UIKitView(
        factory = { bridge.makeView(onReady = onReady) },
        modifier = modifier,
        update = {},
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun PlaceholderRevealScene(reveal: RevealController, card: CardController, modifier: Modifier) {
    var yaw by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(0f) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(reveal, card) {
        while (isActive) {
            withFrameNanos { }
            val v = RevealReducer.visuals(reveal.state.value)
            visible = v.cardVisible
            if (v.inspect) {
                val c = card.state.value
                yaw = c.yaw
                scale = c.scale
            } else {
                yaw = v.cardYaw
                scale = v.cardRise
            }
        }
    }

    UIKitView(
        factory = {
            UIView().apply {
                backgroundColor = cardColor
                layer.cornerRadius = 20.0
                clipsToBounds = true
            }
        },
        modifier = modifier,
        update = { view ->
            val s = if (visible) scale.coerceIn(0.0001f, 4f).toDouble() else 0.0001
            val rotated = CGAffineTransformMakeRotation(yaw.toDouble())
            view.transform = CGAffineTransformScale(rotated, s, s)
        },
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

private val cardColor = UIColor.colorWithRed(red = 0.55, green = 0.80, blue = 0.58, alpha = 1.0)
