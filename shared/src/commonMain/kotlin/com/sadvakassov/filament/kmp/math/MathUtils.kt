package com.sadvakassov.filament.kmp.math

import kotlin.math.PI
import kotlin.math.exp

/** Frame-rate independent easing of [current] toward [target].
 *
 * [speed] is the approximate responsiveness (1/seconds): higher snaps faster. Using an
 * exponential makes the result stable regardless of frame timing [dt] (seconds). */
fun approach(current: Float, target: Float, speed: Float, dt: Float): Float {
    val t = 1f - exp(-speed * dt)
    return current + (target - current) * t
}

/** Linear interpolation. */
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

const val DEG_TO_RAD: Float = (PI / 180.0).toFloat()
const val RAD_TO_DEG: Float = (180.0 / PI).toFloat()

fun Float.toDegrees(): Float = this * RAD_TO_DEG
fun Float.toRadians(): Float = this * DEG_TO_RAD
