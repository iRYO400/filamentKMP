package com.sadvakassov.filament.kmp.reveal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sadvakassov.filament.kmp.scene.CardController

/**
 * Android actual for the reveal: embeds a real Filament `SurfaceView` via `AndroidView`, exactly
 * like `CardScene.android.kt`. Because this composable mounts only on the Reveal screen, the engine
 * is created on entry and destroyed on exit — lazy start/teardown for free (Phase D preview).
 */
@Composable
actual fun RevealScene(
    reveal: RevealController,
    card: CardController,
    onReady: () -> Unit,
    modifier: Modifier,
) {
    val holder = remember { RevealRendererHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            RevealRenderer(ctx, reveal, card, onReady).also { holder.value = it }.textureView
        },
        modifier = modifier,
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.value?.resume()
                Lifecycle.Event.ON_PAUSE -> holder.value?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.value?.destroy()
            holder.value = null
        }
    }
}

private class RevealRendererHolder {
    var value: RevealRenderer? = null
}
