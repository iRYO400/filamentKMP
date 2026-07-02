package com.sadvakassov.filament.kmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

/**
 * Branded entry screen. Auto-advances to Home after a short beat.
 *
 * The [LaunchedEffect] beat is deliberately the seat for **Phase E**'s device-capability probe:
 * later it becomes `val tier = probeDeviceCapability(); onDone(tier)`, pairing the slow first
 * launch with Phase D warm-up. For preMVP it's just a timed delay; [onDone] stays parameterless.
 */
@Composable
fun SplashScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        delay(SPLASH_MS)
        onDone()
    }
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF0E0E14)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "filamentKMP",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
    }
}

/** How long the splash lingers before advancing (ms). Later replaced by the probe's duration. */
private const val SPLASH_MS = 900L
