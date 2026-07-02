import UIKit
import Shared

/**
 * Swift implementation of the Kotlin `RevealSceneBridge` (declared in `iosMain`). Owns the native
 * `RevealCardView` and is injected into `MainViewController(...)` from `ContentView`. Kotlin calls
 * these methods each frame with the shared, already-eased channels; the work happens in the
 * Objective-C++ shim. Filament (C++/Metal) stays entirely on the app side — Kotlin/Native never
 * links against it.
 */
final class RevealSceneBridgeImpl: RevealSceneBridge {
    private var view: RevealCardView?

    func makeView(onReady: @escaping () -> Void) -> UIView {
        let v = RevealCardView(frame: .zero)
        v.onReady = onReady
        self.view = v
        return v
    }

    func update(
        shakeX: Float,
        boxBobY: Float,
        boxScale: Float,
        boxSplit: Float,
        seamGlow: Float,
        boxOpacity: Float,
        cardVisible: Bool,
        inspect: Bool,
        cardRise: Float,
        cardSpinYaw: Float,
        cardYaw: Float,
        cardPitch: Float,
        cardScale: Float
    ) {
        view?.updateShake(
            shakeX,
            bob: boxBobY,
            scale: boxScale,
            split: boxSplit,
            glow: seamGlow,
            opacity: boxOpacity,
            cardVisible: cardVisible,
            inspect: inspect,
            cardRise: cardRise,
            spinYaw: cardSpinYaw,
            yaw: cardYaw,
            pitch: cardPitch,
            cardScale: cardScale
        )
    }

    func dispose() {
        view?.dispose()
        view = nil
    }
}
