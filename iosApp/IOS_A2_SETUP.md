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
   Compile Sources* for the `iosApp` target. (Target membership can only be set in the GUI — this
   is the one irreducible manual step.)

3. **Build settings — mostly already done, nothing to click:**
   - **Bridging header**: pre-wired in `Configuration/Config.xcconfig`
     (`SWIFT_OBJC_BRIDGING_HEADER = $(SRCROOT)/iosApp/iosApp-Bridging-Header.h`) — no GUI step.
   - **C++ dialect**: already `gnu++20` in the project — no change.
   - **C++ standard library**: `libc++` is the default — no change.
   - Leave the existing `Shared.framework` gradle embed (`embedAndSignAppleFrameworkForXcode`
     build phase) untouched; CocoaPods coexists with it.

4. **Frameworks**: the Filament pod links its static libs; `Metal`/`MetalKit` come in via
   `#import <MetalKit/MetalKit.h>` + the pod. Add them under *Frameworks, Libraries, and Embedded
   Content* only if the linker complains.

5. **Run** on a **real device or arm64 simulator** (Apple-silicon Mac). Metal in older Intel
   simulators is flaky; the project targets `iosArm64` + `iosSimulatorArm64`.

> **Pod version caveat:** the `Podfile` pins `pod 'Filament', '1.72.0'`. If `pod install` says that
> version isn't published, use the closest available (e.g. drop the pin or `~> 1.72`) — iOS is
> self-contained and need not byte-match Android's runtime. Note the version actually used.

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
