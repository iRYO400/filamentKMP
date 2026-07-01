# filamentKMP — deep project context

> Companion to root `CLAUDE.md`. This is the full design record: goal, decisions, milestones,
> architecture, the Filament-sources handoff, and the A2 as-built record (next up: A3). A fresh
> session should be able to resume from here without re-deriving anything.

---

## 1. Goal & priorities

Demo (non-commercial; for learning + building a reusable pipeline):
- **KMP** for **iOS + Android**, UI in **Compose Multiplatform**.
- On screen: a 3D "Pokémon-style" card — rotate by touch, tap → responsive animation/feedback,
  holographic sheen across the surface.
- Compose UI (HUD, buttons, overlays) **interacts** with the 3D through one shared state.

**Priorities (do not misread):**
- Primary = a **reusable Filament ↔ KMP/Compose interop pipeline**. The card is only a vehicle
  to exercise every hard seam.
- A **beautiful visual is also a goal, just a later phase** (deferred, not dropped). First prove
  the seam works and is smooth on a minimal visual; pile on visuals afterward, on top of the
  working pipeline. "Adding visuals = swapping material/model, not rearchitecting" is itself the
  proof the pipeline is good.
- **Success criterion:** "I understand every seam and can reproduce it in a real app by swapping
  the scene" — not "a pretty card".

## 2. Decisions (locked)
1. **Sequencing: Android-first → then iOS.** Order of work, not scope — the foundation must run
   on both. Android first (trivial Maven, all Kotlin) to debug the architecture/state contract on
   the easy platform, then port to iOS where the effort goes to the hard part (`.mm` shim).
2. **Starter geometry: procedural quad** (+ slight thickness) — focus on the seam and material,
   no external modeling. glTF comes later (Phase B).
3. **Starter effect: tilt-follow + basic view-angle sheen** — exercises the full data path
   (gesture → CardState → TransformManager + material uniform → frame). Rich holographic foil is
   Phase B.

## 3. Milestones & status

**Phase A — Foundation** (prove the seam is alive, smooth, cleanly separated):
- **A1 — Runnable skeleton + architecture. ✅ DONE (runs on Android).**
  KMP wiring, `CardController`/`CardState`/`CardReducer`, `expect CardScene` + `actual` on both
  platforms. App launches; gestures + frame loop + two-way HUD binding work. *Visual was a 2D
  placeholder at A1 — since superseded by real Filament in A2.*
- **A2 — Real basic geometry renders. ✅ DONE (Android; owner confirmed 3D on-screen).**
  `FilamentRenderer.kt` owns a Filament `Engine` on a `SurfaceView` (UiHelper/DisplayHelper,
  GL backend), draws a procedural quad with a runtime-built material, and drives it from
  `controller.state` on a `Choreographer` loop via `TransformManager`. `commonMain` untouched.
  Scope diffs from the original plan (deliberate): material is **UNLIT + doubleSided, no
  light** (clear two-sided card without a tangent frame; lit/PBR → Phase B); only
  `filament-android` + `filamat-android` deps were needed (`filament-utils-android` not used).
  See §7 for the as-built record.
- **A3 — Geometry reacts to input through the engine. ✅ DONE (confirmed on-device).**
  Flick-to-spin inertia added purely in `commonMain`: `CardState.yawVelocity` (+ internal
  `prevYaw`); `CardReducer.step` measures yaw speed while pressed and, on release, coasts yaw with
  exponential friction (`SPIN_FRICTION`), clamped by `MAX_SPIN`, snapping to rest below `MIN_SPIN`.
  `pressStart` zeroes velocity (catch-to-stop) and seeds `prevYaw`. Pitch still recenters via
  `approach`. Renderer untouched — it already reads `yaw` each frame, so inertia "just works".
  HUD shows live `spinning N°/s`. Verified by 5 pure `commonTest` cases
  (`:shared:testAndroidHostTest`, all green) and approved on-device; tunables left at current
  values. **Phase A foundation is complete.** Next: either Phase B visual polish, or port
  A2/A3 to iOS once Xcode is available (§6).
- **iOS A2/A3 — ✅ DONE** (Filament via Metal/MTKView, confirmed on simulator; A3 feel inherited
  from shared `CardReducer` for free). **Phase A complete on Android + iOS.** See §6.

**Phase B — Content & visual** (only after a working foundation):
- **B1 — glTF pipeline.** Swap procedural geometry for a model via `gltfio`. No architecture
  change.
- **B2 — Visual polish.** Holographic foil `.mat`, IBL environment, bloom/AA, tap particles +
  haptics. "Looks like a production demo."

**Phase C — Architecture hardening / modern KMP conventions** (BACKLOG — separate phase, not now):
Goal: bring the project in line with current KMP best practices now that the foundation works.
Headline item (anchored, owner-requested):
- **Adopt JetBrains' May-2026 "new default KMP structure"** — split the single `shared` module
  (which today mixes pure logic + Compose UI) into **`sharedLogic`** (no Compose deps — the
  reusable core: `CardState`/`CardReducer`/`CardController`/`math`) and **`sharedUI`** (Compose:
  `CardStage`/`CardHud`/`App`/`CardScene` + renderers). We're a textbook candidate. Refs:
  https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/ and
  https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html
  Re-read both before starting — details may have evolved.
Candidate companion items (decide scope when the phase starts):
- DI for `CardController` instead of `remember { CardController() }` in `App()`.
- Expand `commonTest` beyond the reducer; consider a tiny render-contract test.
- Revisit source-set/package naming and the iOS bridge packaging against the new guidance.
Constraint: the interop seam must survive the module split — `sharedLogic` must not gain a
Compose or platform dependency, or the whole point is lost.

**Phase D — Engine lifecycle: lazy-load & warm-up** (BACKLOG — separate phase, not now):
Goal: stop loading Filament 100% eagerly. Today the engine is built synchronously the instant
`CardScene` composes — Android `FilamentRenderer.init {}` via the `AndroidView` factory
(`Filament.init()` native lib load → `Engine.create()` → scene/material build → `resume()`);
iOS `FilamentCardView initWithFrame` (`setupMetalView` + `setupFilament`, `paused = NO`) via the
`UIKitView` factory / bridge. Real apps defer heavy 3D until it's needed (e.g. behind a tab) or
pre-warm it off the critical path. The interesting work is a **lifecycle contract on the seam**,
kept platform-agnostic. The only lifecycle that exists today is pause/resume of the *frame loop*
(Choreographer callback / `MTKView.paused`); the engine itself always stays alive, and teardown
only fires on composition dispose.
- **D1 — Lazy / deferred start.** Don't create the engine until the surface is actually shown.
  A host-controlled gate so `CardScene` builds the native engine on demand rather than in the
  factory, with a lightweight placeholder (or the existing 2D placeholder) until then. Android:
  defer `Engine.create()` + scene build out of `init`; iOS: defer `setupMetalView`/`setupFilament`.
- **D2 — Background warm-up (pre-warm).** Start the expensive parts ahead of time so the surface
  appears instantly: native lib load (`Filament.init()`), `Engine.create()`, runtime material
  compilation. **Constraint:** Filament requires its API calls on the main/render thread, so
  "background" means pre-scheduling CPU-bound prep off the first-frame critical path — not calling
  the engine from an arbitrary thread. Scope the real thread boundaries when the phase starts.
- **D3 — Teardown / reclaim on hide.** Policy for when the card leaves view: fully `destroy()` to
  reclaim GPU/native memory vs. keep-warm + pause. Today `destroy()` only fires on composition
  `onDispose`; pause only stops the frame loop. Decide keep-warm vs. reclaim (likely configurable)
  and where the trigger lives (composition dispose vs. an explicit host signal).
- **D4 — Readiness state in the shared core.** An engine-lifecycle state
  (`Idle → WarmingUp → Ready → Paused`, + an error case) in the shared layer so the HUD can show a
  loading indicator and gestures can be gated until ready. **Must stay platform-agnostic** — no
  Compose/engine types in the state; renderers report readiness back through the seam, they don't
  leak into `CardState`.
Seam impact (decide when the phase starts): the `expect CardScene` signature and/or
`CardController` gain a start/readiness concept. This is the same seam Phase C splits into
`sharedLogic`/`sharedUI`, so the D4 readiness contract must live in `sharedLogic` with no platform
dependency — **sequence D after (or alongside) Phase C**, and don't let the lifecycle contract
re-couple the core to a platform. Prerequisite for demoing lazy-load on-device: a minimal
multi-screen/tab host exists nowhere today — building one is out of scope for this entry and
decided when D starts.

**Phase E — Device capability detection & tiering (splash gate)** (BACKLOG — separate phase, not now):
Goal: on **first launch**, a splash screen probes what Filament can actually do on *this* device,
classifies it into a tier, **persists** the verdict, and gates the experience accordingly (à la
Telegram's low/medium/high device classes that drive which animations/UI to show). First launch is
intentionally slower (the probe); later launches read the cached tier and skip it. This pairs
naturally with **Phase D** — the same slow-first-launch moment does the warm-up *and* the probe.
Deep-dive on what's probeable: `docs/FILAMENT_CAPABILITIES.md` §2 & §5.
- **E1 — Tier model (stage-1 = 3 tiers).** Owner-proposed simplification: **`zero / low / high`**
  (expandable later to Telegram-style `low/medium/high`). Mapping (draft, ground in feature levels):
  - **zero = unsupported** — backend won't init or `getSupportedFeatureLevel()` < FL1 (GLES-2-only
    / FL0). No point continuing → show an "unsupported device" screen, don't enter the experience.
  - **low = weak device** — FL1 baseline. Run a reduced path: simpler material, no bloom/SSAO/heavy
    shadows, low/no MSAA, ≤16 samplers, smaller textures.
  - **high = full capabilities** — FL2/FL3. All post-FX, richer material, higher MSAA.
- **E2 — Probe mechanism.** Create the (real or throwaway) `Engine` once and read
  `getSupportedFeatureLevel()` as the primary signal, plus `isStereoSupported`,
  `getMaxAutomaticInstances`, backend, and the `FEATURE_LEVEL_CAPS`/`MAX_*` limits. Optional
  stage-2: a tiny timed micro-render to catch devices that *report* a level but run it slowly (the
  Telegram "perf class" idea) — decide if worth it when the phase starts.
- **E3 — Persistence.** Cache the tier + probed values so only the first launch pays the cost
  (invalidate on app/Filament version bump). Needs a small KMP key-value store (`expect/actual` or
  a multiplatform settings lib) — call the dependency choice out as a sub-task.
- **E4 — Where it lives (seam discipline).** The **tier enum + probe-result model + classification
  rules** are pure data → `sharedLogic` (no Compose/engine types). Only the raw `Engine` query is
  platform-side, reported back through a small seam (mirrors D4's readiness contract). The splash
  UI + "unsupported" screen are Compose in `sharedUI`. The experience/HUD reads the tier to pick
  its render path.
Constraint: same as D/C — the classifier must not pull a platform or Compose dependency into the
shared core. Sequence **after Phase D** (shares the first-launch/warm-up flow) and mind Phase C's
`sharedLogic`/`sharedUI` split.

## 4. Architecture

```
┌───────────────────────── COMPOSE UI (commonMain) ─────────────────────────┐
│ HUD / buttons / overlays — plain Compose MP, bound to CardController.state │
└───────────────┬───────────────────────────────────┬──────────────────────┘
                │ reads/writes state                  │ expect CardScene()
                ▼                                      ▼
┌───────────────────────────┐          ┌──────────────────────────────────┐
│ SHARED STATE/LOGIC (common)│          │ PLATFORM RENDER (actual)          │
│ CardState (snapshot)       │◄─────────┤ Android: AndroidView(View)        │
│ CardReducer (pure + physics)│          │ iOS:     UIKitView(UIView)        │
│ CardController (StateFlow)  │          │ renderer reads state each frame   │
│ CardStage (gestures + tick) │          └──────────────────────────────────┘
└───────────────────────────┘                         │
                                                       ▼
                                         FILAMENT ENGINE (native, A2+)
                                         Engine→Renderer→View→Scene→Camera
```

**Data flow:** `gesture → CardController (StateFlow) → renderer reads snapshot each frame`.
Gestures are captured in shared `CardStage` (not the native view) so a single state drives both
the 3D and the UI. `StateFlow.value` is an atomic snapshot — safe to read from a render thread.

**expect/actual seam:**
```kotlin
@Composable expect fun CardScene(controller: CardController, modifier: Modifier = Modifier)
```
Android `actual` → `AndroidView`; iOS `actual` → `UIKitView` (new API
`androidx.compose.ui.viewinterop.UIKitView`, `properties = UIKitInteropProperties(isInteractive = false)`
so touches pass through to the Compose gesture detector).

## 5. Module / source layout (as generated by the KMP wizard)
- `shared/` — KMP library (`com.android.kotlin.multiplatform.library`). Holds Compose UI + logic.
  - `commonMain/.../{scene,ui,math}` — the reusable core.
  - `androidMain/.../scene/CardScene.android.kt` — Android actual.
  - `iosMain/.../scene/CardScene.ios.kt` — iOS actual.
  - iOS framework: `binaries.framework { baseName = "Shared"; isStatic = true }`, targets
    `iosArm64` + `iosSimulatorArm64` (no x64 sim).
- `androidApp/` — `com.android.application`, depends on `:shared`, hosts `MainActivity { App() }`.
- `iosApp/` — plain Xcode project (`ContentView.swift` → `MainViewControllerKt.MainViewController()`).
  **No CocoaPods yet** — wizard used a direct static framework, not the `kotlin.cocoapods` plugin.
  `Info.plist` already sets `CADisableMinimumFrameDurationOnPhone` (needed for high-FPS Compose).

## 6. iOS A2 — real Filament (🛠 IN PROGRESS: Kotlin seam done+verified, native half awaiting Xcode)
Filament on iOS is native C++/Metal; K/N can't cinterop C++ → the engine lives on the **Swift
side** and is injected into Compose. Confirmed during build-out: `pod 'Filament'` is real (since
1.8.0) and **includes filamat**, so the material is built at runtime exactly like Android (no
`matc` needed); the official path uses **`MTKView` + `MTKViewDelegate`** as the vsync loop (not a
hand-rolled `CADisplayLink`).

**Kotlin seam — DONE, compiles on `iosSimulatorArm64`+`iosArm64` (commit `42f0052`):**
- `CardSceneBridge` interface (`makeView()/update(yaw,pitch,scale)/dispose()`) + `IosCardScene`
  holder; `MainViewController(bridge: CardSceneBridge? = null)` injects it; `CardScene.ios` uses
  the real path when a bridge is present (hosts the native `UIView` in `UIKitView`, pushes the
  transform every frame via `withFrameNanos` — no recompose) and otherwise renders the 2D
  placeholder. `commonMain` untouched.

**Native half — generated, NOT yet built (owner does this in Xcode; see `iosApp/IOS_A2_SETUP.md`):**
- `FilamentCardView.h/.mm` (ObjC++): faithful port of Android `FilamentRenderer` — Metal Engine,
  1.0×1.4 quad, runtime UNLIT+doubleSided material (`targetApi METAL`), camera +Z=3.2, transform
  `Ry·Rx·scale` via `TransformManager`; `MTKViewDelegate.drawInMTKView` is the frame loop.
- `CardSceneBridgeImpl.swift` implements the Kotlin protocol and owns the view; `ContentView.swift`
  now passes `MainViewController(bridge: CardSceneBridgeImpl())`; `Podfile` + bridging header added.
- Owner runs `pod install`, adds files to the target, sets the bridging header + C++17/libc++,
  builds on arm64. The `.mm` couldn't be shell-compiled — expect minor first-build fixups
  (filamat `build()` arg, `Package` getters, `createSwapChain` cast); the seam won't change.

**Env + pods bring-up — DONE (this session):** `kdoctor` clean (Xcode ✓, CocoaPods ✓ after
`brew install cocoapods` + UTF-8 exports in `~/.zprofile`). `pod install` succeeded and confirmed
**`pod 'Filament' 1.72.0` exists** (the version-pin risk is gone); it vendors xcframeworks
(arm64 + sim) for the whole set (filament/filamat/gltfio/image/…). `.xcworkspace` + `Podfile.lock`
committed; `Pods/` (~88MB) is gitignored — reproduce with `pod install`.

**iOS A2 + A3 — ✅ DONE, confirmed on the iOS simulator (iPhone 17 Pro).** Green Filament quad on
dark navy, real perspective turntable on drag, live HUD, flick-to-spin inertia. Build fixups that
were actually needed (vs the generated guesses): **none in the `.mm`** — the Filament 1.72.0 C++
API matched as written; the only real issue was **linking MetalKit** (the pod links Metal, not
MetalKit) → added `-framework MetalKit -framework Metal` via `OTHER_LDFLAGS` in `Config.xcconfig`.
Gotchas learned: the project uses **Xcode-16 file-system synchronized groups**, so shim files in
`iosApp/iosApp/` are auto-included in the target (no pbxproj entries, no manual "add to target");
"Add Files" can mistakenly add workspace-level refs (harmless). A3 needed **zero** iOS-specific
code — the shared `CardReducer` drives the feel and the shim reads `yaw` per frame.
**Phase A is now complete on both platforms.** iOS lifecycle pause/resume is now wired too:
`FilamentCardView` observes `UIApplicationDidEnterBackground/WillEnterForeground` and toggles
`MTKView.paused` (mirrors Android ON_PAUSE/ON_RESUME; also prevents Metal rendering to a
backgrounded layer), removing observers in `dispose`.

## 7. A2 as-built — real Filament on Android (✅ DONE, commit `c76cc0f`)
Self-contained on the owner's machine (Maven only; **no `matc`, no Filament source build**):
- Deps actually added: `com.google.android.filament:filament-android:1.72.0` +
  `:filamat-android:1.72.0` (runtime material builder). **`filament-utils-android` was NOT
  needed** — `UiHelper`/`DisplayHelper` ship inside `filament-android` (pkg
  `com.google.android.filament.android`). (`gltfio-android` still Phase B only.)
- Only `CardScene.android.kt` changed + new `FilamentRenderer.kt`; **commonMain untouched**.
- `FilamentRenderer`: `Engine.create()` (default GL backend) → `SurfaceView` + `UiHelper` →
  `SwapChain` (from `SurfaceCallback.onNativeWindowChanged`); `Renderer`/`View`/`Scene`/
  `Camera` (camera at +Z=3.2 looking at origin, 45° vertical FOV). Geometry: a flat quad
  (1.0×1.4, XY plane) via `VertexBuffer`/`IndexBuffer`, POSITION-only.
- Material: built at runtime via `filamat` `MaterialBuilder` — **UNLIT + doubleSided**, solid
  baseColor, `targetApi(OPENGL)` to match the GL engine. (No light; lit/PBR deferred to B2.)
  `MaterialBuilder.init()/shutdown()` bracket the build.
- `Choreographer` frame loop reads `controller.state.value` and maps yaw/pitch/scale to a
  column-major 4×4 (`Ry*Rx*scale`) applied through `TransformManager`. No Compose recompose
  per frame.
- Lifecycle: `resume()`/`pause()` toggle the frame callback (running-guard prevents
  double-post); `destroy()` tears down all Filament objects + entities + `Engine.destroy()`,
  wired to ON_RESUME/ON_PAUSE/onDispose in the Compose actual.
- Acceptance met: owner built it and a real 3D card renders + reacts to the same drag/tap.
- Known cosmetic gap: clear-color comment says "soft pink" but the value is dark navy
  `(0.055,0.055,0.078)` — harmless, revisit when polishing the visual.

## 8. Filament-sources handoff (for a second Claude opened in `google/filament`)
That repo compiles slowly — **extract artifacts/knowledge, don't rebuild the engine.** Useful
outputs for later phases:
- Phase B material toolchain: exact `matc` flags to compile `card.mat` → `card.filamat` for
  mobile across Metal + GL/Vulkan; `cmgen` to turn an `.hdr` into IBL (`*_ibl.ktx`) + skybox.
  Tools must come from the **1.72.0** release (must match runtime).
- A holographic `.mat` draft (view-direction/fresnel based) referencing `samples/materials/`.
- iOS `.mm` skeleton based on `ios/samples/` (gltf-viewer / hello-pbr): CAMetalLayer setup,
  `createSwapChain`, `CADisplayLink` loop, glTF + IBL load; plus the pod's lib/link-flag list.
Note: for A2 (Android basic quad) none of this is required — `filamat-android` builds the
material at runtime.

## 9. Versions
Kotlin 2.4.0 · Compose MP 1.11.1 · AGP 9.0.1 · coroutines 1.9.0 · minSdk 24 / compileSdk 36 ·
Filament 1.72.0 (live on Android, GL backend) · package `com.sadvakassov.filament.kmp`.

## 10. Gotchas / lessons
- Build environment varies by machine: some setups have full network + warm Gradle caches and
  build from the shell, others are restricted (no aapt2 / no Kotlin/Native dist) and `./gradlew`
  fails. Confirm which at session start before trusting a build result (see CLAUDE.md). Task
  names use the new `com.android.kotlin.multiplatform.library` plugin: `:shared:compileAndroidMain`,
  `:androidApp:assembleDebug`, `:shared:compileCommonMainKotlinMetadata` (NOT `compileDebugKotlinAndroid`).
- The A1 2D-Canvas fake is **gone as of A2** — the on-screen card is now real Filament output.
  (Historical: A1 squeezed width via `|cos|` to fake yaw; that code no longer renders.)
- Old `androidx.compose.ui.interop.UIKitView` is deprecated → use `androidx.compose.ui.viewinterop`.
- Compose MP 1.11.1 changed `UIKitInteropProperties`: there is no `isInteractive` in the default
  constructor anymore. For a passive native view (touches fall through to Compose gestures) pass
  `interactionMode = null`. (Caught on the first-ever iOS compile; the placeholder predated 1.11.1.)
- iOS Kotlin (`iosMain` placeholder) compiles on `iosSimulatorArm64` + `iosArm64` once Xcode +
  K/N dist are present. First native build downloads the K/N toolchain (minutes); later builds are
  seconds. Verify with `:shared:compileKotlinIosSimulatorArm64` / `:shared:compileKotlinIosArm64`.
- Shell gotcha: `./gradlew … | tee log | tail` reports the **pipe's** exit code (`tail` = 0), so a
  failed Gradle build looks like it passed. Capture `$?` of gradle directly (redirect, then tail),
  not through a pipe, when you need a trustworthy exit code.
