package com.sadvakassov.filament.kmp.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * A2 Android actual: embeds a real Filament-backed [android.view.SurfaceView] via `AndroidView`.
 *
 * The renderer reads [controller].state every frame on its own Choreographer loop (no Compose
 * recomposition per frame), so gestures captured in shared `CardStage` flow straight into the
 * 3D scene. Engine lifecycle is tied to the composition + host lifecycle:
 *  - resume/pause the frame loop with ON_RESUME/ON_PAUSE,
 *  - destroy the engine when the composable leaves.
 */
@Composable
actual fun CardScene(controller: CardController, modifier: Modifier) {
    val renderer = remember { RendererHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx -> FilamentRenderer(ctx, controller).also { renderer.value = it }.surfaceView },
        modifier = modifier,
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> renderer.value?.resume()
                Lifecycle.Event.ON_PAUSE -> renderer.value?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            renderer.value?.destroy()
            renderer.value = null
        }
    }
}

/** Tiny mutable holder so the factory result is reachable from the DisposableEffect. */
private class RendererHolder {
    var value: FilamentRenderer? = null
}
