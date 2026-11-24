package com.svce.issuegram.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The Issuegram theme, built on Material 3 Expressive.
 *
 * Motion tokens map onto [MaterialTheme.motionScheme][androidx.compose.material3.MaterialTheme]:
 *   spatial fast/default/slow  -> fastSpatialSpec() / defaultSpatialSpec() / slowSpatialSpec()
 *   effects fast/default/slow  -> fastEffectsSpec() / defaultEffectsSpec() / slowEffectsSpec()
 * Read them from the scheme rather than hard-coding springs.
 *
 * @param motionScheme [MotionScheme.expressive] for consumer and playful screens (overshoot),
 *   [MotionScheme.standard] for productivity and utility screens (no visible bounce).
 * @param dynamicColor derive colours from the wallpaper on Android 12+. Off by default so the
 *   brand palette shows; keep brand moments on brand colours either way.
 * @param amoledBlack in dark theme, use a true-black page for OLED screens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IssuegramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionScheme: MotionScheme = MotionScheme.expressive(),
    dynamicColor: Boolean = false,
    amoledBlack: Boolean = false,
    typography: Typography = ExpressiveTypography,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> ExpressiveDarkColors
        else -> ExpressiveLightColors
    }
    val amoled = darkTheme && amoledBlack
    val colorScheme = remember(base, amoled) { if (amoled) base.toAmoledBlack() else base }
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) ExpressiveDarkExtended else ExpressiveLightExtended,
        // Tonal elevation would paint grey back over a true-black page.
        LocalTonalElevationEnabled provides !amoled
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = ExpressiveShapes,
            typography = typography,
            content = content
        )
    }
}

/** Access to the roles Material 3 has no slot for (highlight, positive). */
object Expressive {
    val extendedColors: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}
