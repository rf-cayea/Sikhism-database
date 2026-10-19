package com.shabados.android

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Maps the shared design tokens onto a Material colour scheme.
 *
 * Colours themselves live in `packages/design/tokens.md` — edit there, not here.
 *
 * `MaterialTheme { }` with no colorScheme uses Material 3's baseline palette, which
 * is purple-tinted; that is where the lavender background came from, not from
 * anything Android imposes. **Dynamic colour (Material You) is not used anywhere**
 * (decided 2026-10-16): it tints from the user's wallpaper, so the same shabad would
 * look different on every device and could never be matched to iOS. It was tried for
 * the Journeys sidebar and the viewer's controls, and set them apart from the rest of
 * the app; both are on these tokens, as on iOS.
 */
// secondary/tertiary and the *Container slots are set even though nothing uses them
// yet: any slot left unset keeps Material's baseline purple, so the first component
// that reaches for one reintroduces the tint this theme exists to remove.
private val Light: ColorScheme = lightColorScheme(
  primary = DesignTokens.uiLight,
  onPrimary = DesignTokens.backgroundBaseLight,
  secondary = DesignTokens.uiLight,
  onSecondary = DesignTokens.backgroundBaseLight,
  tertiary = DesignTokens.uiLight,
  onTertiary = DesignTokens.backgroundBaseLight,
  primaryContainer = DesignTokens.backgroundBaseLight,
  onPrimaryContainer = DesignTokens.foregroundLight,
  secondaryContainer = DesignTokens.backgroundBaseLight,
  onSecondaryContainer = DesignTokens.foregroundLight,
  tertiaryContainer = DesignTokens.backgroundBaseLight,
  onTertiaryContainer = DesignTokens.foregroundLight,
  background = DesignTokens.backgroundLight,
  onBackground = DesignTokens.foregroundLight,
  surface = DesignTokens.backgroundLight,
  onSurface = DesignTokens.foregroundLight,
  surfaceVariant = DesignTokens.backgroundBaseLight,
  onSurfaceVariant = DesignTokens.foregroundMutedLight,
  outline = DesignTokens.foregroundMutedLight,
  outlineVariant = DesignTokens.foregroundLight.copy(alpha = DesignTokens.TONER_OPACITY),
)

private val Dark: ColorScheme = darkColorScheme(
  primary = DesignTokens.uiDark,
  onPrimary = DesignTokens.backgroundDark,
  secondary = DesignTokens.uiDark,
  onSecondary = DesignTokens.backgroundDark,
  tertiary = DesignTokens.uiDark,
  onTertiary = DesignTokens.backgroundDark,
  primaryContainer = DesignTokens.backgroundBaseDark,
  onPrimaryContainer = DesignTokens.foregroundDark,
  secondaryContainer = DesignTokens.backgroundBaseDark,
  onSecondaryContainer = DesignTokens.foregroundDark,
  tertiaryContainer = DesignTokens.backgroundBaseDark,
  onTertiaryContainer = DesignTokens.foregroundDark,
  background = DesignTokens.backgroundDark,
  onBackground = DesignTokens.foregroundDark,
  surface = DesignTokens.backgroundDark,
  onSurface = DesignTokens.foregroundDark,
  surfaceVariant = DesignTokens.backgroundBaseDark,
  onSurfaceVariant = DesignTokens.foregroundMutedDark,
  outline = DesignTokens.foregroundMutedDark,
  outlineVariant = DesignTokens.foregroundDark.copy(alpha = DesignTokens.TONER_OPACITY),
)

/**
 * Also sets the content colour to the text colour, for the whole app. Ripples and
 * icons are drawn in it. A `Scaffold` or `Surface` sets it; the reading shell has
 * neither, and Material's default is black, so taps' ripples and the header's icons
 * were black on black in dark mode (Pixel 7a, 2026-10-16).
 */
@Composable
fun ShabadOSTheme(dark: Boolean, content: @Composable () -> Unit) {
  val scheme = if (dark) Dark else Light
  MaterialTheme(colorScheme = scheme) {
    CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
  }
}
