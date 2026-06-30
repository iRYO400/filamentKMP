package com.sadvakassov.filament.kmp.scene

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The platform 3D surface. The [actual] implementations embed a native view:
 *  - Android: `AndroidView` hosting a Filament `SurfaceView` (A1: a placeholder `View`).
 *  - iOS: `UIKitView` hosting a Metal-backed `UIView` (A1: a placeholder `UIView`).
 *
 * It reads [controller].state every frame and maps it onto the scene. It deliberately does
 * NOT handle gestures — those are captured in shared code (`CardStage`) so a single state
 * drives both the 3D and the Compose UI.
 */
@Composable
expect fun CardScene(controller: CardController, modifier: Modifier = Modifier)
