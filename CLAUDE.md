# filamentKMP — project guide (main context)

> Loaded every session. Keep it short and high-signal. Deep detail lives in
> [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md) — read it before any non-trivial change.
> Filament's cross-platform (Android+iOS) feature set + feature-level reference:
> [`docs/FILAMENT_CAPABILITIES.md`](docs/FILAMENT_CAPABILITIES.md).

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
- **iOS A2 + A3 — DONE & confirmed on the iOS simulator (iPhone 17 Pro).** Real Filament renders
  (green quad on dark navy, true perspective turntable on drag) and the A3 feel — flick-to-spin
  inertia, recenter, tap pulse — works **for free**: no iOS-specific physics, the shared
  `CardReducer` drives it and `FilamentCardView` reads `yaw` each frame. Engine lives in Swift
  (`FilamentCardView.h/.mm` ObjC++/Metal in an `MTKView`), injected via
  `MainViewController(bridge)`; `commonMain` untouched. Built via CocoaPods (`pod 'Filament' 1.72.0`);
  MetalKit linked in `Config.xcconfig`; files auto-included via Xcode-16 synchronized groups.
- **🎉 Phase A complete on BOTH platforms.** The core goal is proven: one shared state drives a
  real native 3D engine + reactive feel on Android *and* iOS through one seam.
- **iOS lifecycle — wired.** `FilamentCardView` observes `UIApplication` background/foreground
  notifications and toggles `MTKView.paused` (iOS counterpart of Android's ON_PAUSE/ON_RESUME;
  also avoids Metal rendering to a backgrounded layer). Observers removed in `dispose`.
- **Next (backlog, not started):** Phase B = visual (glTF/PBR/holographic); **Phase C =
  architecture hardening** — adopt JetBrains' May-2026 KMP structure (split `shared` →
  `sharedLogic` + `sharedUI`); **Phase D = engine lifecycle** — lazy-load / warm-up /
  reclaim-on-hide / readiness state (stop loading Filament 100% eagerly); **Phase E = device
  capability detection** — first-launch splash probes Filament feature level → `zero/low/high`
  tier, cached, gates the experience. See PROJECT_CONTEXT §3 Phases C–E.

## Hard environment facts
- The owner builds/runs in **Android Studio / IntelliJ on macOS**.
- **Capabilities vary by machine — confirm at session start (deliberate convention).** The
  owner works across more than one Mac and they differ in two ways:
  - **Network / Gradle cache:** some have full network + warm caches and build fine from the
    shell; others are restricted, where `./gradlew` fails to resolve and "it builds" can't be
    trusted.
  - **Xcode:** present on some machines, absent on others — gates all iOS build/run work.
  Before relying on a build result or planning iOS work, **ask which machine this is** rather
  than guessing. (This session's machine: network OK + Xcode present, per the owner.)
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
