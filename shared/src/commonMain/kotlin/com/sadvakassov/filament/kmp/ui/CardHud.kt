package com.sadvakassov.filament.kmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sadvakassov.filament.kmp.getPlatform
import com.sadvakassov.filament.kmp.math.toDegrees
import com.sadvakassov.filament.kmp.scene.CardController
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Overlay that proves the two-way binding between Compose and the 3D state:
 *  - reads [CardController.state] live (3D → UI: yaw/pitch/scale readout),
 *  - writes via buttons (UI → 3D: nudge / reset).
 *
 * In A1 this is the visible evidence that the shared interaction pipeline is alive,
 * independent of how the native surface chooses to render the same state.
 */
@Composable
fun CardHud(controller: CardController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val platformName = remember { getPlatform().name }

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Filament KMP · A3 feel",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "platform: $platformName",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "yaw ${state.yaw.toDegrees().roundToInt()}°   " +
                "pitch ${state.pitch.toDegrees().roundToInt()}°   " +
                "scale ${(state.scale * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            when {
                state.isPressed -> "dragging…"
                abs(state.yawVelocity) > 0.05f -> "spinning ${state.yawVelocity.toDegrees().roundToInt()}°/s"
                else -> "idle"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { controller.nudgeYaw(-45f) }) { Text("⟲ 45°") }
            OutlinedButton(onClick = { controller.reset() }) { Text("Reset") }
            Button(onClick = { controller.nudgeYaw(45f) }) { Text("45° ⟳") }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "drag = rotate · tap = pulse",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
