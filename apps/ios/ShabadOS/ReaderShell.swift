import Combine
import SwiftUI

/// When someone last used the app, for the journey's idle boundary.
///
/// **A plain class, deliberately not observed**, like `RowFrames`: it is written on
/// every tap, scroll and pinch, and must not re-render anything.
final class ActivityClock {
  private(set) var last = Date.now
  func touch() { last = .now }
}

/// Whether the reader's pinch is in progress, so the sidebar drag can stand aside
/// for it: two fingers spreading sideways would otherwise read as a drag.
///
/// **A plain class, deliberately not observed**, like `ActivityClock`.
final class PinchFlag {
  var active = false
}

/// The app: the viewer, lying over the Journeys sidebar. "The viewer is the app"
/// (docs/requirements/reading-shell.md): it is what the app opens to, and the
/// sidebar is never a destination of its own.
///
/// **Opening Journeys slides the viewer aside** to show the sidebar beneath it,
/// leaving `peek` of the viewer's inner edge in view, faded towards the
/// background. Tapping that edge returns to the viewer. The sidebar does not move:
/// it starts washed in the page colour and comes up to full strength as the viewer
/// moves away (`sidebarStrength`).
///
/// **What is being read is held in memory only.** Journeys are not stored yet, so a
/// cold launch, including after a force quit, is a new journey and shows the
/// default screen. A journey also ends after `idleLimit` without activity, open or
/// in the background (docs/requirements/journeys.md#journeys).
struct ReaderShell: View {
  /// The bani being read, by `Bani.id`; nil shows the default screen.
  @State private var baniID: String?
  /// The bani of the journey that ended, offered by the default screen's
  /// `continue reading`. Nil until a journey has ended since launch.
  @State private var previousBaniID: String?
  @State private var journeysOpen = false
  /// The drag's horizontal travel while it is moving the viewer; nil otherwise.
  @State private var dragX: CGFloat?
  /// What the current drag was judged to be, once it has moved far enough to tell.
  @State private var dragAxis: DragAxis?
  /// `@State` only to keep one instance for the view's lifetime.
  @State private var activity = ActivityClock()
  /// `@State` only to keep one instance for the view's lifetime.
  @State private var pinch = PinchFlag()
  @Environment(\.scenePhase) private var scenePhase

  private enum DragAxis { case horizontal, other }

  /// Settled in docs/requirements/journeys.md#journeys: 15 minutes without
  /// activity ends a journey, including while the app is open.
  static let idleLimit: TimeInterval = 15 * 60

  /// How much of the viewer stays in view while Journeys is open. First pass,
  /// matched by eye to the sliver other apps leave (2026-10-15).
  private static let peek: CGFloat = 72
  /// How far a drag moves before it is judged horizontal or not. Below this a
  /// finger has not said which way it is going.
  private static let dragDecision: CGFloat = 12
  /// How visible the sidebar is with the viewer covering it, and how far the
  /// viewer has slid by the time it is fully visible (chosen 2026-10-16).
  private static let sidebarStartStrength: Double = 0.4
  private static let sidebarFullAt: Double = 0.5

  /// The sidebar's visibility at `progress`: `sidebarStartStrength` at rest,
  /// rising evenly to full at `sidebarFullAt`, full from there on.
  private static func sidebarStrength(_ progress: CGFloat) -> Double {
    let rise = min(1, Double(progress) / sidebarFullAt)
    return sidebarStartStrength + (1 - sidebarStartStrength) * rise
  }

  /// The sidebar's size with the viewer covering it; full size is reached only
  /// when the viewer is fully aside, though the sidebar is fully visible from
  /// `sidebarFullAt`. First pass (2026-10-16).
  private static let sidebarStartScale: CGFloat = 0.95

  private static func sidebarScale(_ progress: CGFloat) -> CGFloat {
    sidebarStartScale + (1 - sidebarStartScale) * progress
  }

  /// The sidebar's colour: `backgroundBase`, white in light mode and #222 in
  /// dark (chosen 2026-10-16), so it reads apart from the page-coloured viewer.
  /// Android is to take Material You's dynamic colour here instead
  /// (packages/design/tokens.md#colour).
  fileprivate static let sidebarBackground = DesignTokens.backgroundBase

  /// The least corner radius the viewer keeps while it is moved; see `cardShape`.
  /// The display radius of the iPhone it was checked on (2026-10-16).
  private static let movingCornerRadius: CGFloat = 55

  /// How often an open app checks the boundary, so a journey ends within this
  /// long of reaching it. A timer does not run in the background; returning to
  /// the app checks at once (see `body`).
  private let idleCheck = Timer.publish(every: 10, on: .main, in: .common).autoconnect()

  var body: some View {
    GeometryReader { geometry in
      let travel = max(0, geometry.size.width - Self.peek)
      let progress = openness(travel: travel)
      let sidebarScale = Self.sidebarScale(progress)

      // Two whole screens, one over the other. Each is laid out in the safe area,
      // as any screen is, and draws past it (under the bars) as any screen does.
      // **Nothing here may clip to a layer's layout frame**: that frame stops at
      // the safe area, so a clip there cuts the text off below the status bar and
      // leaves a gap above the home indicator. Every overlay and mask that must
      // cover a whole layer therefore ignores the safe area itself.
      ZStack(alignment: .leading) {
        // Beneath: the sidebar, on its own colour across the whole screen.
        ZStack(alignment: .leading) {
          Self.sidebarBackground.ignoresSafeArea()
          JourneysPanel(readingID: baniID, open: open)
            // As wide as the viewer leaves uncovered, so nothing in it sits under the peek.
            .frame(width: travel)
            // Grows to full size as the viewer slides, outward from its right
            // edge: its top rises, its bottom falls and its left edge moves left. Only the panel scales; the
            // colour behind it stays full screen, so no edge shows.
            //
            // **Laid out at full size throughout; only its drawing is scaled.**
            // `geometryGroup` settles the panel's own layout as one unit, so
            // nothing inside it moves or re-lays out while the scale changes,
            // and `visualEffect` scales the finished drawing without telling the
            // panel its size changed. Reaching full size is then only the scale
            // reaching 1: nothing in the list redraws or shifts.
            .geometryGroup()
            .visualEffect { content, _ in
              content.scaleEffect(sidebarScale, anchor: .trailing)
            }
        }
        // Washed in its own colour rather than dimmed towards black, so it reads
        // the same in light and dark.
        .overlay {
          Self.sidebarBackground.opacity(1 - Self.sidebarStrength(progress))
            .ignoresSafeArea()
            .allowsHitTesting(false)
        }

        NavigationStack {
          viewer
            .toolbar {
              ToolbarItem(placement: .topBarLeading) {
                Button("Journeys", systemImage: AppIcons.journeys) { setJourneys(open: !journeysOpen) }
              }
            }
        }
        .overlay {
          DesignTokens.background.opacity(0.6 * progress)
            .ignoresSafeArea()
            .allowsHitTesting(false)
        }
        // A mask that ignores the safe area, not `clipShape`, for the reason at the top.
        .mask { cardShape(moved: progress > 0).ignoresSafeArea() }
        // A hairline along the viewer's edge, so it reads as a card's edge, not
        // only a shadow's.
        .overlay {
          cardShape(moved: progress > 0)
            .stroke(DesignTokens.foreground.opacity(0.08 * progress), lineWidth: 0.5)
            .ignoresSafeArea()
            .allowsHitTesting(false)
        }
        .shadow(color: .black.opacity(0.18 * progress), radius: 24)
        // Moved when drawn, not laid out somewhere else: see `cardShape`. Its
        // layout stays where it was, so it takes no touches once moved; the
        // sidebar beneath does, and the edge below returns to it.
        .visualEffect { content, _ in content.offset(x: travel * progress) }
        .allowsHitTesting(progress == 0)
        // A horizontal drag moves the viewer; stop it scrolling meanwhile.
        .scrollDisabled(dragX != nil)

        // While open, the visible edge, Journeys button included, is one target
        // that returns to the viewer.
        if journeysOpen && dragX == nil {
          Color.clear
            .contentShape(Rectangle())
            .frame(width: Self.peek)
            .frame(maxWidth: .infinity, alignment: .trailing)
            .ignoresSafeArea()
            .onTapGesture { setJourneys(open: false) }
        }
      }
      .simultaneousGesture(sidebarDrag(travel: travel))
    }
    .ignoresSafeArea(.container, edges: .horizontal)
    // On opening only (chosen 2026-10-16).
    .sensoryFeedback(.impact(weight: .light), trigger: journeysOpen) { _, isOpen in isOpen }
    .onReceive(idleCheck) { _ in endJourneyIfIdle() }
    .onChange(of: scenePhase) { _, phase in
      if phase == .active { endJourneyIfIdle() }
    }
  }

  /// The viewer's outline: **the display's own corners, kept as it moves.**
  ///
  /// `ConcentricRectangle` matches the display exactly at rest (checked against
  /// UIKit's own radius on an iPhone, 2026-10-16). Two things keep it once moved:
  ///
  /// - **The viewer is moved with `visualEffect`, not `offset`.** A concentric
  ///   corner shrinks by its distance from the display's corner, and `offset`
  ///   moves the view's frame, so the corners went square as it slid. A visual
  ///   effect moves only the drawing, so the shape is worked out where the viewer
  ///   rests.
  /// - **While moved, the corners keep at least `movingCornerRadius`**, should the
  ///   shape be worked out from the moved position after all. At rest there is no
  ///   minimum, so a display with smaller corners, or none, is matched exactly.
  ///
  /// Not a radius read from the display: `GeometryProxy.concentricCornerRadii`
  /// (iOS 27) came back empty on a full-screen view, and UIKit's own value is private.
  private func cardShape(moved: Bool) -> ConcentricRectangle {
    ConcentricRectangle(corners: moved ? .concentric(minimum: .fixed(Self.movingCornerRadius)) : .concentric)
  }

  @ViewBuilder
  private var viewer: some View {
    if let bani = Self.bani(baniID) {
      BaniReaderView(bani: bani, activity: activity, pinch: pinch)
        // A different bani is a new reader: its row frames, held line and
        // expanded continuations all belong to the bani they were read from.
        .id(bani.id)
    } else {
      DefaultScreen(continueWith: Self.bani(previousBaniID), open: open)
    }
  }

  /// 0 with the viewer covering the sidebar, 1 with it aside; between while dragged.
  private func openness(travel: CGFloat) -> CGFloat {
    let base: CGFloat = journeysOpen ? 1 : 0
    guard let dragX, travel > 0 else { return base }
    return min(1, max(0, base + dragX / travel))
  }

  /// A drag anywhere opens or closes Journeys: rightward on the viewer, leftward
  /// on the sidebar or the viewer's edge. On iOS it may start at the screen edge
  /// as well as inside the content: the reader has no back button, so there is no
  /// system back-swipe to collide with (docs/interaction.md).
  ///
  /// **Only a horizontal drag counts**, judged once it has moved `dragDecision`:
  /// anything steeper is a scroll and is left alone for its whole length. A drag
  /// that has started is dropped if a pinch begins.
  private func sidebarDrag(travel: CGFloat) -> some Gesture {
    DragGesture(minimumDistance: 0)
      .onChanged { value in
        if pinch.active {
          dragAxis = .other
          if dragX != nil { withAnimation(.smooth) { dragX = nil } }
          return
        }
        let dx = value.translation.width
        let dy = value.translation.height
        if dragAxis == nil {
          guard max(abs(dx), abs(dy)) >= Self.dragDecision else { return }
          // The direction must be one that can move the viewer from where it is.
          let towardsChange = journeysOpen ? dx < 0 : dx > 0
          dragAxis = abs(dx) > abs(dy) * 1.5 && towardsChange ? .horizontal : .other
        }
        guard dragAxis == .horizontal else { return }
        dragX = dx
      }
      .onEnded { value in
        defer { dragAxis = nil }
        guard dragAxis == .horizontal, travel > 0 else { return }
        // Where a flick would carry it, not only where the finger lifted, so a
        // short quick drag still opens or closes.
        let base: CGFloat = journeysOpen ? 1 : 0
        let landing = base + value.predictedEndTranslation.width / travel
        activity.touch()
        withAnimation(.smooth) {
          journeysOpen = landing > 0.5
          dragX = nil
        }
      }
  }

  private static func bani(_ id: String?) -> Bani? {
    id.flatMap { id in Corpus.banis.first { $0.id == id } }
  }

  private func setJourneys(open: Bool) {
    activity.touch()
    withAnimation(.smooth) { journeysOpen = open }
  }

  /// Every path out of the sidebar closes it
  /// (docs/requirements/reading-shell.md#getting-between-them).
  private func open(_ bani: Bani) {
    activity.touch()
    baniID = bani.id
    withAnimation(.smooth) { journeysOpen = false }
  }

  private func endJourneyIfIdle() {
    guard Date.now.timeIntervalSince(activity.last) >= Self.idleLimit else { return }
    endJourney()
  }

  private func endJourney() {
    guard let baniID else { return }
    previousBaniID = baniID
    self.baniID = nil
  }
}

/// What the viewer shows when there is nothing to resume: **ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ**, pills
/// for a few banis, and `continue reading` naming the last journey's bani when
/// there is one (docs/requirements/reading-shell.md#the-default-screen). No title:
/// the title names what is being read, and nothing is.
private struct DefaultScreen: View {
  let continueWith: Bani?
  let open: (Bani) -> Void

  /// The system's Body text size: 17pt at the default Text Size, following the
  /// person's setting. The greeting and pills are sized from it, not from the
  /// reader's size, so pinching the reader does not change them (chosen 2026-10-16).
  @ScaledMetric(relativeTo: .body) private var uiSize: CGFloat = 17
  private static let greetingScale: CGFloat = 3.2
  private static let pillScale: CGFloat = 1.6
  /// Added to each side of a pill's text, beyond the glass button's own inset.
  /// First pass (2026-10-16).
  private static let pillPadding: CGFloat = 12

  /// The pills, by `Bani.id`, in order (chosen 2026-10-15). A fixed stand-in for
  /// the spec's "what is due now", which needs goals and schedules. Ardas was also
  /// chosen but is not bundled yet; an id not in the corpus is skipped, so it
  /// appears once `ARDS` is exported.
  private static let pills = ["JAPJ", "RHRS", "SHLA", "ARDS"]

  private var pillBanis: [Bani] {
    Self.pills.compactMap { id in Corpus.banis.first { $0.id == id } }
  }

  var body: some View {
    VStack(spacing: 24) {
      Text("ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ")
        .font(Fonts.variable(
          Fonts.gurmukhi,
          size: uiSize * Self.greetingScale,
          weight: DesignTokens.weightPrimary
        ))
        .foregroundStyle(DesignTokens.foreground)
        .multilineTextAlignment(.center)

      // In a row when they fit, stacked when they do not (narrow screens, large
      // Text Size).
      ViewThatFits(in: .horizontal) {
        HStack(spacing: 8) { pillButtons }
        VStack(spacing: 8) { pillButtons }
      }

      // Absent, not disabled, when there is nothing to continue: "an empty or dead
      // control on a first launch is worse than nothing".
      if let bani = continueWith {
        Button { open(bani) } label: {
          VStack(spacing: 2) {
            Text("Continue reading")
              .font(.headline)
            Text(bani.latin)
              .font(.subheadline)
              .foregroundStyle(DesignTokens.foregroundMuted)
          }
        }
        .buttonStyle(.glass)
        .tint(DesignTokens.ui)
      }
    }
    .padding()
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(DesignTokens.background)
    .navigationBarTitleDisplayMode(.inline)
  }

  private var pillButtons: some View {
    ForEach(pillBanis) { bani in
      // Gurmukhi in the greeting's weight and colour (chosen 2026-10-16).
      Button { open(bani) } label: {
        Text(bani.gurmukhi)
          .font(Fonts.variable(Fonts.gurmukhi, size: uiSize * Self.pillScale, weight: DesignTokens.weightPrimary))
          .foregroundStyle(DesignTokens.foreground)
          .padding(.horizontal, Self.pillPadding)
      }
      .buttonStyle(.glass)
      .lineLimit(1)
    }
  }
}

/// A stand-in for the Journeys sidebar (docs/requirements/reading-shell.md#the-journeys-sidebar),
/// which needs journeys, pins and the Library, none of which exist yet. It has the
/// specified header; the bani list stands in for the rest and should be deleted
/// when they land. `New journey` is left out until that band is designed. It lies
/// beneath the viewer; see `ReaderShell`.
///
/// **No toggle of its own.** The viewer's Journeys button stays in view on the
/// viewer's edge while this is open, and returns to the viewer.
///
/// **Plain SwiftUI views only: no `NavigationStack`, no `List`.** The panel is zoomed
/// as the viewer slides (`ReaderShell.sidebarScale`), and UIKit-backed containers
/// answered the zoom by laying themselves out again at the smaller size instead of
/// being drawn smaller: the text stayed full size while rows moved, and at full size
/// everything jumped and a row that had not fitted appeared (iPhone, 2026-10-16).
/// SwiftUI views are drawn scaled and never re-laid out. The header is a
/// `safeAreaBar`, which keeps the soft fade beneath it that a navigation bar gave.
private struct JourneysPanel: View {
  /// The bani the viewer is showing, drawn as selected.
  let readingID: String?
  let open: (Bani) -> Void

  /// The space on each side of the panel's content: one left edge for the logo,
  /// the section label and every row's text, and the same on the right
  /// (chosen 2026-10-16).
  static let inset: CGFloat = 30
  /// The header's height, a navigation bar's, so the name sits level with the
  /// viewer's Journeys button beside it.
  private static let headerHeight: CGFloat = 44

  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 0) {
        // Latin interface text at the system's Body size (`headline` is Body,
        // semibold); see packages/design/tokens.md#type.
        Text("Nitnem")
          .font(.headline)
          .foregroundStyle(DesignTokens.foregroundMuted)
          .padding(.horizontal, Self.inset)
          .padding(.bottom, 8)
        BaniRows(selectedID: readingID, inset: Self.inset, open: open)
      }
      .padding(.vertical, 12)
    }
    // The list fades under the header rather than being cut
    // (reading-shell.md#header, interaction.md).
    .scrollEdgeEffectStyle(.soft, for: .top)
    .safeAreaBar(edge: .top) { header }
  }

  private var header: some View {
    HStack(spacing: 8) {
      // The app icon, round, without the white corners of its export
      // (Assets.xcassets/lotus, derived from brand/assets/lotus.svg).
      Image("lotus")
        .resizable()
        .scaledToFit()
        .frame(width: 32, height: 32)
        .accessibilityHidden(true)
      Text("Shabad OS")
        .font(.title2.weight(.semibold))
        .foregroundStyle(DesignTokens.foreground)
    }
    .padding(.horizontal, Self.inset)
    .frame(maxWidth: .infinity, minHeight: Self.headerHeight, alignment: .leading)
  }
}
