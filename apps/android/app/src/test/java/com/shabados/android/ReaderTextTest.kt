package com.shabados.android

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.TextUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What reading text inherits from the theme. Each case is a way a line drawn at a
 * large Zoom has gone wrong.
 */
class ReaderTextTest {
  @Test fun `reading text inherits no line height from the theme`() {
    // The theme's Body line height is a fixed 24sp. Inherited, a secondary line at
    // a large Zoom kept 24sp and its wrapped lines overlapped (2026-10-22).
    val style = readerTextStyle(Typography().bodyLarge)
    assertEquals(TextUnit.Unspecified, style.lineHeight)
  }
}
