package com.shabados.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

// The reading shell's rules: what is being read, when a journey ends, and how the
// Journeys sidebar opens. Compose state but no drawing, so the JVM tests in src/test
// can pin each rule (CLAUDE.md: every domain rule needs a test that fails when it
// breaks). ReaderShell.kt only measures and draws.

/** The bani last read, and when, in wall-clock milliseconds: what `continue
 * reading` offers ([ContinueReading]). [line] is the line that was at the top, by
 * `ReaderItem.id`, which `continue reading` returns to; null for the top of the bani.
 * (docs/requirements/journeys.md: "End a journey, reopen it ⇒ the same tabs, each at
 * the position it held".) */
data class LastRead(val id: String, val atMs: Long, val line: String? = null)

/** Where [LastRead] is saved: SharedPreferences in the app, a field in tests. */
interface LastReadStore {
  fun get(): LastRead?
  fun set(value: LastRead)
}

/**
 * What the viewer shows, the journey's idle boundary
 * (docs/requirements/journeys.md#journeys), and what `continue reading` offers.
 * [now] is a monotonic clock in milliseconds (`SystemClock.elapsedRealtime` in the
 * app), for the idle boundary; [wallNow] is the wall clock
 * (`System.currentTimeMillis`), for when something was read, which must mean the
 * same thing after the process is gone.
 *
 * **The open bani is held in process memory only** (see `Session` in
 * MainActivity.kt). Rotation and other configuration changes keep it, because the
 * process survives them. Process death loses it, and the app opens to the default
 * screen as a new journey (decided 2026-10-16, as an iOS cold launch). **What was
 * last read is saved** ([store]), so that screen can still offer `continue reading`
 * ([ContinueReading], decided 2026-10-16).
 */
class ShellState(
  private val now: () -> Long,
  private val wallNow: () -> Long,
  private val store: LastReadStore,
) {
  /** The bani being read, by `Bani.id`; null shows the default screen. */
  var openId by mutableStateOf<String?>(null)
    private set

  /** What `continue reading` offers: the bani last read, while [ContinueReading]
   * allows. Restored from [store] when the process starts. */
  var previous by mutableStateOf(store.get()?.takeIf { ContinueReading.offers(it, wallNow()) })
    private set

  val previousId: String? get() = previous?.id

  var journeysOpen by mutableStateOf(false)
    private set

  /** The line the reader opens at, by `ReaderItem.id`: where `continue reading` left
   * off, or null for the top. Read once, when a reader is created. */
  var startLine: String? = null
    private set

  /** The line at the top of the reader now, reported as it scrolls. **A plain field,
   * deliberately not observed**: it changes with every scroll and must not recompose
   * anything. Saved with [LastRead]. */
  var readingLine: String? = null

  /** **A plain field, deliberately not observed**: it is written on every touch,
   * and must not recompose anything. */
  private var lastActivity = now()

  /** Any use of the app: a tap, a scroll, a pinch, the sidebar. */
  fun touch() {
    lastActivity = now()
  }

  /** Every path out of the sidebar closes it
   * (docs/requirements/reading-shell.md#getting-between-them). */
  fun open(id: String) {
    touch()
    openId = id
    startLine = null
    readingLine = null
    journeysOpen = false
    store.set(LastRead(id, wallNow()))
  }

  /** `continue reading`: opens [previous] at the line that was at its top. */
  fun resume() {
    val read = previous ?: return
    touch()
    openId = read.id
    startLine = read.line
    readingLine = read.line
    journeysOpen = false
    store.set(read.copy(atMs = wallNow()))
  }

  /** When the open bani was last read, on the wall clock: the last activity, which
   * was measured on the monotonic clock. */
  private fun lastReadAt(): Long = wallNow() - (now() - lastActivity)

  /** Saves when the open bani was last read. Called as the app leaves the screen,
   * since a process in the background can be killed without warning; not on every
   * touch, which would write storage many times a second. */
  fun saveLastRead() {
    val id = openId ?: return
    store.set(LastRead(id, lastReadAt(), readingLine))
  }

  /**
   * Opens or closes the Journeys sidebar. **Returns whether this opened it**, which
   * is when the light haptic plays: on opening, by button or drag, never on closing
   * (docs/interaction.md, "Opening a sidebar gives a light haptic").
   */
  fun setJourneys(open: Boolean): Boolean {
    touch()
    val opened = open && !journeysOpen
    journeysOpen = open
    return opened
  }

  /** System Back is the app's while the sidebar is open, and closes it
   * (docs/interaction.md: "Android's system Back must close a sidebar"). Closed, Back
   * is left to the system. */
  val handlesBack: Boolean get() = journeysOpen

  /** Whether the screen is held awake: content open in the viewer, no sidebar
   * (reading-shell.md#the-screen-stays-awake-while-content-is-read). The 15-minute
   * limit needs nothing here: going idle ends the journey, which closes the content. */
  val keepsScreenOn: Boolean get() = openId != null && !journeysOpen

  fun back() {
    setJourneys(open = false)
  }

  /**
   * Ends the journey once [IDLE_LIMIT_MS] has passed without activity, open or in
   * the background; what was being read becomes `continue reading`. And withdraws
   * `continue reading` once [ContinueReading] no longer allows it, so a default
   * screen left showing does not keep offering it. Run periodically and on return.
   */
  fun refresh() {
    previous?.let { if (!ContinueReading.offers(it, wallNow())) previous = null }
    if (now() - lastActivity < IDLE_LIMIT_MS) return
    val id = openId ?: return
    val read = LastRead(id, lastReadAt(), readingLine)
    store.set(read)
    previous = read.takeIf { ContinueReading.offers(it, wallNow()) }
    openId = null
  }

  companion object {
    /** Settled in docs/requirements/journeys.md#journeys: 15 minutes without
     * activity ends a journey, including while the app is open. */
    const val IDLE_LIMIT_MS = 15 * 60 * 1000L
  }
}

/**
 * When the default screen offers `continue reading`
 * (docs/requirements/reading-shell.md#the-default-screen, decided 2026-10-16): **the
 * bani last read, if it is a daily bani read less than [DAILY_WINDOW_MS] ago**,
 * whether or not the app was closed since. One rule, so the answer does not depend
 * on how the app was left.
 */
object ContinueReading {
  /** The seven bundled banis: Morning Nitnem's five, Rehras Sahib and Kirtan Sohila
   * (library.md#base-collections), all read daily. */
  val DAILY_IDS = setOf("JAPJ", "JAAP", "TPSS", "BNCP", "ANND", "RHRS", "SHLA")

  const val DAILY_WINDOW_MS = 8 * 60 * 60 * 1000L

  /** A time ahead of [nowMs] (the clock was set back) is not offered: it cannot be
   * told how long ago it was. */
  fun offers(read: LastRead, nowMs: Long): Boolean =
    read.id in DAILY_IDS && nowMs - read.atMs in 0 until DAILY_WINDOW_MS
}

/** The default screen's pills
 * (docs/requirements/reading-shell.md#the-default-screen). */
object DefaultScreenPills {
  /**
   * By `Bani.id`, in order (chosen 2026-10-15, as on iOS): Jap Ji Sahib, Rehras
   * Sahib, Kirtan Sohila, Ardas. A fixed stand-in for "what is due now", which needs
   * goals and schedules. Ardas is not bundled yet; an id not in the corpus is
   * skipped, so it appears once `ARDS` is exported.
   */
  val IDS = listOf("JAPJ", "RHRS", "SHLA", "ARDS")

  fun of(banis: List<Bani>): List<Bani> = IDS.mapNotNull { banis.withId(it) }
}

/**
 * How the Journeys sidebar opens beneath the viewer
 * (docs/requirements/reading-shell.md#getting-between-them). The amounts are iOS's
 * (`ReaderShell.swift`), first passes there.
 *
 * `progress` is 0 with the viewer covering the sidebar and 1 with it aside.
 */
object Sidebar {
  /** How much of the viewer stays in view while Journeys is open, in dp. Matched by
   * eye on iOS to the sliver other apps leave (2026-10-15). */
  const val PEEK_DP = 72f

  /** How visible the sidebar is with the viewer covering it, and how far the viewer
   * has slid by the time it is fully visible (chosen on iOS 2026-10-16). */
  const val START_STRENGTH = 0.4f
  const val FULL_AT = 0.5f

  /** The sidebar's size with the viewer covering it. It reaches full size only at
   * the end of the slide, though fully visible from [FULL_AT]. */
  const val START_SCALE = 0.95f

  /** How much more horizontal than vertical a drag must be to move the viewer; as on
   * iOS. Anything steeper is a scroll. */
  const val HORIZONTAL_RATIO = 1.5f

  /** How far ahead of the finger a released drag is judged, in seconds of its
   * velocity, so a short quick flick still opens or closes. iOS uses
   * `predictedEndTranslation`, which Compose has no counterpart for. First pass. */
  const val FLING_LOOKAHEAD_S = 0.2f

  /**
   * How far the viewer slides, which is also the sidebar's width: **the screen's
   * short side less the peek, plus the inset at its left edge**, so the sidebar's
   * contents are as wide in landscape as in portrait (decided 2026-10-16). In
   * landscape the viewer then leaves more than the peek in view. Any units, as long
   * as all four agree.
   *
   * [leftInset] is a camera cutout or navigation bar on the left in landscape. The
   * panel is padded clear of it inside this width, so without adding it back the
   * panel was that much narrower in landscape (2026-10-19; iOS's
   * `ReaderShell.travel` is the same rule). In portrait it is 0.
   */
  fun travel(width: Float, height: Float, peek: Float, leftInset: Float): Float =
    (minOf(width, height) - peek + leftInset).coerceAtLeast(0f)

  fun strength(progress: Float): Float =
    START_STRENGTH + (1 - START_STRENGTH) * (progress / FULL_AT).coerceIn(0f, 1f)

  fun scale(progress: Float): Float = START_SCALE + (1 - START_SCALE) * progress.coerceIn(0f, 1f)

  /** What a drag is, once it has said. */
  enum class Axis { HORIZONTAL, OTHER }

  /**
   * What a one-finger drag that has moved ([dx], [dy]) is: null until it has moved
   * [decision] pixels, then decided once for its whole length. **Horizontal only if
   * it is mostly sideways and towards a change**: rightward while closed, leftward
   * while open. Anything else is a scroll, or a drag the other way, and is left
   * alone (docs/interaction.md, "Where the sidebar drag may start").
   */
  fun judge(dx: Float, dy: Float, decision: Float, open: Boolean): Axis? {
    if (maxOf(abs(dx), abs(dy)) < decision) return null
    val towardsChange = if (open) dx < 0 else dx > 0
    return if (abs(dx) > abs(dy) * HORIZONTAL_RATIO && towardsChange) Axis.HORIZONTAL else Axis.OTHER
  }

  /**
   * Whether a drag that starts at [x] may move the viewer. **On Android, inside the
   * content only**: the screen edges, [leftEdge] and [rightEdge] wide, belong to
   * gesture navigation's Back (docs/interaction.md, decided 2026-10-15). With
   * three-button navigation the edges are 0 wide and a drag may start anywhere.
   */
  fun startsInContent(x: Float, width: Float, leftEdge: Float, rightEdge: Float): Boolean =
    x >= leftEdge && x <= width - rightEdge

  /** Where a drag of [dx] has moved the viewer to, from [open], over [travel]. */
  fun progress(open: Boolean, dx: Float, travel: Float): Float {
    val base = if (open) 1f else 0f
    if (travel <= 0f) return base
    return (base + dx / travel).coerceIn(0f, 1f)
  }

  /** Whether a drag released at [dx], moving at [velocityX] pixels a second, leaves
   * the sidebar open: where a flick would carry it, past halfway. */
  fun landsOpen(open: Boolean, dx: Float, velocityX: Float, travel: Float): Boolean =
    progress(open, dx + velocityX * FLING_LOOKAHEAD_S, travel) > 0.5f
}

/** Tapping a line scrolls it to the top
 * (docs/requirements/display-controls.md#tap-a-line). */
object TapScroll {
  /** On both platforms, easing in and out with no overshoot (2026-10-16). */
  const val DURATION_MS = 500

  /**
   * How far to scroll for a tap on the row at [index], whose top is [rowTop] pixels
   * below where the list's content starts; null for no scroll. **The bani's first
   * row never scrolls**: there is nothing above it, and scrolling it would only hide
   * the space before it (iOS's `tapLine`, verified there on screen recordings).
   */
  fun distance(index: Int, rowTop: Float): Float? = if (index == 0) null else rowTop
}
