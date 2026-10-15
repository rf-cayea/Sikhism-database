package com.shabados.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The reader toolbar draws `R.drawable.ic_back` and `R.drawable.ic_controls`
 * directly, so a missing drawable is a compile error rather than a blank button.
 * That bypasses the generated [AppIcons] names, so this checks the two still agree:
 * renaming an icon in packages/design/icons.md must fail here, not silently leave
 * the toolbar on the old drawable.
 *
 * Gradle runs JVM unit tests from the module directory (apps/android/app), which is
 * what the relative path below assumes.
 */
class ToolbarIconsTest {
  private val drawables = File("src/main/res/drawable")

  @Test fun `the toolbar's drawables are the ones AppIcons names`() {
    assertEquals("ic_back", AppIcons.back)
    assertEquals("ic_controls", AppIcons.controls)
  }

  @Test fun `each toolbar drawable is imported`() {
    for (name in listOf(AppIcons.back, AppIcons.controls)) {
      assertTrue("$name.xml is not in ${drawables.absolutePath}", File(drawables, "$name.xml").isFile)
    }
  }
}
