import SwiftUI

/// The bundled banis as rows, for the Journeys stand-in (`JourneysPanel`).
/// Tapping one opens it in the viewer.
///
/// Plain buttons in a stack, not `List` rows; see `JourneysPanel` for why.
struct BaniRows: View {
  /// The bani the viewer is showing, drawn as selected.
  let selectedID: String?
  /// Where each row's text starts, shared with the panel's header and label.
  let inset: CGFloat
  let open: (Bani) -> Void

  /// The system's Body size, following the person's Text Size. Gurmukhi is set at
  /// `uiGurmukhiRatio` × it, at `weightPrimary` (packages/design/tokens.md#type).
  @ScaledMetric(relativeTo: .body) private var bodySize: CGFloat = 17

  var body: some View {
    ForEach(Array(Corpus.banis.enumerated()), id: \.element.id) { index, bani in
      if index > 0 {
        Divider().padding(.horizontal, inset)
      }
      // Gurmukhi only (chosen 2026-10-16).
      Button { open(bani) } label: {
        Text(bani.gurmukhi)
          .font(Fonts.variable(
            Fonts.gurmukhi,
            size: bodySize * DesignTokens.uiGurmukhiRatio,
            weight: DesignTokens.weightPrimary
          ))
          .foregroundStyle(DesignTokens.foreground)
          .frame(maxWidth: .infinity, alignment: .leading)
          .padding(.horizontal, inset)
          .padding(.vertical, 14)
          .contentShape(Rectangle())
      }
      .buttonStyle(RowStyle(selected: selectedID == bani.id, inset: inset))
    }
  }
}

/// No card behind a row (2026-10-16). The bani being read, and a row being
/// pressed, take the toner wash in the reader's card shape, bled outward from the
/// text as a tapped line's is.
private struct RowStyle: ButtonStyle {
  let selected: Bool
  let inset: CGFloat

  private static let bleed: CGFloat = 8
  private static let cornerRadius: CGFloat = 12

  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .background(
        RoundedRectangle(cornerRadius: Self.cornerRadius)
          .fill(selected || configuration.isPressed ? DesignTokens.toner : .clear)
          .padding(.horizontal, inset - Self.bleed)
      )
  }
}
