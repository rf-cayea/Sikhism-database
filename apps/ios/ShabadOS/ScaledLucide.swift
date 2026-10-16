import SwiftUI

/// A Lucide icon that follows the person's Text Size, as an SF Symbol does.
///
/// An asset image is drawn at its own size whatever the setting, so a Lucide icon
/// beside SF Symbols stayed the same size while they grew (iPhone, 2026-10-16).
/// This draws its 24pt artwork at 24pt at the default Text Size and scales it with
/// the Body text style from there. Use it wherever `Image(lucide:)` would be shown.
struct ScaledLucide: View {
  let icon: LucideIcon

  @ScaledMetric(relativeTo: .body) private var size: CGFloat = 24

  init(_ icon: LucideIcon) {
    self.icon = icon
  }

  var body: some View {
    Image(lucide: icon)
      .resizable()
      .scaledToFit()
      .frame(width: size, height: size)
  }
}
