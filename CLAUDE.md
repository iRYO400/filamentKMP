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

**End-goal product (what the backlog climbs toward):** a free **case-opening simulator** —
payoff = a real 3D reveal on this pipeline. Tiered delivery; **preMVP = DONE** (splash → hero
button → procedural 3D box-open → holo card rises + inspect, on Android *and* iOS). **Next = MVP**:
real glTF gun models (Phase B1); MVP+ = full app (Cases + Inventory, 3 tabs). Concept +
backlog↔product map: [`docs/PRODUCT.md`](docs/PRODUCT.md).

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
- **🎉 preMVP — DONE & confirmed on BOTH platforms (owner-approved feel).** Splash → Home (one
  OPEN hero button) → **Reveal**: a *procedural* 3D lootbox (two half-cubes + seam-glow quad,
  runtime UNLIT material) plays the open choreography → the existing holo card rises out + one
  auto-spin → drag-to-inspect (reuses A3 flick-to-spin, **no seeding** via the 2π-spin trick).
  Architecture reuses the card idiom twice more, all seam-clean:
  - **Navigation = Navigation 3** (JetBrains CMP port `org.jetbrains.androidx.navigation3`, alpha).
    "The back stack is your state" → nav lives in `app/AppState.backStack` (pure, no Compose),
    mutated by `AppReducer`, held by `AppController`; `App.kt` hosts `NavDisplay` and routes Nav3
    back-events into the reducer. Chosen over Decompose/Voyager for single-source-of-truth
    coherence + scale. `NavDisplay.onBack` is `() -> Unit` in this version.
  - **Choreography = shared state machine** in `reveal/` (`RevealPhase`/`RevealState`/`RevealReducer`
    /`RevealVisuals`/`RevealController`), pure + time-stepped, unit-tested; `visuals(s)` derives all
    render channels so both renderers stay dumb. `RevealStage` runs one frame loop (choreography →
    hands the clock to `CardController` at Inspect) with gestures gated on inspect.
  - **Renderers:** Android `reveal/RevealRenderer.kt`; iOS `RevealCardView.mm` (Metal) via a
    `RevealSceneBridge`. Both read the *same* `RevealReducer.visuals`. iOS geometry/colour constants
    are **hand-synced** with `RevealRenderer.kt` (accepted duplication — no shared mesh layer) → tune
    both when changing feel. **iOS gotcha (fixed):** Filament uploads buffers async without copying —
    vertex data must outlive the upload; heap-copy + `releaseBuffer` callback (a stack-local array
    left the box invisible while the `static` card quad rendered).
  - **Phase-D preview (D1 + D3 + D4-lite):** 3D mounts only on the Nav3 Reveal entry → engine
    created on enter, destroyed on exit (D1 lazy-start + D3 teardown for free). **Load smoothing:**
    `RevealScene` reports its first rendered frame back through the seam (`onReady` callback — no
    engine types in shared state); `RevealScreen` holds an opaque "Loading…" cover + gates the
    choreography clock until `onReady` **and** ≥400 ms, then `AnimatedVisibility` cross-fades it away
    (hides the engine-create hitch + blank first frame + start-behind-loader). **Android Reveal uses
    `TextureView`, not `SurfaceView`** — it composites in the view tree so Nav3 enter/exit transitions
    animate smoothly (SurfaceView black-flashed on exit; iOS `MTKView` was already smooth). Still
    missing for full Phase D: warm-up (D2) + async teardown + a real readiness enum (`onDispose`
    destroy is still synchronous).
- **Next (backlog, not started):** Phase B = visual (glTF/PBR/holographic); **Phase C =
  architecture hardening** — split `shared` → `sharedLogic` + `sharedUI` (the `app/` + `reveal/`
  core packages are already Compose-free, so the split is mechanical); **Phase D = engine
  lifecycle** — formalize the readiness state / warm-up (preMVP already previews lazy-start);
  **Phase E = device capability detection** — the Splash beat is the seat for the feature-level
  probe → `zero/low/high` tier. See PROJECT_CONTEXT §3 Phases C–E.

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

## Build & run
```bash
./gradlew :androidApp:installDebug      # Android — or use the IDE run button
```
iOS: open `iosApp/iosApp.xcworkspace` in Xcode and run (needs Xcode + network for the Kotlin/Native
dist). Headless check: `xcodebuild -workspace iosApp/iosApp.xcworkspace -scheme iosApp -sdk
iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`.

## Architecture at a glance
```
commonMain  → state + logic + Compose UI (the reusable core)
  app/       Screen · AppState(backStack) · AppReducer · AppController   [Nav3, no Compose]
  scene/     CardState · CardReducer · CardController · CardStage · expect CardScene   [card pipeline]
  reveal/    RevealState/Phase · RevealReducer · RevealVisuals · RevealController      [box-open logic]
             RevealStage · RevealScreen · expect RevealScene                            [reveal Compose]
  ui/        SplashScreen · HomeScreen · CardHud
  App.kt     Nav3 NavDisplay host (backStack = AppState, back → AppController.onBack)
androidMain → actual CardScene / RevealScene = AndroidView( SurfaceView + Filament )   [DONE]
iosMain     → actual CardScene / RevealScene = UIKitView( native MTKView ) via *SceneBridge   [DONE]
```
Data flow (unchanged for all three): **gesture/tick → Controller (StateFlow) → renderer reads
snapshot each frame**. Reducers are pure + unit-tested; renderers only *read*. `app/` + `reveal/`
core stay Compose/engine-free (Phase-C-ready). `commonMain` never sees Filament — that's the seam.

## Key files
| Path | Role |
|---|---|
| `shared/src/commonMain/.../scene/CardState.kt` | state contract |
| `shared/src/commonMain/.../scene/CardReducer.kt` | pure transitions + physics (tunables here) |
| `shared/src/commonMain/.../scene/CardController.kt` | StateFlow source of truth |
| `shared/src/commonMain/.../scene/CardStage.kt` | gestures + frame loop |
| `shared/src/commonMain/.../scene/CardScene.kt` | `expect` platform surface |
| `shared/src/commonMain/.../ui/CardHud.kt` | Compose overlay (UI↔3D) |
| `shared/src/androidMain/.../scene/FilamentRenderer.kt` | A2 card Filament renderer (Engine, quad, runtime material, frame loop) |
| `shared/src/androidMain/.../reveal/RevealRenderer.kt` | reveal renderer — procedural box + card, driven by `RevealVisuals`/`CardState` |
| `iosApp/iosApp/filament/{FilamentCardView,RevealCardView}.mm` | iOS Metal shims (card + reveal), injected via `*SceneBridge` |
| `shared/src/commonMain/.../{app,reveal}/*.kt` | Nav3 nav core + reveal choreography (pure, Phase-C-ready) |
| `shared/src/commonTest/.../{AppReducerTest,RevealReducerTest}.kt` | nav + choreography unit tests |

## Conventions
- All `CardState` changes go through `CardReducer` pure functions — keep `CardController` thin.
- Gestures and the frame tick stay in **commonMain** (`CardStage`). Renderers only *read* state.
- Swapping the rendering backend must NOT require touching `commonMain`. If it does, the seam
  is wrong — fix the seam, not the common code.
- Tunables (sensitivity, clamps, spring speeds) live as constants in `CardReducer`.

## Stack / versions
Kotlin 2.4.0 · Compose MP 1.11.1 · AGP 9.0.1 (`com.android.kotlin.multiplatform.library`) ·
minSdk 24 · package `com.sadvakassov.filament.kmp` · Filament **1.72.0** (Android: GL backend +
runtime material via `filamat`; iOS: Metal via CocoaPods `pod 'Filament'`) · **Navigation 3**
`org.jetbrains.androidx.navigation3:navigation3-ui` **1.1.0-alpha01** (CMP port, alpha — re-pin on
any Compose MP bump). Interop: `androidx.compose.ui.viewinterop.UIKitView` / `AndroidView`.
