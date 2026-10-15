package com.shabados.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The open bani survives activity recreation as a saved id, looked up again in the
 * corpus. An id the corpus no longer has must show the bani list, never crash.
 *
 * What this cannot check: that the id is actually saved and restored. That needs a
 * Compose UI test, which this project does not have (README.md#tests), so it is
 * checked by hand.
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

  @Test fun `an id the corpus no longer has opens nothing, so the list shows`() {
    assertNull(banis.withId("no-longer-in-the-corpus"))
  }
}
