package com.sadvakassov.filament.kmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Home: mostly empty by design (preMVP = minimal shell, maximum 3D). A single large hero button
 * in a bottom bar opens the reveal.
 */
@Composable
fun HomeScreen(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0E0E14))) {
        Button(
            onClick = onOpen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeContentPadding()
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .height(64.dp),
        ) {
            Text("OPEN")
        }
    }
}
