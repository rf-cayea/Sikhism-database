package com.shabados.android

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.util.TypedValue

/**
 * Android's tablet line: smallest screen width of at least 600dp, the platform's own
 * `sw600dp` qualifier (decided 2026-10-14, packages/design/tokens.md#type). Smallest
 * width rather than current width so rotation and split screen do not change the
 * answer. An unfolded foldable is a tablet; folded, a phone.
 */
private const val TABLET_SMALLEST_WIDTH_DP = 600

val Configuration.isTablet: Boolean get() = smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP

/**
 * The reader's size in the platform's SharedPreferences. Not Jetpack DataStore,
 * which would be a dependency for one float.
 */
class PreferencesSizeStore(private val prefs: SharedPreferences) : SizeStore {
  override fun get(): Float? = if (prefs.contains(KEY)) prefs.getFloat(KEY, 0f) else null
  override fun set(size: Float) = prefs.edit().putFloat(KEY, size).apply()

  private companion object {
    const val KEY = "fontSize"
  }
}

fun Context.readerSizeStore() = PreferencesSizeStore(getSharedPreferences("reader", Context.MODE_PRIVATE))

/** What the reader shows before the person changes anything
 * (docs/requirements/display-controls.md#pauses, #pronunciations, #ratio). */
object ReaderDefaults {
  const val PAUSES = true
  const val PRON_DEVANAGARI = false
  const val PRON_LATIN = false
  const val RATIO = DesignTokens.RATIO_DEFAULT
}

/**
 * Pauses, pronunciations and ratio, saved across launches as on iOS
 * (docs/requirements/display-controls.md#persistence). The keys are iOS's
 * `@AppStorage` keys in BaniReaderView.swift, so a setting has one name on both.
 * **A value is written only when the person changes it**, so a setting never
 * touched keeps following [ReaderDefaults] if a default changes.
 */
class ReaderSettings(private val prefs: SharedPreferences) {
  var pauses: Boolean
    get() = prefs.getBoolean(PAUSES, ReaderDefaults.PAUSES)
    set(value) = prefs.edit().putBoolean(PAUSES, value).apply()

  var pronDevanagari: Boolean
    get() = prefs.getBoolean(PRON_DEVANAGARI, ReaderDefaults.PRON_DEVANAGARI)
    set(value) = prefs.edit().putBoolean(PRON_DEVANAGARI, value).apply()

  var pronLatin: Boolean
    get() = prefs.getBoolean(PRON_LATIN, ReaderDefaults.PRON_LATIN)
    set(value) = prefs.edit().putBoolean(PRON_LATIN, value).apply()

  /** Read only: Android has no Ratio control yet. Read anyway so that, once one
   * lands, a saved ratio is already honoured. */
  val ratio: Float get() = prefs.getFloat(RATIO, ReaderDefaults.RATIO)

  private companion object {
    const val PAUSES = "pauses"
    const val PRON_DEVANAGARI = "pronDevanagari"
    const val PRON_LATIN = "pronLatin"
    const val RATIO = "ratio"
  }
}

fun Context.readerSettings() = ReaderSettings(getSharedPreferences("reader", Context.MODE_PRIVATE))

/**
 * The dp Android gives [DesignTokens.ANDROID_BODY_SIZE] sp at the person's Font size.
 * `applyDimension` goes through the display's font scale converter, so this follows
 * Android's non-linear scaling above ~1.03, not a straight multiple.
 */
fun Resources.bodyDp(): Float =
  TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DesignTokens.ANDROID_BODY_SIZE, displayMetrics) /
    displayMetrics.density
