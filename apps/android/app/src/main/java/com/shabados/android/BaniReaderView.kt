package com.shabados.android

import android.content.res.AssetManager
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
// matchParentSize needs no import -- it's a member of BoxScope, not a top-level
// function, and resolves automatically inside a Box{} content lambda.
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Sant Lipi at a specific `wght` axis value. A single `Font()` entry pins one
 * static instance of a variable font — arbitrary weights (title 650, ik oankar
 * 400, body 550, ...) each need their own [FontVariation.Settings], the Android
 * equivalent of `Fonts.variable(_:size:weight:)` on iOS. Callers `remember` the
 * result keyed on the weight so this isn't rebuilt every recomposition.
 */
private fun gurmukhiFont(assets: AssetManager, weight: Int): FontFamily =
  FontFamily(
    Font(
      path = "SantLipi-VF.ttf",
      assetManager = assets,
      variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )
  )

/** `background` blended toward `onBackground` by [DesignTokens.TONER_OPACITY] — a
 * solid colour (not a translucent wash) for the same reason `DesignTokens.toner`
 * is solid on iOS: the Keep Reading preview's fade overlay needs one concrete
 * colour to paint toward, and a translucent fill can't serve as that target
 * without a second blend of its own. */
@Composable
private fun tonerColor(): Color =
  lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground, DesignTokens.TONER_OPACITY)

/** The same wash, lighter: the top of the Keep Reading card's gradient. */
@Composable
private fun tonerFaintColor(): Color =
  lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground, DesignTokens.TONER_FAINT_OPACITY)

/** The vertical gap between reader rows, as padding inside each row's own bounds.
 * Named because the pinch anchor discounts it: a pinch in that padding is a pinch
 * between rows. */
private val RowSpacing = 6.dp

/** How long a pinch's anchor highlight stays fully on after the fingers lift, then
 * how long it takes to fade. iOS's `tapHighlightHoldDuration` and
 * `tapHighlightFadeOutDuration`, so the two platforms fade alike. */
private const val HIGHLIGHT_HOLD_MS = 500L
private const val HIGHLIGHT_FADE_MS = 250

/**
 * How reading text is drawn. Two things, both because the reader's size is its own:
 *
 * - **The system font scale is removed.** The reader's sizes are dp: the font scale
 *   chose the starting size once (see [ReaderSize]) and must not multiply every size
 *   again on top of it. With a font scale of 1, `.sp` is exactly dp.
 * - **[TextMotion.Animated]**: glyphs laid out and drawn at their exact scaled size
 *   and position. The default, `Static`, rounds glyph positions and advances to whole
 *   pixels, so a pinch moves text in visible jumps rather than continuously. Always on,
 *   not just during a pinch: switching on release could move line breaks, and with
 *   them the row the pinch just put back.
 *
 * Only reading text goes inside this; the toolbar and the Expand button keep following
 * the system setting like the rest of the platform's UI.
 */
@Composable
private fun ReaderText(content: @Composable () -> Unit) {
  val density = LocalDensity.current
  CompositionLocalProvider(
    LocalDensity provides Density(density.density, fontScale = 1f),
    LocalTextStyle provides LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
    content = content,
  )
}

private fun vishraamColor(weight: Vishraam, dark: Boolean): Color =
  when (weight) {
    Vishraam.HEAVY -> if (dark) DesignTokens.vishraamHeavyDark else DesignTokens.vishraamHeavyLight
    Vishraam.MEDIUM -> if (dark) DesignTokens.vishraamMediumDark else DesignTokens.vishraamMediumLight
    Vishraam.LIGHT -> if (dark) DesignTokens.vishraamLightDark else DesignTokens.vishraamLightLight
  }

/** The reader's one card shape: the pinch anchor's toner, the tap ripple and the
 * Keep Reading card are all clipped to it, on the same bounds. */
private object ReaderCard {
  /** How far a card extends past the text on each side: half the reader's 16dp
   * gutter, as on iOS. Vertically a row extends by [RowSpacing] instead, the gap
   * it already owns, because two rows' tap targets cannot overlap. */
  val bleed = 8.dp
  val shape = RoundedCornerShape(12.dp)
}

/**
 * One reader row. **Everything drawn behind or over the text — the Keep Reading
 * card's fill ([background]), the pinch anchor's toner ([highlight]), the tap
 * ripple — is on this one node, clipped to [ReaderCard.shape]**, so none of them can
 * differ in size or corner radius. They did when the toner was drawn bled outside
 * the row (iOS's negative-padding approach) while the ripple stayed on the row's
 * own, unrounded bounds. The text is inset instead: the list's side padding is
 * [ReaderCard.bleed] short of the gutter and [content] adds it back.
 */
@Composable
private fun ReaderRow(
  highlight: Float,
  onTap: () -> Unit,
  background: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ReaderCard.shape)
      .then(background)
      .background(tonerColor().copy(alpha = highlight))
      .clickable(onClick = onTap)
      .padding(vertical = RowSpacing),
  ) { content() }
}

private object Title {
  const val HEADING_SIZE_RATIO = 1.25f
  const val MOOLMANTAR_SIZE_RATIO = 1.5f
  const val WEIGHT = 650
  const val SPACE_BEFORE = 1.6f
  const val SPACE_AFTER = 0.8f
  const val IK_OANKAR_SIZE_RATIO = 2.5f
  const val IK_OANKAR_WEIGHT = 400

  fun sizeRatio(isHeading: Boolean, isMoolmantar: Boolean): Float =
    when {
      isMoolmantar -> MOOLMANTAR_SIZE_RATIO
      isHeading -> HEADING_SIZE_RATIO
      else -> 1f
    }
}

private const val COLOPHON_OPACITY = 0.55f

/** Builds pause-coloured segments from [Line.pauses] over [Line.gurmukhi] — every
 * pause word is styled either way, its colour is just the [pauses] toggle. */
private fun coloredText(line: Line, pauses: Boolean, dark: Boolean, foreground: Color): AnnotatedString =
  buildAnnotatedString {
    val chars = line.gurmukhi
    var cursor = 0
    for (run in line.pauses) {
      val lower = run.range.first.coerceIn(cursor, chars.length)
      val upper = (run.range.last + 1).coerceIn(lower, chars.length)
      if (lower >= upper) continue
      if (lower > cursor) append(chars.substring(cursor, lower))
      val color = if (pauses) vishraamColor(run.weight, dark) else foreground
      withStyle(SpanStyle(color = color)) { append(chars.substring(lower, upper)) }
      cursor = upper
    }
    if (cursor < chars.length) append(chars.substring(cursor))
  }

/**
 * Lays the mool mantar out as `ੴ` alone on its own line, then its six word-pairs
 * flowing after it — each pair glued together so it can never split across a
 * wrap, but free to share a line with a neighbouring pair. Ported from
 * `LineText.layingOutMoolmantar` on iOS — same word-index map, same Word Joiner
 * (`⁠`) glue, same reasoning for using it over a non-breaking space (a
 * default-ignorable character renders zero-width and blocks a line break on
 * either side regardless of any specific font's own glyph metrics, where Sant
 * Lipi's own NBSP glyph is known broken — see that function's own comment for
 * the full font-table investigation behind this).
 *
 * Only called on `line.isMoolmantar` text, one exact verified spelling, so the
 * word-index map below is safe to hardcode:
 * word 0 = ੴ, 1–12 = the six pairs (sat nam / karta purakh / nirbhau nirvair /
 * akal murat / ajuni saibhan / gur prasad), 13 = the closing `॥`.
 */
private fun layOutMoolmantar(text: AnnotatedString, ikOankarStyle: SpanStyle): AnnotatedString {
  val spaceIndices = text.text.indices.filter { text.text[it] == ' ' }
  if (spaceIndices.isEmpty()) return text

  val forcedNewline = setOf(0)
  // Within-pair spaces, plus the space before the closing danda — treated as a
  // seventh pair so it can never end up orphaned alone on a line.
  val nonBreaking = setOf(1, 3, 5, 7, 9, 11, 12)

  return buildAnnotatedString {
    var segmentStart = 0
    spaceIndices.forEachIndexed { index, spaceIndex ->
      if (index !in forcedNewline && index !in nonBreaking) return@forEachIndexed
      val piece = text.subSequence(segmentStart, spaceIndex)
      if (index == 0) {
        withStyle(ikOankarStyle) { append(piece) }
      } else {
        append(piece)
      }
      if (index in forcedNewline) {
        append("\n")
      } else {
        // The ORIGINAL space, unmodified, is what actually renders — the Word
        // Joiners either side are what stop it being a valid wrap point.
        append("⁠")
        append(text.subSequence(spaceIndex, spaceIndex + 1))
        append("⁠")
      }
      segmentStart = spaceIndex + 1
    }
    append(text.subSequence(segmentStart, text.text.length))
  }
}

/** Was the row directly before `index` also a title-styled line? A line preceded
 * by a continuation card counts as *not* preceded by a title — a card is a real
 * visual break, not a sibling heading. */
private fun precedingItemIsTitle(items: List<ReaderItem>, index: Int): Boolean {
  if (index <= 0) return false
  val previous = items[index - 1] as? ReaderItem.LineItem ?: return false
  return previous.line.isHeading || previous.line.isMoolmantar
}

/** Inlines every opened continuation's lines, in place, as ordinary line items —
 * so the tap gesture and highlight background every row already gets apply to a
 * revealed line exactly the same way they apply to any other line, with no
 * separate code path. One-directional: nothing here ever re-collapses a
 * continuation, matching library.md's "no setting that removes anything". */
private fun expand(items: List<ReaderItem>, expandedIds: Set<String>): List<ReaderItem> =
  items.flatMap { item ->
    if (item is ReaderItem.ContinuationItem && item.continuation.id in expandedIds) {
      item.continuation.lines.map { line -> ReaderItem.LineItem(line.id, line) }
    } else {
      listOf(item)
    }
  }

/** Every Sant Lipi weight the reader needs, built once per reader session (see
 * `gurmukhiFont`'s own comment) and threaded down, rather than rebuilt by every
 * row each time it scrolls back into view — `LazyColumn` disposes off-screen row
 * composables, which would otherwise re-run a per-row `remember` on every
 * re-entry. */
private class GurmukhiFonts(assets: AssetManager) {
  val title = gurmukhiFont(assets, Title.WEIGHT)
  val body = gurmukhiFont(assets, DesignTokens.WEIGHT_PRIMARY.roundToInt())
  val secondary = gurmukhiFont(assets, DesignTokens.WEIGHT_SECONDARY.roundToInt())
  val ikOankar = gurmukhiFont(assets, Title.IK_OANKAR_WEIGHT)
}

@Composable
private fun LineRow(
  line: Line,
  size: Float,
  pauses: Boolean,
  schemes: List<Pronunciation>,
  ratio: Float,
  fonts: GurmukhiFonts,
  precededByTitle: Boolean,
) {
  val dark = isSystemInDarkTheme()
  val isTitle = line.isHeading || line.isMoolmantar
  val titleSize = size * Title.sizeRatio(line.isHeading, line.isMoolmantar)

  val foreground = MaterialTheme.colorScheme.onBackground
  val base = remember(line.id, pauses, dark, foreground) { coloredText(line, pauses, dark, foreground) }
  val text = if (line.isMoolmantar) {
    remember(base, size) {
      layOutMoolmantar(
        base,
        SpanStyle(fontSize = (size * Title.IK_OANKAR_SIZE_RATIO).sp, fontFamily = fonts.ikOankar),
      )
    }
  } else {
    base
  }

  ReaderText {
    Column(
      horizontalAlignment = if (isTitle) Alignment.CenterHorizontally else Alignment.Start,
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          top = if (isTitle && !precededByTitle) (size * Title.SPACE_BEFORE).dp else 0.dp,
          bottom = if (isTitle) (size * Title.SPACE_AFTER).dp else 0.dp,
        )
        .alpha(if (line.isColophon) COLOPHON_OPACITY else 1f),
    ) {
      Text(
        text = text,
        fontFamily = if (isTitle) fonts.title else fonts.body,
        color = foreground,
        fontSize = (if (isTitle) titleSize else size).sp,
        lineHeight = ((if (isTitle) titleSize else size) * DesignTokens.LINE_HEIGHT_RATIO).sp,
        textAlign = if (isTitle) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
      )
      schemes.forEach { scheme ->
        val secondary = size * ratio
        Text(
          text = Transliteration.of(line, scheme),
          fontFamily = if (scheme == Pronunciation.DEVANAGARI) fonts.secondary else FontFamily.Default,
          fontWeight = if (scheme == Pronunciation.LATIN) FontWeight(DesignTokens.WEIGHT_LATIN.roundToInt()) else null,
          fontSize = secondary.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = if (isTitle) TextAlign.Center else TextAlign.Start,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

/**
 * The "Keep reading" control. Tapping the card body scrolls it to the top, the
 * same as any other row; tapping `Expand` only expands. **No button-frame
 * exclusion hack needed here** — unlike SwiftUI's `.simultaneousGesture`, which
 * fires regardless of what else claims a touch, Compose's `clickable`/`Button`
 * consume their own touch sequence, so a plain nested `Button` inside a
 * `clickable` card correctly gets first claim on its own area for free.
 *
 * A tap gets the platform ripple, like every line row, not a toner fill:
 * docs/interaction.md gives a continuation card the platform's ordinary pressed
 * state and nothing more.
 */
@Composable
private fun ContinuationCard(
  continuation: Continuation,
  size: Float,
  fonts: GurmukhiFonts,
  highlight: Float,
  onTapCard: () -> Unit,
  onExpand: () -> Unit,
) {
  val toner = tonerColor()
  val tonerFaint = tonerFaintColor()
  val previewVisualLines = 3
  val previewLineSpacing = 4.dp
  val previewPadding = 16.dp

  ReaderRow(
    highlight = highlight,
    onTap = onTapCard,
    // At rest, a gradient from `tonerFaint` at the top to `toner`
    // (packages/design/tokens.md#opacity), **reaching `toner` exactly where the
    // preview's veil ends** and solid below it. The veil ends in `toner` too, so
    // the two meet without an edge. A gradient running on to the card's bottom
    // edge, as on iOS, left the veil's bottom a shade darker than the card behind
    // it: a faint rectangle inside the card.
    background = Modifier.drawBehind {
      val solid = (RowSpacing + previewPadding).toPx()
      drawRect(Brush.verticalGradient(listOf(tonerFaint, toner), startY = 0f, endY = this.size.height - solid))
    },
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = previewPadding),
      contentAlignment = Alignment.Center,
    ) {
      // No fixed `.height()` here any more — a first attempt tried to
      // independently estimate one line's rendered height
      // (`(size * LINE_HEIGHT_RATIO).sp.toDp()`) to budget for exactly three,
      // and that estimate didn't actually match what `Text(lineHeight:)` renders
      // in practice: the box came out shorter than three real lines *and* a
      // visibly different size from the card's own bled bounds around it — which
      // is what read as a second, inset background rather than one card. Taking
      // exactly three real lines and letting the Column size itself to however
      // tall they actually are removes the estimate entirely, so there's nothing
      // left that can disagree with reality.
      ReaderText {
        Column(
          verticalArrangement = Arrangement.spacedBy(previewLineSpacing),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReaderCard.bleed),
        ) {
          continuation.lines.take(previewVisualLines).forEach { line ->
            Text(
              text = line.gurmukhi,
              fontFamily = fonts.body,
              fontSize = size.sp,
              lineHeight = (size * DesignTokens.LINE_HEIGHT_RATIO).sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      // A solid-colour veil painted over the text, top to bottom, ending exactly
      // at `toner` — not a mask of the text's own alpha, which multiplies unevenly
      // against Gurmukhi's stacked matras. See `preview`'s own comment on iOS.
      // **The card's full width**, not the text's, so it has no side edges to
      // show against the gradient behind it.
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(Brush.verticalGradient(0f to Color.Transparent, 1f to toner)),
      )
      Button(onClick = onExpand) { Text("Expand") }
    }
  }
}

/** Saves a set of ids as a list. Explicit, rather than relying on the set being
 * `Serializable`, which depends on which Set implementation is behind it. */
private val StringSetSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaniReader(bani: Bani, onBack: () -> Unit) {
  val context = LocalContext.current
  val fonts = remember(context.assets) { GurmukhiFonts(context.assets) }
  val isTablet = LocalConfiguration.current.isTablet

  // Saved across launches (docs/requirements/display-controls.md#persistence). The
  // starting size was written before any UI, in MainActivity, so the default here
  // is only a fallback for a store that cannot be read.
  val sizeStore = remember(context) { context.readerSizeStore() }
  var savedSize by remember { mutableFloatStateOf(sizeStore.get() ?: DesignTokens.DEFAULT_SIZE) }
  fun saveSize(value: Float) {
    savedSize = value
    sizeStore.set(value)
  }
  /** The size during a pinch, saved only when the fingers lift. */
  var livePinchSize by remember { mutableStateOf<Float?>(null) }
  val readerSize = livePinchSize ?: ReaderSize.displaySize(savedSize, isTablet)

  // Saveable, not remember: rotation, a theme or font size change, and process death
  // recreate the activity, and the reader must come back as the person left it. Not
  // persisted across launches as iOS does (apps/README.md#state-of-play). Saved state
  // ends when the reader closes, so each bani still opens with these defaults.
  var pauses by rememberSaveable { mutableStateOf(true) }
  var pronDevanagari by rememberSaveable { mutableStateOf(false) }
  var pronLatin by rememberSaveable { mutableStateOf(false) }
  var ratio by rememberSaveable { mutableFloatStateOf(DesignTokens.RATIO_DEFAULT) }
  val schemes = remember(pronDevanagari, pronLatin) {
    buildList {
      if (pronDevanagari) add(Pronunciation.DEVANAGARI)
      if (pronLatin) add(Pronunciation.LATIN)
    }
  }

  // Saveable, and restored in the same first composition as `listState` below, so the
  // saved scroll index points into the list with these continuations already
  // expanded. Restoring it any later would land the scroll on the wrong row.
  var expandedContinuations by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(setOf<String>()) }
  val rawItems = remember(bani.id) { bani.items() }
  val items = remember(rawItems, expandedContinuations) { expand(rawItems, expandedContinuations) }

  // Saves the first visible row's index and offset, and its key, which the list uses
  // to find that row again. Keys are ReaderItem.id, stable for a given bani.
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  /** Scrolls the tapped row to the top. **No custom highlight here** — that was
   * iOS's own answer to "confirm which line was tapped," built because
   * SwiftUI's plain tap gestures have no feedback of their own. Android
   * already has one: the default ripple every `clickable` gets, which is why
   * neither this row's own `clickable` nor `ContinuationCard`'s suppresses it.
   * Same requirement (docs/requirements/display-controls.md#tap-a-line), a
   * platform-appropriate mechanism rather than a ported one. */
  fun tapLine(index: Int) {
    scope.launch { listState.animateScrollToItem(index) }
  }

  /** The row a pinch is anchored to, by item key, drawn with a toner wash. A pinch
   * has no ripple to show where it is anchored, so unlike a tap it gets the wash. */
  var highlightedKey by remember { mutableStateOf<Any?>(null) }
  /** Restores the anchor and then fades it; cancelled by the next pinch. */
  var anchorJob by remember { mutableStateOf<Job?>(null) }

  /** Each visible row's extent, without the [RowSpacing] padding inside it, so a
   * pinch in that padding counts as between rows. Pixels from the reader's top, the
   * same space the pointer reports in. */
  fun visibleRows(spacingPx: Float): List<Pinch.Row> {
    val info = listState.layoutInfo
    return info.visibleItemsInfo.map {
      val top = (it.offset - info.viewportStartOffset).toFloat()
      Pinch.Row(it.key, it.index, top + spacingPx, top + it.size - spacingPx)
    }
  }

  /**
   * Once the pinch ends: the point of the anchor row that was under the fingers goes
   * back to where the fingers went down, then the highlight fades.
   *
   * **Once, on release — never during the pinch.** On iOS the position was corrected
   * on every step, and at large sizes the reader jittered badly: each step re-wrapped
   * lines and the lazy list re-estimated rows off screen, so each correction was
   * computed from guesses (apps/ios/README.md#state-of-play). Android corrected every
   * step too, with the same exposure. During the pinch the list holds its top row,
   * as it does for any size change.
   *
   * Exact, not within a line as on iOS: Compose reports each row's measured offset
   * once laid out.
   */
  fun restoreAnchor(row: Pinch.Row, y: Float, fraction: Float, spacingPx: Float) {
    anchorJob = scope.launch {
      // Two frames: the saved size recomposes the rows in one and is laid out by the
      // next, so the row is measured at its final size before it is placed.
      withFrameNanos {}
      withFrameNanos {}
      listState.scrollToItem(row.index)
      val info = listState.layoutInfo
      info.visibleItemsInfo.firstOrNull { it.index == row.index }?.let {
        val top = (it.offset - info.viewportStartOffset) + spacingPx
        listState.scrollBy(Pinch.correction(top, it.size - 2 * spacingPx, fraction, y))
      }
      delay(HIGHLIGHT_HOLD_MS)
      if (highlightedKey == row.key) highlightedKey = null
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        // One line, ellipsised. At the largest Font size and Display size the
        // actions leave the title a few characters' width, and wrapping broke it
        // into a column of syllables that pushed the reader down the screen.
        title = { Text(bani.latin, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // Icon buttons, not text, matching iOS: the text buttons scaled with Font
        // size and at the largest Font and Display size left the title only "…".
        // Icons are sized in dp and do not scale. R.drawable rather than
        // AppIcons.back's string: a missing drawable is then a compile error, not
        // a blank button. ToolbarIconsTest checks the names agree.
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
        },
        actions = {
          // Letters, not icons: "a" and "A", chosen by the author 2026-10-15. These
          // two buttons go when the Controls sidebar lands, so they have no rows in
          // packages/design/icons.md. Sized in dp, like the icons beside them,
          // so the system Font size cannot widen them.
          val letterSize = with(LocalDensity.current) { 20.dp.toSp() }
          IconButton(onClick = { saveSize(DesignTokens.clamp(readerSize - 2f, isTablet)) }) {
            Text("a", fontSize = letterSize, modifier = Modifier.clearAndSetSemantics { contentDescription = "Smaller" })
          }
          IconButton(onClick = { saveSize(DesignTokens.clamp(readerSize + 2f, isTablet)) }) {
            Text("A", fontSize = letterSize, modifier = Modifier.clearAndSetSemantics { contentDescription = "Larger" })
          }
          var menuOpen by remember { mutableStateOf(false) }
          IconButton(onClick = { menuOpen = true }) {
            Icon(painterResource(R.drawable.ic_controls), contentDescription = "Controls")
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Pauses") },
              trailingIcon = { Checkbox(checked = pauses, onCheckedChange = { pauses = it }) },
              onClick = { pauses = !pauses },
            )
            DropdownMenuItem(
              text = { Text(Pronunciation.DEVANAGARI.label) },
              trailingIcon = { Checkbox(checked = pronDevanagari, onCheckedChange = { pronDevanagari = it }) },
              onClick = { pronDevanagari = !pronDevanagari },
            )
            DropdownMenuItem(
              text = { Text(Pronunciation.LATIN.label) },
              trailingIcon = { Checkbox(checked = pronLatin, onCheckedChange = { pronLatin = it }) },
              onClick = { pronLatin = !pronLatin },
            )
          }
        },
      )
    }
  ) { inset ->
    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(isTablet) {
          val spacingPx = RowSpacing.toPx()
          // **The Initial pass, and every event consumed once a second finger is
          // down**, so the pinch owns the gesture. In the Main pass the list and the
          // rows see each event first: the list can scroll with the fingers
          // mid-pinch, and lifting them counted as a tap on the row (seen on device
          // 2026-10-14) — a ripple beside the anchor's toner, and a scroll to the
          // top competing with the anchor's restore. The first finger's down is not
          // consumed, so a one-finger scroll or tap is untouched.
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Set when a second finger lands; null means this gesture is not a pinch.
            var baseline: Float? = null
            // Cumulative since the pinch began, so the size cannot compound.
            var magnification = 1f
            var anchor: Pinch.Row? = null
            var focalY = 0f
            var fraction = 0f
            do {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              if (baseline == null && event.changes.count { it.pressed } >= 2) {
                // From the size on screen, within range, not the saved value.
                baseline = ReaderSize.displaySize(savedSize, isTablet)
                focalY = event.calculateCentroid(useCurrent = true).y
                anchor = Pinch.anchor(focalY, visibleRows(spacingPx), size.height.toFloat())
                fraction = anchor?.let { Pinch.fraction(focalY, it) } ?: 0f
                anchorJob?.cancel()
                highlightedKey = anchor?.key
              }
              if (baseline != null) {
                // Until every finger lifts, including the ups, so none ends as a tap.
                event.changes.forEach { it.consume() }
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                  magnification *= zoom
                  val next = Pinch.size(baseline, magnification, isTablet)
                  if (next != livePinchSize) livePinchSize = next
                }
              }
            } while (event.changes.any { it.pressed })
            if (baseline != null) {
              livePinchSize?.let(::saveSize)
              livePinchSize = null
              anchor?.let { restoreAnchor(it, focalY, fraction, spacingPx) }
            }
          }
        },
      contentPadding = PaddingValues(
        top = inset.calculateTopPadding(),
        bottom = inset.calculateBottomPadding(),
        // The 16dp gutter, less the card's bleed, which each row adds back as
        // padding inside its own rounded bounds (see ReaderRow).
        start = 16.dp - ReaderCard.bleed,
        end = 16.dp - ReaderCard.bleed,
      ),
    ) {
      itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
        // On at once, so it shows as the pinch starts; off with a fade.
        val highlight by animateFloatAsState(
          targetValue = if (highlightedKey == item.id) 1f else 0f,
          animationSpec = if (highlightedKey == item.id) snap() else tween(HIGHLIGHT_FADE_MS, easing = EaseOut),
          label = "pinch anchor",
        )
        when (item) {
          is ReaderItem.LineItem ->
            // Default ripple, same reasoning as ContinuationCard's own
            // clickable — see tapLine's comment.
            ReaderRow(highlight = highlight, onTap = { tapLine(index) }) {
              Box(Modifier.padding(horizontal = ReaderCard.bleed)) {
                LineRow(
                  line = item.line,
                  size = readerSize,
                  pauses = pauses,
                  schemes = schemes,
                  ratio = ratio,
                  fonts = fonts,
                  precededByTitle = precedingItemIsTitle(items, index),
                )
              }
            }
          is ReaderItem.ContinuationItem ->
            ContinuationCard(
              continuation = item.continuation,
              size = readerSize,
              fonts = fonts,
              highlight = highlight,
              onTapCard = { tapLine(index) },
              onExpand = { expandedContinuations = expandedContinuations + item.continuation.id },
            )
        }
      }
    }
  }
}
