import UIKit
import Shared

/**
 * Swift implementation of the Kotlin `CardSceneBridge` (declared in `iosMain`). It owns the
 * native `FilamentCardView` and is injected into `MainViewController(bridge:)` from
 * `ContentView`. Kotlin calls these methods; the work happens in the Objective-C++ shim.
 *
 * Keeping this on the app side is what lets us use Filament (C++/Metal) without Kotlin/Native
 * ever linking against it — the documented iOS seam (PROJECT_CONTEXT §6).
 */
final class CardSceneBridgeImpl: CardSceneBridge {
    private var view: FilamentCardView?

    func makeView() -> UIView {
        let v = FilamentCardView(frame: .zero)
        self.view = v
        return v
    }

    func update(yaw: Float, pitch: Float, scale: Float) {
        view?.setYaw(yaw, pitch: pitch, scale: scale)
    }

    func dispose() {
        view?.dispose()
        view = nil
    }
}
