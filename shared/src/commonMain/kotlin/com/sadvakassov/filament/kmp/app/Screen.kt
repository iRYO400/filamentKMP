package com.sadvakassov.filament.kmp.app

/**
 * The set of app screens, used as navigation keys.
 *
 * Pure data — no Compose, no engine, no platform types — so it lives in the reusable core and
 * drops straight into `sharedLogic` at Phase C. The UI host (App.kt) maps each key to its
 * composable; the back stack of these keys is the single source of truth (see [AppState]).
 */
sealed interface Screen {
    data object Splash : Screen
    data object Home : Screen
    data object Reveal : Screen
}
