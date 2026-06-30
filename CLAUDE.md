# filamentKMP — project guide (main context)

> Loaded every session. Keep it short and high-signal. Deep detail lives in
> [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md) — read it before any non-trivial change.

## What this is
KMP + Compose Multiplatform demo that renders an interactive 3D "Pokémon-style" card with
Google **Filament**. The card is just a vehicle: **the real goal is a reusable interop
pipeline** for embedding Filament (native 3D) into Compose Multiplatform on Android + iOS,
where Compose UI drives the 3D through one shared state. A polished visual is a **later
phase, not abandoned**. Success = "I understand every seam and can reuse it by swapping the
scene", not "a pretty card".

## Current status (read this first)
- **Phase A / A1 — DONE.** Skeleton + architecture; gestures, shared state, frame loop,
  two-way HUD binding all working.
- **Phase A / A2 — DONE & runs on Android (owner confirmed real 3D on-screen).** The 2D
  `Canvas` placeholder is gone; `CardScene.android.kt` now hosts a real `FilamentRenderer`
  (Filament Engine on a `SurfaceView`, `Choreographer` loop reading the same controller).
  `commonMain` untouched — the seam held. Scope kept tight on purpose: a flat UNLIT
  double-sided quad, **no lighting/PBR/thickness yet** (those are Phase B). Deps added:
  `filament-android` + `filamat-android` (runtime material via `MaterialBuilder`);
  `filament-utils-android` was NOT needed (UiHelper/DisplayHelper live in `filament-android`).
- **Phase A / A3 — DONE & confirmed on-device.** Flick-to-spin yaw inertia: `CardState` carries
  `yawVelocity`; `CardReducer.step` measures angular speed while dragging and coasts yaw with
  friction on release (catch-to-stop on re-grab). Pitch still exponentially recenters. All in
  `commonMain` (renderer untouched). Covered by 5 `commonTest` cases
  (`./gradlew :shared:testAndroidHostTest`). Feel approved as-is — the tunables
  (`SPIN_FRICTION` / `MAX_SPIN` / `RECENTER_SPEED` in `CardReducer`) are left at current values.
  **Phase A complete.**
- **iOS — deferred.** Code (`CardScene.ios.kt`) is written but **not built** (owner has no
  Xcode). Don't spend effort on iOS until Xcode is available.

## Hard environment facts
- The owner builds/runs in **Android Studio / IntelliJ on macOS**.
- **Build environment varies by machine.** Some setups have full network + warm Gradle
  caches and build fine from the shell; others are network/cache-restricted, where
  `./gradlew` fails to resolve and "it builds" can't be trusted. **At the start of a
  session, before relying on or claiming a build result, ask the owner which environment
  this is** (open ↔ restricted) — it's a deliberate convention to avoid guessing.
- When the shell *can* build, verify with these task names (new
  `com.android.kotlin.multiplatform.library` plugin — NOT the old `compileDebugKotlinAndroid`):
  - `./gradlew :shared:compileAndroidMain` — common+Android Kotlin
  - `./gradlew :androidApp:assembleDebug` — full APK
  - `./gradlew :shared:compileCommonMainKotlinMetadata` — shared code for the iOS side
- Harmless noise on a working build: `[CXX1101] NDK ... ndk-bundle did not have a
  source.properties file` (empty `ndk-bundle` dir) — doesn't fail the build.

## Build & run (Android)
```bash
./gradlew :androidApp:installDebug      # or use the IDE run button
```
iOS target exists in Gradle but needs Xcode + network (Kotlin/Native dist) — skip for now.

## Architecture at a glance
```
commonMain  → state + logic + Compose UI (the reusable core)
  CardState        immutable snapshot (yaw/pitch/scale/isPressed)
  CardReducer      pure state transitions + per-frame physics
  CardController   StateFlow holder; UI & renderer both use it
  CardStage        gestures (drag/tap) + withFrameNanos tick + hosts CardScene
  CardHud          Compose overlay, two-way bound to the same state
  expect CardScene(controller, modifier)   ← the platform seam
androidMain → actual CardScene = AndroidView( SurfaceView )    [A2: real Filament — DONE]
iosMain     → actual CardScene = UIKitView( native UIView )   [placeholder; not built yet]
```
Data flow: **gesture → CardController (StateFlow) → renderer reads snapshot each frame**.
Gestures are captured in shared code (not the native view) so one state drives 3D *and* UI.

## Key files
| Path | Role |
|---|---|
| `shared/src/commonMain/.../scene/CardState.kt` | state contract |
| `shared/src/commonMain/.../scene/CardReducer.kt` | pure transitions + physics (tunables here) |
| `shared/src/commonMain/.../scene/CardController.kt` | StateFlow source of truth |
| `shared/src/commonMain/.../scene/CardStage.kt` | gestures + frame loop |
| `shared/src/commonMain/.../scene/CardScene.kt` | `expect` platform surface |
| `shared/src/commonMain/.../ui/CardHud.kt` | Compose overlay (UI↔3D) |
| `shared/src/androidMain/.../scene/CardScene.android.kt` | Android actual — hosts Filament `SurfaceView` + engine lifecycle |
| `shared/src/androidMain/.../scene/FilamentRenderer.kt` | A2 real Filament renderer (Engine, quad, runtime material, frame loop) |
| `shared/src/iosMain/.../scene/CardScene.ios.kt` | iOS actual (placeholder, unbuilt) |

## Conventions
- All `CardState` changes go through `CardReducer` pure functions — keep `CardController` thin.
- Gestures and the frame tick stay in **commonMain** (`CardStage`). Renderers only *read* state.
- Swapping the rendering backend must NOT require touching `commonMain`. If it does, the seam
  is wrong — fix the seam, not the common code.
- Tunables (sensitivity, clamps, spring speeds) live as constants in `CardReducer`.

## Stack / versions
Kotlin 2.4.0 · Compose MP 1.11.1 · AGP 9.0.1 (`com.android.kotlin.multiplatform.library`) ·
minSdk 24 · package `com.sadvakassov.filament.kmp` · Filament **1.72.0** (live on Android: GL
backend, runtime material via `filamat`) · iOS via CocoaPods + Metal (later).
New interop API: `androidx.compose.ui.viewinterop.UIKitView`.
