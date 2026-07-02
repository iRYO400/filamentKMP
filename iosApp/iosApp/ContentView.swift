import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        // Inject the native Filament bridges so the iOS scenes render real 3D.
        // Pass nil for either to fall back to that scene's 2D placeholder.
        MainViewControllerKt.MainViewController(
            cardBridge: CardSceneBridgeImpl(),
            revealBridge: RevealSceneBridgeImpl()
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}