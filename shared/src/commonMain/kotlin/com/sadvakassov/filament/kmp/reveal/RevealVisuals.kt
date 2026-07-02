package com.sadvakassov.filament.kmp.reveal

/**
 * Flat bag of render channels derived from [RevealState] by [RevealReducer.visuals]. Each field
 * maps straight onto a transform or material uniform, so a renderer just reads them — no easing,
 * no phase logic on the platform side. Keeping the easing here (shared) means Android and iOS
 * animate identically.
 */
data class RevealVisuals(
    /** Vertical bob offset of the whole box (idle breathing). */
    val boxBobY: Float = 0f,
    /** Horizontal shake offset (anticipation). */
    val shakeX: Float = 0f,
    /** Uniform scale of the box (pop overshoot). */
    val boxScale: Float = 1f,
    /** 0..1 separation of the two box halves. */
    val boxSplit: Float = 0f,
    /** 0..1 emissive intensity of the seam light-leak. */
    val seamGlow: Float = 0f,
    /** 1..0 opacity of the box (fades as it splits). */
    val boxOpacity: Float = 1f,
    /** Whether the card should be drawn at all. */
    val cardVisible: Boolean = false,
    /** 0..1 rise of the card out of the box. */
    val cardRise: Float = 0f,
    /** Card auto-spin angle in radians (pre-inspect only). */
    val cardYaw: Float = 0f,
    /** Terminal state: card is now driven by CardState, box is gone. */
    val inspect: Boolean = false,
)
