package com.sadvakassov.filament.kmp.scene

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs
import kotlin.math.cos

/**
 * A1 Android placeholder. Embeds a native [View] via `AndroidView` and feeds it the latest
 * [CardState] through the `update` lambda (re-invoked whenever the collected state changes).
 *
 * The drawing is throwaway pseudo-3D — its only job is to prove the seam: a real native view
 * lives in the Compose tree and visibly reacts to the shared state. In A2 this `factory`
 * returns a Filament `SurfaceView` instead, driven by the same [controller].
 */
@Composable
actual fun CardScene(controller: CardController, modifier: Modifier) {
    val state by controller.state.collectAsState()
    AndroidView(
        factory = { ctx -> CardPlaceholderView(ctx) },
        modifier = modifier,
        update = { view -> view.render(state) },
    )
}

private class CardPlaceholderView(context: Context) : View(context) {
    private var state = CardState()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5B5BD6.toInt() }
    private val fillPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE85AAD.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = 0x66FFFFFF
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }

    fun render(newState: CardState) {
        state = newState
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width * 0.92f
        val h = height * 0.92f
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(state.scale, state.scale)
        // fake turntable: yaw squeezes width (|cos|), pitch tilts the card
        canvas.scale(abs(cos(state.yaw)).coerceAtLeast(0.2f), 1f)
        canvas.rotate(state.pitch * 28f)
        val rect = RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)
        canvas.drawRoundRect(rect, 36f, 36f, if (state.isPressed) fillPressed else fill)
        canvas.drawRoundRect(rect, 36f, 36f, border)
        canvas.drawText("Filament A2", 0f, 14f, text)
        canvas.restore()
    }
}
