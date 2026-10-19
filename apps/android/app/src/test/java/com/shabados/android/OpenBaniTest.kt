package com.shabados.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The open bani is held by id ([ShellState.openId]) and looked up in the corpus. An
 * id the corpus no longer has must show the default screen, never crash.
 *
 * What this cannot check: that the id survives the activity being recreated. That
 * needs a Compose UI test, which this project does not have (README.md#tests), so it
 * is checked by hand (scripts/check-restore.ts).
 */
class OpenBaniTest {
  private fun bani(id: String) = Bani(id, mapOf("Latn" to id), sections = emptyList(), continuations = emptyList())
  private val banis = listOf(bani("japji-sahib"), bani("rehras-sahib"))

  @Test fun `a saved id opens that bani`() {
    assertEquals("rehras-sahib", banis.withId("rehras-sahib")?.id)
  }

  @Test fun `nothing saved opens nothing`() {
    assertNull(banis.withId(null))
  }

  @Test fun `an id the corpus no longer has opens nothing, so the default screen shows`() {
    assertNull(banis.withId("no-longer-in-the-corpus"))
  }
}
