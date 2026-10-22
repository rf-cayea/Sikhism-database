import CoreText
import SwiftUI

@main
struct ShabadOSApp: App {
  init() {
    Fonts.register()
  }

  var body: some Scene {
    WindowGroup {
      ReaderShell()
        .modifier(StartingSize())
        .modifier(AppliedAppearance())
    }
  }
}

/// Applies the `Appearance` setting to every window
/// (docs/requirements/display-controls.md#appearance), so the whole app follows it,
/// sheets and menus included.
///
/// **The window's `overrideUserInterfaceStyle`, not `.preferredColorScheme`**: going
/// back to Auto must hand the choice to the system again, which `nil` passed to
/// `.preferredColorScheme` has not reliably done once a scheme was set.
private struct AppliedAppearance: ViewModifier {
  @AppStorage(Appearance.key) private var appearance = Appearance.auto.rawValue

  func body(content: Content) -> some View {
    content.onChange(of: appearance, initial: true) { _, raw in
      let style = (Appearance(rawValue: raw) ?? .auto).interfaceStyle
      for case let scene as UIWindowScene in UIApplication.shared.connectedScenes {
        for window in scene.windows { window.overrideUserInterfaceStyle = style }
      }
    }
  }
}

/// Saves the reader's starting size the first time the app opens, from the
/// device and the system Text Size setting
/// (docs/requirements/display-controls.md#starting-size).
///
/// Written once and never recomputed. Someone who later changes their Text Size
/// keeps the reader size they have, and a size they set by pinch is never
/// overwritten. Attached at the root so it runs before the reader can open; the
/// reader itself only ever reads the saved value.
struct StartingSize: ViewModifier {
  @Environment(\.dynamicTypeSize) private var dynamicTypeSize

  func body(content: Content) -> some View {
    content.onAppear {
      let defaults = UserDefaults.standard
      guard defaults.object(forKey: BaniReaderView.fontSizeKey) == nil else { return }
      defaults.set(Self.deviceSize(at: dynamicTypeSize), forKey: BaniReaderView.fontSizeKey)
    }
  }

  /// The starting size this device is given at `dynamicTypeSize`: on a first launch,
  /// and again by Settings' Reset (reading-shell.md#settings).
  ///
  /// The Body size iOS uses at that Text Size setting is asked for explicitly by
  /// setting rather than through UITraitCollection.current, which is not guaranteed
  /// to be populated inside onAppear.
  @MainActor static func deviceSize(at dynamicTypeSize: DynamicTypeSize) -> Double {
    let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(dynamicTypeSize))
    let body = UIFont.preferredFont(forTextStyle: .body, compatibleWith: traits).pointSize
    return DesignTokens.startingSize(isPad: UIDevice.current.userInterfaceIdiom == .pad, bodySize: body)
  }
}

/// Sant Lipi is bundled rather than relying on system Gurmukhi fonts, whose
/// coverage of conjuncts and yayya variants is poor on both platforms.
enum Fonts {
  /// Resolved from the font file rather than hardcoded, because `Font.custom`
  /// needs the PostScript name and a variable font's is not reliably guessable
  /// from the family name. A wrong guess fails silently into a system fallback,
  /// which is precisely the rendering this bundle exists to avoid.
  private(set) static var gurmukhi = "Sant Lipi"

  /// A font at an exact variable-axis weight.
  ///
  /// SwiftUI's `.fontWeight()` only reaches the nine named weights, and Sant Lipi's
  /// `wght` axis is continuous 100–900 — 550 is not `.medium` rounded, it is 550.
  /// Reaching it means a CoreText descriptor keyed by the axis's four-character tag.
  ///
  /// Falls back to the plain font if the variation cannot be applied, so a font
  /// without the axis renders at its default weight rather than not at all.
  static func variable(_ name: String, size: Double, weight: Double) -> Font {
    let wght = 0x77_67_68_74  // 'wght'
    let descriptor = UIFontDescriptor(fontAttributes: [
      .name: name,
      kCTFontVariationAttribute as UIFontDescriptor.AttributeName: [wght: weight],
    ])
    return Font(UIFont(descriptor: descriptor, size: size))
  }

  /// Registered at app init, not `onAppear` — a view that renders before
  /// registration completes falls back to the system font for its first frame.
  static func register() {
    guard let url = Bundle.main.url(forResource: "SantLipi-VF", withExtension: "ttf")
    else {
      assertionFailure("SantLipi-VF.ttf missing from bundle")
      return
    }

    CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)

    if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL)
      as? [CTFontDescriptor],
      let first = descriptors.first,
      let name = CTFontDescriptorCopyAttribute(first, kCTFontNameAttribute) as? String
    {
      gurmukhi = name
    }
  }
}
