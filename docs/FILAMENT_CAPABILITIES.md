# Filament capabilities — the Android + iOS common set

> Reference doc for this project. Scope: **what Filament can do on _both_ Android and iOS at
> once** — the safe cross-platform surface we can build on with one shared scene. Pinned to
> **Filament 1.72.0** (our runtime). Re-verify against the release notes before relying on an
> edge feature; the engine moves fast. Sources at the bottom.

## TL;DR
Filament abstracts the GPU behind a backend, so at a given **feature level** the *feature set is
essentially identical across platforms* — that's the whole reason "swap the scene, not the
architecture" works. The real cross-platform variables are: (1) which **backend** runs, (2) which
**feature level** the device reports, and (3) a few **format/limit** differences. Everything else
(PBR, lighting, shadows, post-FX, glTF) is available on both.

## 1. Backends per platform
| Platform | Backends Filament supports | What THIS project uses |
|---|---|---|
| **Android** | OpenGL ES 3.0+, **Vulkan 1.0** | OpenGL ES (`Engine.create()` default) |
| **iOS** | OpenGL ES 3.0+, **Metal** | Metal (`Engine::create(Backend::METAL)`) |

Notes:
- We deliberately run **different backends per platform** (GL / Metal) and still share one scene —
  proof the abstraction holds. Vulkan on Android is available but unused; GL on iOS exists but is
  Apple-deprecated, so Metal is the right default there.
- Min OS: **iOS 11.0+**. Android: GLES 3.0 (≈ API 18+; we target minSdk 24, well above).

## 2. Feature levels — the backbone of capability detection
Filament grades devices into **feature levels** (`Engine.getSupportedFeatureLevel()`). A level must
be *set* before its features can be used; default is FL1 (or FL0 on pre-GLES-3.0 devices).

| Level | Means (verbatim from `DriverEnums.h`) | Cross-platform reality |
|---|---|---|
| **FEATURE_LEVEL_0** | "OpenGL ES 2.0 features" | **GL-only, ancient.** Metal/Vulkan never report this. No uniform buffers → most modern material features off. |
| **FEATURE_LEVEL_1** | "OpenGL ES 3.0 features (default)" | **Baseline everywhere.** Metal's *minimum*. Full PBR, IBL, shadows, post-FX. 16 vertex + 16 fragment samplers. |
| **FEATURE_LEVEL_2** | "OpenGL ES 3.1 features + 16 texture units + cubemap arrays" | Modern Android/iOS. Adds cubemap arrays, compute-era features. |
| **FEATURE_LEVEL_3** | "OpenGL ES 3.1 features + 31 texture units + cubemap arrays" | 31 samplers "guaranteed by Metal" → effectively high-end **iOS/Metal & Vulkan/desktop**; Android GLES typically caps at FL2. |

`FEATURE_LEVEL_CAPS` sampler guarantees (from source): FL1 `{16,16}`, FL2 `{16,16}` ("guaranteed by
OpenGL ES, Vulkan, Metal & WebGPU"), FL3 `{31,31}` ("guaranteed by Metal"). Global hard caps:
`MAX_VERTEX_ATTRIBUTE_COUNT = 16`, `MAX_SAMPLER_COUNT = 62`, `MAX_SSBO_COUNT = 4`.

**Takeaway for us:** FL1 is the floor for a real experience on both platforms; FL2+ is the
headroom. This is exactly what the Phase E splash-gate classifies (see PROJECT_CONTEXT §3 Phase E).

## 3. The common feature set (works on both, FL1+)
All of this is exposed identically through the shared Filament API on Android-GL and iOS-Metal:

**Shading / materials**
- Cook-Torrance microfacet specular BRDF + Lambertian diffuse; **metallic-roughness** workflow.
- Clear coat, anisotropy, **approximated subsurface/translucency**, cloth/fabric/sheen.
- UNLIT and LIT shading models (we're on UNLIT today; LIT is Phase B).
- Runtime material compilation via **filamat** (`MaterialBuilder`) on both platforms — no `matc`
  needed at runtime (this is how our card material is built live on each platform).

**Lighting**
- Directional, point, spot lights **with shadows**; image-based lighting (IBL) with custom surface
  shading; physical light units; physically-based camera (shutter/ISO/aperture).

**Shadows**
- Cascaded shadow maps; **EVSM, PCSS, DPCF, PCF**; colored penumbra, transparent & contact shadows.

**Screen-space & post-processing**
- Ambient occlusion (SSAO), screen-space reflections, refraction; global **fog** (with FSR dynamic
  resolution); **HDR bloom**, depth of field, screen-space lens flares.
- Tone mappers: GT7, PBR Neutral, AgX, generic, ACES, filmic; full color grading.
- Anti-aliasing: **TAA, FXAA, MSAA**.

**Geometry / content (gltfio)**
- glTF 2.0 loading; **skinning + joint animation, morph targets** (incl. sparse accessors),
  transform animation (linear interp).
- glTF extensions: Draco compression, KHR clearcoat/transmission/sheen/IOR/etc., mesh quantization,
  **Basis (KTX2) texture compression** — the portable path for cross-platform textures.
- **GPU instancing** (automatic + manual).

> None of the above is platform-forked in our code: enabling it = swapping the material/model and
> flipping `View` options, per the project's "add visuals = swap, not rearchitect" thesis.

## 4. Per-platform caveats (the honest differences)

### Platform limits at a glance
| Axis | Android (GL ES) | iOS (Metal) | Rule of thumb |
|---|---|---|---|
| **Max feature level** | ~**FL2** (GLES caps here) | **FL3** | Design "high" for ≤16 samplers → runs on both |
| **Samplers (vtx/frag)** | 16 / 16 | 16 (FL1–2) → **31 (FL3)** | Don't rely on >16 cross-platform |
| **Min feature level** | **FL0** (GLES 2.0 devices) | **FL1** (Metal floor) | FL0 / no-init ⇒ `zero` tier |
| **Compressed textures** | ETC2 (+usually ASTC) | ASTC + PVRTC (+ETC2 A-series) | Ship **KTX2/Basis**, transcode via gltfio |
| **Material `targetApi`** | `OPENGL` | `METAL` | Must match active backend or card is blank |
| **Min OS** | GLES 3.0 (≈API 18; we're minSdk 24) | **iOS 11+** | — |
| **Pause mechanism** | Choreographer callback | `MTKView.paused` | Same behavior, different hook |

Global hard caps (both): vertex attributes **16**, total samplers **62**, SSBO **4**.

### Details / gotchas
Same features, but a few things genuinely differ — check these when a feature matters:
- **Compressed texture formats:** Android GLES guarantees ETC2 (and usually ASTC); iOS Metal has
  ASTC + PVRTC (+ ETC2 on A-series). Ship **KTX2/Basis-Universal** and let gltfio transcode to the
  device's supported format rather than committing to one — the only fully portable choice.
- **FL3 is Metal-leaning:** the 31-sampler tier is guaranteed by Metal; don't assume Android
  reaches it. Design the "high" path to need ≤16 samplers so it runs on both.
- **Backend defaults differ:** we pass `targetApi(OPENGL)` to the material on Android and Metal on
  iOS — the material must be built for the active backend. A single wrong `targetApi` = blank card.
- **External/protected textures, HDR display, stereo:** platform- and device-gated — query, don't
  assume. `isStereoSupported(type)` exists for the stereo case.
- **Min OS / lifecycle:** iOS 11+; background handling differs (iOS pauses via `MTKView.paused`,
  Android via the Choreographer callback) — behaviorally equivalent, mechanically not.

## 5. Capability-probe APIs (feed Phase E tiering)
From `Engine` (available on both platforms via the shared Filament API):
- `getSupportedFeatureLevel()` → max FL the device+backend supports. **Primary tier signal.**
- `getActiveFeatureLevel()` / `setActiveFeatureLevel(level)` → what we've actually turned on.
- `isStereoSupported(StereoscopicType)` → stereo capability.
- `getMaxAutomaticInstances()` → instancing headroom.
- Backend/limit constants (`FEATURE_LEVEL_CAPS`, `MAX_*`) bound what a tier can request.

These are exactly the inputs the **Phase E splash capability-detection** turns into a
`zero / low / high` tier (see PROJECT_CONTEXT §3 Phase E). Keep the *classification* (an enum + the
probed numbers) in `sharedLogic`; only the raw `Engine` query is platform-side.

## Sources
- Filament README (feature list, backend/platform matrix, min versions):
  https://github.com/google/filament/blob/main/README.md
- `Engine.h` (feature-level + capability query API):
  https://github.com/google/filament/blob/main/filament/include/filament/Engine.h
- `DriverEnums.h` (`FeatureLevel` enum + `FEATURE_LEVEL_CAPS`):
  https://github.com/google/filament/blob/main/filament/backend/include/backend/DriverEnums.h
- Verified against **Filament 1.72.0** (this project's pinned runtime), fetched 2026-07.
