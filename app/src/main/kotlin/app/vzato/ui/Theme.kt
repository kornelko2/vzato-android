// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Status and medication colors, the same as Palette in the iOS app. */
object Palette {
    val accent = Color(0xFF1E7B46)
    val taken = Color(0xFF1E7B46)
    val overdue = Color(0xFFB4530A)
    val missed = Color(0xFFC0392B)
    val skipped = Color(0xFF8A7F5A)

    /** Medication colors. Each differs in lightness as well as hue. */
    val medicationHexes = listOf("D9534F", "E0A526", "3B7DD8", "1E7B46", "7B52AB", "C2185B", "00838F", "6D4C41")

    /** "RRGGBB" with or without #; anything else falls back to the accent color. */
    fun fromHex(hex: String?): Color {
        val value = hex?.trim()?.removePrefix("#") ?: return accent
        val number = value.takeIf { it.length == 6 }?.toLongOrNull(16) ?: return accent
        return Color(0xFF000000 or number)
    }
}

private val light = lightColorScheme(
    primary = Palette.accent,
    onPrimary = Color.White,
    secondary = Palette.accent,
    tertiary = Palette.overdue,
    error = Palette.missed,
    primaryContainer = Color(0xFFCFE8D7),
    onPrimaryContainer = Color(0xFF0F3D22),
    secondaryContainer = Color(0xFFCFE8D7),
    onSecondaryContainer = Color(0xFF0F3D22),
    tertiaryContainer = Color(0xFFF6E0CC),
    onTertiaryContainer = Color(0xFF4A2205),
    // Neutral surfaces: Material's defaults carry a purple tint.
    background = Color(0xFFF8F8F6),
    surface = Color(0xFFF8F8F6),
    surfaceContainer = Color(0xFFEFEFEC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3F1),
    surfaceContainerHigh = Color(0xFFF1F1EE),
    surfaceContainerHighest = Color(0xFFE8E8E4),
    surfaceVariant = Color(0xFFE6E6E2),
    inversePrimary = Color(0xFF8FD4A8),
    surfaceTint = Color.Transparent,
)

private val dark = darkColorScheme(
    primary = Color(0xFF4CAF78),
    onPrimary = Color.White,
    secondary = Color(0xFF4CAF78),
    tertiary = Color(0xFFE08A3C),
    error = Color(0xFFE57368),
    primaryContainer = Color(0xFF1F4D32),
    onPrimaryContainer = Color(0xFFCFE8D7),
    secondaryContainer = Color(0xFF1F4D32),
    onSecondaryContainer = Color(0xFFCFE8D7),
    tertiaryContainer = Color(0xFF5A3313),
    onTertiaryContainer = Color(0xFFF6E0CC),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerLowest = Color(0xFF0C0C0C),
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    surfaceVariant = Color(0xFF2A2A2A),
    inversePrimary = Color(0xFF1E7B46),
    surfaceTint = Color.Transparent,
)

/**
 * Vzato's own green instead of the wallpaper colors (dynamic color), so the app looks the same
 * everywhere and matches the iOS design direction C.
 */
@Composable
fun VzatoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content)
}
