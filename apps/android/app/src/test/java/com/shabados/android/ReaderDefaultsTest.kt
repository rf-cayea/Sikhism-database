package com.shabados.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the reader shows before the person changes anything. These are what an
 * unwritten [ReaderControls] value falls back to, on every launch until it is set.
 *
 * What this cannot check: that a changed setting survives a relaunch. That is
 * SharedPreferences, checked by hand (README.md#tests).
 */
class ReaderDefaultsTest {
  @Test fun `pauses are on by default`() {
    // docs/requirements/display-controls.md#pauses
    assertTrue(ReaderDefaults.PAUSES)
  }

  @Test fun `both pronunciations are off by default`() {
    // docs/requirements/display-controls.md: "All off by default."
    assertFalse(ReaderDefaults.PRON_DEVANAGARI)
    assertFalse(ReaderDefaults.PRON_LATIN)
  }

  @Test fun `the ratio defaults to 60 percent`() {
    // docs/requirements/display-controls.md#ratio. Through the shared token, so
    // both platforms start from packages/design/tokens.md.
    assertEquals(DesignTokens.RATIO_DEFAULT, ReaderDefaults.RATIO)
    assertEquals(0.6f, ReaderDefaults.RATIO)
  }

  @Test fun `weight starts at 500`() {
    // display-controls.md#weight, decided 2026-10-19.
    assertEquals(500f, ReaderDefaults.WEIGHT)
  }
}
