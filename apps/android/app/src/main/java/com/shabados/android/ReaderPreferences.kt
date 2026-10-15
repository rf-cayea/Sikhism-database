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

/**
 * The dp Android gives [DesignTokens.ANDROID_BODY_SIZE] sp at the person's Font size.
 * `applyDimension` goes through the display's font scale converter, so this follows
 * Android's non-linear scaling above ~1.03, not a straight multiple.
 */
fun Resources.bodyDp(): Float =
  TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DesignTokens.ANDROID_BODY_SIZE, displayMetrics) /
    displayMetrics.density
