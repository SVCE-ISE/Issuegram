@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalTextApi::class)

package com.svce.issuegram.ui.theme

import androidx.annotation.FontRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.svce.issuegram.R

/**
 * Type system from the Expressive Kit.
 *
 * Fraunces (open source) is the display face. Roboto Flex stands in for the proprietary
 * Google Sans Flex on UI text and Roboto Mono for Google Sans Code on data labels — both
 * are variable fonts, so each cut is pinned to the style's own optical size (`opsz`).
 */

private val familyCache = HashMap<Pair<Int, Int>, FontFamily>()

/**
 * A variable font pinned to one optical size, with real cuts at each weight.
 * M3 type tokens set `opsz` equal to the font size, so small text gets the sturdier
 * text cut and large text the tighter display cut from one font file.
 */
fun variableFontFamily(
    @FontRes resId: Int,
    opticalSize: Float,
    weights: IntArray = intArrayOf(400, 500, 600, 700, 800),
): FontFamily = familyCache.getOrPut(resId to opticalSize.toInt()) {
    FontFamily(
        weights.map { w ->
            Font(
                resId = resId,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(w),
                    FontVariation.Setting("opsz", opticalSize.coerceIn(6f, 144f)),
                ),
            )
        },
    )
}

private fun variableWeightFamily(@FontRes resId: Int): FontFamily = FontFamily(
    intArrayOf(400, 500, 600, 700).map { w ->
        Font(
            resId = resId,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

val GoogleSans: FontFamily = variableFontFamily(R.font.google_sans_flex, 14f)
fun googleSansFamily(size: Float = 14f): FontFamily = variableFontFamily(R.font.google_sans_flex, size)

val MonoFamily: FontFamily = variableWeightFamily(R.font.roboto_mono)

private fun sansFamily(size: Float): FontFamily = variableFontFamily(R.font.google_sans_flex, size)
private fun displayFamily(size: Float): FontFamily = variableFontFamily(R.font.google_sans_flex, size)

/** The fifteen Material styles plus their emphasized twins, with the kit's weights and tracking. */
fun expressiveTypography(
    sans: (Float) -> FontFamily = ::sansFamily,
    display: (Float) -> FontFamily = ::displayFamily,
): Typography = Typography(
    displayLarge = TextStyle(fontFamily = display(57f), fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight(600), letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = display(45f), fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight(600), letterSpacing = (-0.25).sp),
    displaySmall = TextStyle(fontFamily = display(36f), fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight(600), letterSpacing = (0).sp),
    headlineLarge = TextStyle(fontFamily = sans(32f), fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight(700), letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = sans(28f), fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight(600), letterSpacing = (0).sp),
    headlineSmall = TextStyle(fontFamily = sans(24f), fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight(600), letterSpacing = (0).sp),
    titleLarge = TextStyle(fontFamily = sans(22f), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight(600), letterSpacing = (0).sp),
    titleMedium = TextStyle(fontFamily = sans(16f), fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight(600), letterSpacing = (0.1).sp),
    titleSmall = TextStyle(fontFamily = sans(14f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(600), letterSpacing = (0.1).sp),
    bodyLarge = TextStyle(fontFamily = sans(16f), fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight(400), letterSpacing = (0).sp),
    bodyMedium = TextStyle(fontFamily = sans(14f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(400), letterSpacing = (0.25).sp),
    bodySmall = TextStyle(fontFamily = sans(12f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(400), letterSpacing = (0.4).sp),
    labelLarge = TextStyle(fontFamily = sans(14f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(600), letterSpacing = (0.1).sp),
    labelMedium = TextStyle(fontFamily = sans(12f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(600), letterSpacing = (0.5).sp),
    labelSmall = TextStyle(fontFamily = sans(11f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight(600), letterSpacing = (0.5).sp),
    displayLargeEmphasized = TextStyle(fontFamily = display(57f), fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight(700), letterSpacing = (-0.5).sp),
    displayMediumEmphasized = TextStyle(fontFamily = display(45f), fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight(700), letterSpacing = (-0.25).sp),
    displaySmallEmphasized = TextStyle(fontFamily = display(36f), fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight(700), letterSpacing = (0).sp),
    headlineLargeEmphasized = TextStyle(fontFamily = sans(32f), fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp),
    headlineMediumEmphasized = TextStyle(fontFamily = sans(28f), fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight(700), letterSpacing = (0).sp),
    headlineSmallEmphasized = TextStyle(fontFamily = sans(24f), fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight(700), letterSpacing = (0).sp),
    titleLargeEmphasized = TextStyle(fontFamily = sans(22f), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight(700), letterSpacing = (0).sp),
    titleMediumEmphasized = TextStyle(fontFamily = sans(16f), fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight(700), letterSpacing = (0.1).sp),
    titleSmallEmphasized = TextStyle(fontFamily = sans(14f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(700), letterSpacing = (0.1).sp),
    bodyLargeEmphasized = TextStyle(fontFamily = sans(16f), fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight(500), letterSpacing = (0).sp),
    bodyMediumEmphasized = TextStyle(fontFamily = sans(14f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(500), letterSpacing = (0.25).sp),
    bodySmallEmphasized = TextStyle(fontFamily = sans(12f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(500), letterSpacing = (0.4).sp),
    labelLargeEmphasized = TextStyle(fontFamily = sans(14f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(700), letterSpacing = (0.1).sp),
    labelMediumEmphasized = TextStyle(fontFamily = sans(12f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(700), letterSpacing = (0.5).sp),
    labelSmallEmphasized = TextStyle(fontFamily = sans(11f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight(700), letterSpacing = (0.5).sp),
)

val ExpressiveTypography = expressiveTypography()

/** Styles beyond the Material type scale: hero numerals and mono data labels. */
object ExpressiveTextStyles {
    val numeralLarge = TextStyle(fontFamily = variableFontFamily(R.font.google_sans_flex, 56f), fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight(700), letterSpacing = (-1.5).sp)
    val numeralMedium = TextStyle(fontFamily = variableFontFamily(R.font.google_sans_flex, 40f), fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight(700), letterSpacing = (-1).sp)
    val numeralSmall = TextStyle(fontFamily = variableFontFamily(R.font.google_sans_flex, 28f), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight(700), letterSpacing = (-0.5).sp)
    val eyebrow = TextStyle(fontFamily = GoogleSans, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight(700), letterSpacing = (1.2).sp)
    val dataMedium = TextStyle(fontFamily = GoogleSans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(500), letterSpacing = (0).sp)
    val dataSmall = TextStyle(fontFamily = GoogleSans, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight(500), letterSpacing = (0.3).sp)
}
