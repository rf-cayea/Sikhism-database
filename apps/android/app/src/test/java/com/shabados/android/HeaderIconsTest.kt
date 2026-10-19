package com.shabados.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The viewer's header draws `R.drawable.ic_journeys` and `R.drawable.ic_controls`
 * directly, so a missing drawable is a compile error rather than a blank button.
 * That bypasses the generated [AppIcons] names, so this checks the two still agree:
 * renaming an icon in packages/design/icons.md must fail here, not silently leave
 * the header on the old drawable.
 *
 * Gradle runs JVM unit tests from the module directory (apps/android/app), which is
 * what the relative path below assumes.
 */
class HeaderIconsTest {
  private val drawables = File("src/main/res/drawable")

  @Test fun `the header's drawables are the ones AppIcons names`() {
    assertEquals("ic_journeys", AppIcons.journeys)
    assertEquals("ic_controls", AppIcons.controls)
  }

  @Test fun `each header drawable is imported`() {
    for (name in listOf(AppIcons.journeys, AppIcons.controls)) {
      assertTrue("$name.xml is not in ${drawables.absolutePath}", File(drawables, "$name.xml").isFile)
    }
  }
}
