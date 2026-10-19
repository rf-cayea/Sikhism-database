package com.shabados.android

import android.view.HapticFeedbackConstants
import android.view.RoundedCorner
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The viewer's header height: a Material top app bar's, so the sidebar's name sits
 * level with the viewer's Journeys button beside it. */
private val HeaderHeight = 64.dp

/** The space on each side of the sidebar's content: one left edge for the logo, the
 * section label and every row's text (chosen on iOS 2026-10-16). */
private val SidebarInset = 30.dp

/** How often the open app checks the idle boundary, so a journey ends within this
 * long of reaching it. Returning to the app checks at once (MainActivity.onStart). */
private const val IDLE_CHECK_MS = 10_000L

/** Opening and closing: a spring without bounce, close to iOS's `.smooth`. */
private val SlideSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Interface text: the system's Body size (tokens.md#type), with [ANDROID_BODY_SIZE]
 * sp standing in for Body. Latin is set at it directly; Gurmukhi through [bodyTimes].
 */
val BodySize: TextUnit = DesignTokens.ANDROID_BODY_SIZE.sp

/**
 * [ratio] × the system's Body size, for Gurmukhi interface text
 * (packages/design/tokens.md#type). Multiplied in dp, after the person's Font size
 * has turned Body into dp, then handed back as sp, so it is exactly [ratio] × the
 * Body text beside it. Multiplying the sp instead would put a large size through
 * Android's non-linear font scaling a second time, and it would grow less than Body.
 */
@Composable
fun bodyTimes(ratio: Float): TextUnit =
  with(LocalDensity.current) { (BodySize.toDp() * ratio).toSp() }

/**
 * Content passing under a header fades rather than being cut: a hard edge through
 * the sirlekh reads as a rendering fault (docs/interaction.md, "The fading header";
 * reading-shell.md#header). Material's top bars change colour and elevation rather
 * than masking, so it is hand-built: the content is drawn into its own layer and
 * kept only as far as a gradient allows: a flat [FADE_TOP_ALPHA] down to
 * [FADE_START] of the way through the status bar ([statusBar] tall), then rising
 * evenly to full at [to], the header's bottom edge.
 */
fun Modifier.fadingUnder(statusBar: Dp, to: Dp): Modifier =
  graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
      drawContent()
      drawRect(
        Brush.verticalGradient(
          listOf(Color.Black.copy(alpha = FADE_TOP_ALPHA), Color.Black),
          // Above startY the gradient's first colour holds: a flat FADE_TOP_ALPHA.
          startY = (statusBar * FADE_START).toPx(),
          endY = to.toPx(),
        ),
        blendMode = BlendMode.DstIn,
      )
    }

/** How much of the content shows at the top of the screen, behind the status bar:
 * faint enough that the clock and icons read over Gurmukhi, present enough that the
 * text visibly continues under them (chosen 2026-10-16). */
private const val FADE_TOP_ALPHA = 0.08f

/** How far through the status bar the content starts coming back, as a fraction of
 * its height: the rise begins behind its lower part rather than at its edge, so it
 * is already under way where the header starts (chosen 2026-10-16). */
private const val FADE_START = 0.6f

/**
 * The viewer's header: the Journeys button at the left, the title in a centred pill,
 * [actions] at the right. **No background**: the text scrolls up beneath it and
 * fades ([fadingUnder]). One Journeys icon in both states
 * (docs/requirements/reading-shell.md#getting-between-them).
 */
@Composable
fun ViewerHeader(title: String?, onJourneys: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = WindowInsets.statusBars.topDp())
      .height(HeaderHeight)
      // Clear of a camera cutout or navigation bar at the sides in landscape, as
      // the reader's text is (`sideSafeArea`).
      .windowInsetsPadding(sideSafeArea)
      // 12dp, then the icon button's own 4dp around its 40dp circle: the circles sit
      // 16dp inside the safe area, on the reader's text gutter, so they line up
      // with the text below them (chosen 2026-10-16).
      .padding(horizontal = 12.dp),
  ) {
    // R.drawable rather than AppIcons' string: a missing drawable is a compile
    // error, not a blank button. HeaderIconsTest checks the names agree.
    IconButton(
      onClick = onJourneys,
      colors = chromeIconButtonColors(),
      modifier = Modifier.align(Alignment.CenterStart),
    ) {
      Icon(painterResource(R.drawable.ic_journeys), contentDescription = "Journeys")
    }
    if (title != null) {
      // The title is a control (reading-shell.md#the-viewer), so it is drawn as one.
      // The actions that hang off it do not exist yet, so it does nothing.
      Text(
        text = title,
        fontSize = BodySize,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
          .align(Alignment.Center)
          // Clear of the buttons on both sides (12dp padding + a 48dp button), so
          // a long title ellipsises rather than running under them.
          .padding(horizontal = 60.dp)
          .clip(CircleShape)
          .background(chromeColor())
          .padding(horizontal = 16.dp, vertical = 8.dp),
      )
    }
    Row(modifier = Modifier.align(Alignment.CenterEnd), content = actions)
  }
}

/**
 * The display's unsafe areas at the sides: a camera cutout, or a three-button
 * navigation bar, in landscape; nothing in portrait. The viewer and the sidebar are
 * drawn edge to edge, so their controls and text are inset by this; the backgrounds
 * are not.
 */
val sideSafeArea: WindowInsets
  @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

/** The status bar's height, as top padding. */
@Composable
private fun WindowInsets.topDp(): Dp = with(LocalDensity.current) { getTop(this).toDp() }

/** Where the viewer's content starts: below the status bar and the header. Content
 * scrolls up into this space and fades there. */
@Composable
fun viewerContentTop(): Dp = WindowInsets.statusBars.topDp() + HeaderHeight

/**
 * The app: the viewer, lying over the Journeys sidebar. "The viewer is the app"
 * (docs/requirements/reading-shell.md): it is what the app opens to, and the sidebar
 * is never a destination of its own.
 *
 * **Opening Journeys slides the viewer aside** to show the sidebar beneath it,
 * leaving at least [Sidebar.PEEK_DP] of the viewer's inner edge in view, faded
 * towards the background; more in landscape, since the sidebar keeps its portrait
 * width ([Sidebar.travel]). Tapping that edge, Journeys button included, returns to the viewer, as
 * does system Back. The sidebar does not move: it fades in and grows to full size as
 * the viewer moves away ([Sidebar.strength], [Sidebar.scale]).
 *
 * Everything that follows the slide is read inside `graphicsLayer` and draw blocks,
 * so a frame of the slide redraws and never recomposes or re-lays out.
 */
@Composable
fun ReaderShell(shell: ShellState, readerKey: Any, banis: List<Bani>) {
  val view = LocalView.current
  val assets = LocalContext.current.assets
  // Interface Gurmukhi is at weightPrimary (packages/design/tokens.md#type).
  val gurmukhi = remember(assets) { gurmukhiFont(assets, DesignTokens.WEIGHT_PRIMARY.roundToInt()) }
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  /** Settled position: 0 with the viewer over the sidebar, 1 with it aside. */
  val slide = remember { Animatable(if (shell.journeysOpen) 1f else 0f) }
  /** Where a drag has moved the viewer to; null when no drag is moving it. */
  var dragProgress by remember { mutableStateOf<Float?>(null) }
  fun progress() = dragProgress ?: slide.value

  fun setJourneys(open: Boolean) {
    // On opening only (docs/interaction.md, chosen 2026-10-16). CONTEXT_CLICK is
    // the platform's light tick.
    if (shell.setJourneys(open)) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    scope.launch {
      dragProgress?.let { slide.snapTo(it) }
      dragProgress = null
      slide.animateTo(if (open) 1f else 0f, SlideSpec)
    }
  }

  fun open(bani: Bani) {
    shell.open(bani.id)
    scope.launch { slide.animateTo(0f, SlideSpec) }
  }

  BackHandler(enabled = shell.handlesBack) { setJourneys(open = false) }

  LaunchedEffect(Unit) {
    while (true) {
      delay(IDLE_CHECK_MS)
      shell.refresh()
    }
  }

  val gestureEdges = WindowInsets.systemGestures
  val leftEdge = gestureEdges.getLeft(density, LayoutDirection.Ltr).toFloat()
  val rightEdge = gestureEdges.getRight(density, LayoutDirection.Ltr).toFloat()
  // The sidebar's panel is padded clear of this, so its width adds it back (`Sidebar.travel`).
  val leftInset = sideSafeArea.getLeft(density, LayoutDirection.Ltr).toFloat()

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      // Any touch is activity for the idle boundary. The Initial pass and nothing
      // consumed, so it sees everything and changes nothing.
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            shell.touch()
          }
        }
      }
      .pointerInput(leftEdge, rightEdge, leftInset) {
        sidebarDrag(
          shell = shell,
          leftEdge = leftEdge,
          rightEdge = rightEdge,
          leftInset = leftInset,
          onMove = { dragProgress = it },
          onSettle = ::setJourneys,
        )
      },
  ) {
    val leftInsetDp = with(density) { leftInset.toDp() }
    val travelDp = Sidebar.travel(maxWidth.value, maxHeight.value, Sidebar.PEEK_DP, leftInsetDp.value).dp
    val travelPx = with(density) { travelDp.toPx() }

    // Beneath: the sidebar, on `backgroundBase` across the whole screen
    // (Material's `surfaceVariant`, Theme.kt), so it reads as a surface apart from
    // the page-coloured viewer: packages/design/tokens.md, "Where the two
    // backgrounds are used". The same token as on iOS (decided 2026-10-16).
    Box(
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceVariant)
        // Covered by the viewer while closed, so out of reach of TalkBack too.
        .then(if (shell.journeysOpen) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
      JourneysPanel(
        banis = banis,
        readingId = shell.openId,
        gurmukhi = gurmukhi,
        onOpen = ::open,
        modifier = Modifier
          // As wide as the viewer leaves uncovered, so nothing in it sits under
          // the peek.
          .width(travelDp)
          .fillMaxHeight()
          // **Laid out at full size throughout; only its drawing is scaled and
          // faded.** Nothing inside re-lays out as the scale changes, so reaching
          // full size is only the scale reaching 1. On iOS a re-layout here made
          // the rows jump and an extra row appear (apps/ios/README.md). Grows
          // outward from its right edge: up, down and to the left.
          .graphicsLayer {
            val p = progress()
            alpha = Sidebar.strength(p)
            scaleX = Sidebar.scale(p)
            scaleY = scaleX
            transformOrigin = TransformOrigin(1f, 0.5f)
          },
      )
    }

    val background = MaterialTheme.colorScheme.background
    val hairline = MaterialTheme.colorScheme.onBackground
    val corners = remember(view) { DisplayCorners(view) }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          val p = progress()
          translationX = travelPx * p
          // The display's own corners, so the moving viewer reads as the screen
          // itself slid aside. At rest the display's corners already hide it.
          shape = corners
          clip = p > 0f
          shadowElevation = 24.dp.toPx() * p
        }
        .background(background)
        // While aside, only its edge is a target (the Box below), for TalkBack too.
        .then(if (shell.journeysOpen) Modifier.clearAndSetSemantics {} else Modifier)
        .drawWithContent {
          drawContent()
          val p = progress()
          if (p > 0f) {
            // Faded towards the background, so the edge reads as set aside.
            drawRect(background.copy(alpha = 0.6f * p))
            // A hairline along the edge, so it reads as a card's edge and not
            // only a shadow's.
            drawOutline(
              corners.createOutline(size, layoutDirection, this),
              hairline.copy(alpha = 0.08f * p),
              style = Stroke(width = 0.5.dp.toPx()),
            )
          }
        },
    ) {
      val bani = banis.withId(shell.openId)
      if (bani == null) {
        DefaultScreen(
          pills = DefaultScreenPills.of(banis),
          continueWith = banis.withId(shell.previousId),
          gurmukhi = gurmukhi,
          onOpen = ::open,
          onJourneys = { setJourneys(!shell.journeysOpen) },
        )
      } else {
        // A different bani is a new reader, and so is the same bani in a new
        // process: [readerKey] is new each process, so saved scroll state from
        // before a process death is never restored into a later reader.
        key(readerKey, bani.id) {
          BaniReader(bani, onJourneys = { setJourneys(!shell.journeysOpen) })
        }
      }
    }

    // While open, the visible edge, Journeys button included, is one target that
    // returns to the viewer. No ripple: the slide is the feedback.
    if (shell.journeysOpen && dragProgress == null) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          // All of the viewer still in view: the peek in portrait, more in landscape.
          .width(maxWidth - travelDp)
          .fillMaxHeight()
          .semantics { contentDescription = "Journeys" }
          .clickable(interactionSource = null, indication = null) { setJourneys(open = false) },
      )
    }
  }
}

/**
 * Moves the viewer with a one-finger drag. **Only a mostly horizontal drag towards a
 * change counts** ([Sidebar.judge]), **it must start inside the content**
 * ([Sidebar.startsInContent]; the edges are system Back's), and **a pinch is never a
 * drag**: a second finger ends it, and a drag already moving the viewer settles back.
 *
 * Read in the Initial pass so it sees the drag before the list or a row does; once
 * judged horizontal, every event is consumed, so the list does not scroll and the
 * row under the finger does not count a tap. Until then nothing is consumed.
 */
private suspend fun PointerInputScope.sidebarDrag(
  shell: ShellState,
  leftEdge: Float,
  rightEdge: Float,
  leftInset: Float,
  onMove: (progress: Float) -> Unit,
  onSettle: (open: Boolean) -> Unit,
) {
  val decision = viewConfiguration.touchSlop
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (!Sidebar.startsInContent(down.position.x, size.width.toFloat(), leftEdge, rightEdge)) return@awaitEachGesture
    // Per gesture, so a window resized without recreating (split screen) is used
    // at its new width.
    val travel = Sidebar.travel(size.width.toFloat(), size.height.toFloat(), Sidebar.PEEK_DP.dp.toPx(), leftInset)
    val wasOpen = shell.journeysOpen
    val velocity = VelocityTracker().apply { addPointerInputChange(down) }
    var axis: Sidebar.Axis? = null
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      if (event.changes.count { it.pressed } > 1) {
        if (axis == Sidebar.Axis.HORIZONTAL) onSettle(wasOpen)
        return@awaitEachGesture
      }
      val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
      val dx = change.position.x - down.position.x
      val dy = change.position.y - down.position.y
      if (axis == null) axis = Sidebar.judge(dx, dy, decision, wasOpen)
      when (axis) {
        Sidebar.Axis.OTHER -> return@awaitEachGesture
        Sidebar.Axis.HORIZONTAL -> {
          event.changes.forEach { it.consume() }
          velocity.addPointerInputChange(change)
          if (!change.pressed) {
            onSettle(Sidebar.landsOpen(wasOpen, dx, velocity.calculateVelocity().x, travel))
            return@awaitEachGesture
          }
          onMove(Sidebar.progress(wasOpen, dx, travel))
        }
        null -> {
          velocity.addPointerInputChange(change)
          if (!change.pressed) return@awaitEachGesture
        }
      }
    }
  }
}

/**
 * The display's own corner radii, read from the window
 * (`WindowInsets.getRoundedCorner`, API 31; minSdk is 36). A display without
 * rounded corners reports none, and the viewer is square there too. Read when the
 * outline is made, once the view is attached, rather than in composition, where the
 * insets may not have arrived yet.
 */
private class DisplayCorners(private val view: View) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val insets = view.rootWindowInsets
    fun radius(position: Int) = CornerRadius(insets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f)
    return Outline.Rounded(
      RoundRect(
        left = 0f,
        top = 0f,
        right = size.width,
        bottom = size.height,
        topLeftCornerRadius = radius(RoundedCorner.POSITION_TOP_LEFT),
        topRightCornerRadius = radius(RoundedCorner.POSITION_TOP_RIGHT),
        bottomRightCornerRadius = radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
        bottomLeftCornerRadius = radius(RoundedCorner.POSITION_BOTTOM_LEFT),
      )
    )
  }
}

/**
 * Behind every control on the viewer: the default screen's pills and `continue
 * reading`, the title pill, and a circle behind each header button.
 * `backgroundBase` (Material's `surfaceVariant`, Theme.kt), as the sidebar is. **Not
 * Material You's dynamic colour** (decided 2026-10-16): a wallpaper tint set the
 * controls apart from the text they sit among.
 */
@Composable
fun chromeColor(): Color = MaterialTheme.colorScheme.surfaceVariant

/** The header's icon buttons, with a persistent circle of [chromeColor] behind
 * them. The icon keeps the text colour the header sets. */
@Composable
fun chromeIconButtonColors(): IconButtonColors =
  IconButtonDefaults.iconButtonColors(containerColor = chromeColor(), contentColor = LocalContentColor.current)

/**
 * A stand-in for the Journeys sidebar (docs/requirements/reading-shell.md#the-journeys-sidebar),
 * which needs journeys, pins and the Library, none of which exist yet. It has the
 * specified header; the bani list stands in for the rest and goes when they land.
 * `New journey` is left out until that band is designed. **No toggle of its own**:
 * the viewer's Journeys button stays in view on the viewer's edge.
 */
@Composable
private fun JourneysPanel(
  banis: List<Bani>,
  readingId: String?,
  gurmukhi: FontFamily,
  onOpen: (Bani) -> Unit,
  modifier: Modifier,
) {
  val top = WindowInsets.statusBars.topDp()
  val bottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
  val colors = MaterialTheme.colorScheme
  // The toner wash, blended from the sidebar's own colour rather than the page's.
  val toner = lerp(colors.surfaceVariant, colors.onSurface, DesignTokens.TONER_OPACITY)
  // Clear of a camera cutout, or a navigation bar, on its left in landscape: the
  // sidebar is drawn edge to edge, so its inset alone would put the logo and rows
  // behind it (seen on a Pixel 7a, 2026-10-16). Left only: its right side lies
  // under the viewer's edge.
  Box(modifier.windowInsetsPadding(sideSafeArea.only(WindowInsetsSides.Left))) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        // The list fades under the header rather than being cut
        // (reading-shell.md#header).
        .fadingUnder(statusBar = top, to = top + HeaderHeight)
        .verticalScroll(rememberScrollState())
        .padding(top = top + HeaderHeight + 12.dp, bottom = bottom + 12.dp),
    ) {
      Text(
        text = stringResource(R.string.nitnem),
        fontSize = BodySize,
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(start = SidebarInset, end = SidebarInset, bottom = 8.dp),
      )
      banis.forEachIndexed { index, bani ->
        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = SidebarInset), color = colors.outlineVariant)
        // Gurmukhi only (chosen 2026-10-16). No card behind a row: the bani being
        // read takes the toner wash in the reader's card shape, bled 8dp outward
        // from the text as a tapped line's is, and a press takes the ripple there.
        Text(
          text = bani.gurmukhi,
          fontFamily = gurmukhi,
          fontSize = bodyTimes(DesignTokens.UI_GURMUKHI_RATIO),
          color = colors.onSurface,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SidebarInset - 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (bani.id == readingId) toner else Color.Transparent)
            .clickable { onOpen(bani) }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        )
      }
    }
    // The header does not scroll with the list (reading-shell.md#header).
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .padding(top = top, start = SidebarInset, end = SidebarInset)
        .height(HeaderHeight),
    ) {
      // The app icon, round (res/drawable-nodpi/ic_launcher_logo.png, the lotus
      // over its gradient, from brand/assets/lotus.svg via the legacy export).
      Image(
        painter = painterResource(R.drawable.ic_launcher_logo),
        contentDescription = null,
        modifier = Modifier.size(32.dp).clip(CircleShape),
      )
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurface,
      )
    }
  }
}

/**
 * What the viewer shows when there is nothing to resume: **ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ**, pills
 * for a few banis, and `continue reading` naming the last journey's bani when there
 * is one (docs/requirements/reading-shell.md#the-default-screen). No title: the
 * title names what is being read, and nothing is.
 *
 * The greeting and pills are sized from the system's Body size
 * (`GREETING_RATIO`, `PILL_RATIO`: packages/design/tokens.md#type), not from the
 * reader's size, so pinching the reader does not change them.
 */
@Composable
private fun DefaultScreen(
  pills: List<Bani>,
  continueWith: Bani?,
  gurmukhi: FontFamily,
  onOpen: (Bani) -> Unit,
  onJourneys: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  BoxWithConstraints(Modifier.fillMaxSize()) {
    // Centred in the screen below the header, and scrollable should the largest
    // Font size in landscape not fit.
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
      modifier = Modifier
        .verticalScroll(rememberScrollState())
        .fillMaxWidth()
        .heightIn(min = maxHeight)
        .padding(top = viewerContentTop(), bottom = 16.dp, start = 16.dp, end = 16.dp),
    ) {
      Text(
        text = "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ",
        fontFamily = gurmukhi,
        fontSize = bodyTimes(DesignTokens.GREETING_RATIO),
        lineHeight = bodyTimes(DesignTokens.GREETING_RATIO * DesignTokens.LINE_HEIGHT_RATIO),
        color = colors.onBackground,
        textAlign = TextAlign.Center,
      )
      RowOrColumn(spacing = 8.dp) {
        pills.forEach { bani ->
          // Gurmukhi in the greeting's weight and colour (chosen 2026-10-16).
          Text(
            text = bani.gurmukhi,
            fontFamily = gurmukhi,
            fontSize = bodyTimes(DesignTokens.PILL_RATIO),
            color = colors.onBackground,
            maxLines = 1,
            modifier = Modifier
              .clip(CircleShape)
              .background(chromeColor())
              .clickable { onOpen(bani) }
              .padding(horizontal = 24.dp, vertical = 8.dp),
          )
        }
      }
      // Absent, not disabled, when there is nothing to continue: "an empty or dead
      // control on a first launch is worse than nothing".
      if (continueWith != null) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(chromeColor())
            .clickable { onOpen(continueWith) }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
          Text("Continue reading", fontSize = BodySize, fontWeight = FontWeight.SemiBold, color = colors.primary)
          Text(continueWith.latin, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
      }
    }
    ViewerHeader(title = null, onJourneys = onJourneys)
  }
}

/**
 * Its children in a row when they fit the width, otherwise one above another,
 * centred: never a row that wraps partway (iOS's `ViewThatFits` over an `HStack`
 * and a `VStack`). Narrow screens and large Font sizes stack the pills.
 */
@Composable
private fun RowOrColumn(spacing: Dp, content: @Composable () -> Unit) {
  Layout(content) { measurables, constraints ->
    val gap = spacing.roundToPx()
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val placeables = measurables.map { it.measure(loose) }
    val rowWidth = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
    if (rowWidth <= constraints.maxWidth) {
      val height = placeables.maxOfOrNull { it.height } ?: 0
      layout(rowWidth, height) {
        var x = 0
        placeables.forEach {
          it.placeRelative(x, (height - it.height) / 2)
          x += it.width + gap
        }
      }
    } else {
      val width = placeables.maxOfOrNull { it.width } ?: 0
      val height = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
      layout(width, height) {
        var y = 0
        placeables.forEach {
          it.placeRelative((width - it.width) / 2, y)
          y += it.height + gap
        }
      }
    }
  }
}
