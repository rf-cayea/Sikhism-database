package com.shabados.android

import android.content.res.AssetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/**
 * Sant Lipi is bundled rather than relying on system Gurmukhi fonts, whose coverage
 * of conjuncts and yayya variants is poor on both platforms.
 */
private fun santLipi(assets: AssetManager) =
  FontFamily(Font(path = "SantLipi-VF.ttf", assetManager = assets))

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Before any UI, so the reader only ever reads a saved size
    // (docs/requirements/display-controls.md#starting-size). Does nothing once a
    // size is saved.
    ReaderSize.saveStartingSizeOnce(readerSizeStore(), resources.configuration.isTablet) { resources.bodyDp() }
    enableEdgeToEdge()
    setContent {
      ShabadOSTheme(dark = isSystemInDarkTheme()) { App() }
    }
  }
}

@Composable
private fun App() {
  val assets = LocalContext.current.assets
  val banis = remember { Corpus.load(assets) }
  val gurmukhi = remember { santLipi(assets) }
  // Saveable, so rotation, a theme or font size change, and process death (all of
  // which recreate the activity) leave the reader open. By id: a Bani is not
  // Parcelable, and the id is stable across corpus versions.
  var openId by rememberSaveable { mutableStateOf<String?>(null) }

  val current = banis.withId(openId)
  if (current == null) {
    BaniList(banis, gurmukhi) { openId = it.id }
  } else {
    BackHandler { openId = null }
    BaniReader(current) { openId = null }
  }
}
