import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        // Inject the native Filament bridge so the iOS CardScene renders real 3D.
        // Pass nil here to fall back to the 2D placeholder.
        MainViewControllerKt.MainViewController(bridge: CardSceneBridgeImpl())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}