# iOS A2 — wiring real Filament into the Xcode app

The Kotlin/Compose seam (committed) injects a native `CardSceneBridge` and hosts whatever
`UIView` it makes. This doc is the **Xcode-side setup** to make that view a real Metal/Filament
renderer. None of it can be done from the headless shell — it needs Xcode + network (`pod install`).

Files already generated in the repo:

| File | Role |
|---|---|
| `iosApp/iosApp/filament/FilamentCardView.h` | ObjC interface of the Metal/Filament view (Swift-importable) |
| `iosApp/iosApp/filament/FilamentCardView.mm` | ObjC++ impl — Filament Engine (Metal), quad, runtime material, MTKView vsync loop |
| `iosApp/iosApp/CardSceneBridgeImpl.swift` | Swift impl of the Kotlin `CardSceneBridge`, owns the view |
| `iosApp/iosApp/iosApp-Bridging-Header.h` | exposes the ObjC shim to Swift |
| `iosApp/Podfile` | pulls `pod 'Filament'` (engine + filamat) |
| `ContentView.swift` | now calls `MainViewController(bridge: CardSceneBridgeImpl())` |

## Steps

1. **Install the pod**
   ```bash
   sudo gem install cocoapods      # once, if needed
   cd iosApp
   pod install
   ```
   From now on open **`iosApp.xcworkspace`**, not `iosApp.xcodeproj`.

2. **Add the new files to the `iosApp` target** (Xcode → right-click the `iosApp` group → *Add Files…*):
   - `filament/FilamentCardView.h`, `filament/FilamentCardView.mm`
   - `CardSceneBridgeImpl.swift`
   - `iosApp-Bridging-Header.h`
   Confirm `FilamentCardView.mm` and `CardSceneBridgeImpl.swift` appear in *Build Phases →
   Compile Sources* for the `iosApp` target.

3. **Build settings** (target `iosApp`):
   - **Objective-C Bridging Header** = `iosApp/iosApp-Bridging-Header.h`
   - **C++ Language Dialect** = `GNU++17` (or `c++17`)
   - **C++ and Objective-C Interoperability** / **C++ Standard Library** = `libc++`
   - Make sure the existing `Shared.framework` embed (Compose) is intact.

4. **Frameworks**: the Filament pod links its static libs; also ensure `Metal` and `MetalKit`
   are linked (the `#import <MetalKit/MetalKit.h>` + pod usually pull these in; add them under
   *Frameworks, Libraries, and Embedded Content* if the linker complains).

5. **Run** on a **real device or arm64 simulator** (Apple-silicon Mac). Metal in older Intel
   simulators is flaky; the project targets `iosArm64` + `iosSimulatorArm64`.

## What you should see
A solid green card on a dark-navy background, reacting to the same drag/tap/flick as Android
(the shared gesture + inertia logic already drives it). HUD readout works identically.

## If it doesn't compile / run — report back
The `.mm` follows Filament 1.72.0 + the official iOS sample patterns, but it was **not compilable
from the shell**. Likely first-build fixups: exact filamat `build()` argument, `Package` getters
(`getData`/`getSize`), or `createSwapChain` layer cast. Paste the Xcode error and we'll fix it
iteratively — the Kotlin seam won't need to change.

## Scope (same as Android A2)
UNLIT, double-sided, flat quad, no lighting/PBR/thickness — those are Phase B. This proves the
interop seam on platform #2; the engine lives in Swift, `commonMain` is untouched.
