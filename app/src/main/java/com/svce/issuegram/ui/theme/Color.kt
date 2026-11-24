package com.svce.issuegram.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Brand palette from the Expressive Kit tokens (warm clay primary, teal secondary, plum tertiary).

val ExpressiveLightColors = lightColorScheme(
    primary = Color(0xFFA8330F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3B0900),
    inversePrimary = Color(0xFFFFB59C),
    secondary = Color(0xFF2C6765),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBDEEEA),
    onSecondaryContainer = Color(0xFF00201F),
    tertiary = Color(0xFF7A4E8F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3DAFF),
    onTertiaryContainer = Color(0xFF2F0A44),
    surface = Color(0xFFFBF6EE),
    onSurface = Color(0xFF1F1A16),
    onSurfaceVariant = Color(0xFF54463D),
    inverseSurface = Color(0xFF362F2B),
    inverseOnSurface = Color(0xFFFBEEE8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD9C7BC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6EFE6),
    surfaceContainer = Color(0xFFF1E9DF),
    surfaceContainerHigh = Color(0xFFEBE2D6),
    surfaceContainerHighest = Color(0xFFE4DACD),
    background = Color(0xFFFBF6EE),
    onBackground = Color(0xFF1F1A16),
    scrim = Color(0x66140C08)
)

val ExpressiveDarkColors = darkColorScheme(
    primary = Color(0xFFFFB59C),
    onPrimary = Color(0xFF5C1900),
    primaryContainer = Color(0xFF7C2A0E),
    onPrimaryContainer = Color(0xFFFFDBCF),
    inversePrimary = Color(0xFFA8330F),
    secondary = Color(0xFF97D1CE),
    onSecondary = Color(0xFF003735),
    secondaryContainer = Color(0xFF1D4E4C),
    onSecondaryContainer = Color(0xFFBDEEEA),
    tertiary = Color(0xFFE3B6FB),
    onTertiary = Color(0xFF46205A),
    tertiaryContainer = Color(0xFF5B3A6C),
    onTertiaryContainer = Color(0xFFF3DAFF),
    surface = Color(0xFF1A1210),
    onSurface = Color(0xFFF0E1D9),
    onSurfaceVariant = Color(0xFFD8C3B8),
    inverseSurface = Color(0xFFF0E1D9),
    inverseOnSurface = Color(0xFF362F2B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA28E85),
    outlineVariant = Color(0xFF54453E),
    surfaceContainerLowest = Color(0xFF140D0B),
    surfaceContainerLow = Color(0xFF221815),
    surfaceContainer = Color(0xFF271D19),
    surfaceContainerHigh = Color(0xFF322723),
    surfaceContainerHighest = Color(0xFF3D312C),
    background = Color(0xFF1A1210),
    onBackground = Color(0xFFF0E1D9),
    scrim = Color(0x8F000000)
)

/** Roles Material 3 has no slot for: the loud highlight fill and gain/success colours. */
@Immutable
data class ExtendedColors(
    val highlight: Color,
    val onHighlight: Color,
    val positive: Color,
    val positiveContainer: Color,
    val onPositiveContainer: Color
)

val ExpressiveLightExtended = ExtendedColors(
    highlight = Color(0xFFFC9500),
    onHighlight = Color(0xFF2B1700),
    positive = Color(0xFF1B6D3B),
    positiveContainer = Color(0xFFC3EFCF),
    onPositiveContainer = Color(0xFF00210C)
)

val ExpressiveDarkExtended = ExtendedColors(
    highlight = Color(0xFFFFB84D),
    onHighlight = Color(0xFF2B1700),
    positive = Color(0xFF7FD9A3),
    positiveContainer = Color(0xFF0F4A28),
    onPositiveContainer = Color(0xFFC3EFCF)
)

val LocalExtendedColors = staticCompositionLocalOf { ExpressiveLightExtended }

/**
 * True-black variant of a dark scheme for OLED screens. The page goes black and the low
 * containers darken, so cards on `surfaceContainer` and above separate more, not less.
 */
fun ColorScheme.toAmoledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(surfaceContainerLow, Color.Black, 0.6f),
    surfaceContainer = lerp(surfaceContainer, Color.Black, 0.35f),
    scrim = Color.Black
)
