@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.svce.issuegram.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.svce.issuegram.R

val TYPOGRAPHY = Typography()

data class AppFonts(
    val topBarTitle: FontFamily,
    val annotatedString: FontFamily
)

@Composable
fun typography(isNothingOs: Boolean = false): Typography = remember(isNothingOs) {
    if (isNothingOs) {
        val ndot = FontFamily(Font(R.font.ndot, FontWeight.Normal))
        val ntype = FontFamily(Font(R.font.ntype82, FontWeight.Normal))

        return@remember Typography(
            displayLarge = TYPOGRAPHY.displayLarge.copy(fontFamily = ndot),
            displayMedium = TYPOGRAPHY.displayMedium.copy(fontFamily = ndot),
            displaySmall = TYPOGRAPHY.displaySmall.copy(fontFamily = ndot),
            headlineLarge = TYPOGRAPHY.headlineLarge.copy(fontFamily = ndot),
            headlineMedium = TYPOGRAPHY.headlineMedium.copy(fontFamily = ndot),
            headlineSmall = TYPOGRAPHY.headlineSmall.copy(fontFamily = ndot),
            titleLarge = TYPOGRAPHY.titleLarge.copy(fontFamily = ntype, fontWeight = FontWeight.Bold),
            titleMedium = TYPOGRAPHY.titleMedium.copy(fontFamily = ntype, fontWeight = FontWeight.SemiBold),
            titleSmall = TYPOGRAPHY.titleSmall.copy(fontFamily = ntype, fontWeight = FontWeight.Medium),
            bodyLarge = TYPOGRAPHY.bodyLarge.copy(fontFamily = ntype),
            bodyMedium = TYPOGRAPHY.bodyMedium.copy(fontFamily = ntype),
            bodySmall = TYPOGRAPHY.bodySmall.copy(fontFamily = ntype),
            labelLarge = TYPOGRAPHY.labelLarge.copy(fontFamily = ntype, fontWeight = FontWeight.SemiBold),
            labelMedium = TYPOGRAPHY.labelMedium.copy(fontFamily = ntype, fontWeight = FontWeight.Medium),
            labelSmall = TYPOGRAPHY.labelSmall.copy(fontFamily = ntype, fontWeight = FontWeight.Medium)
        )
    }

    val googleFlex400 = FontFamily(
        Font(
            R.font.google_sans_flex,
            FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        )
    )

    val googleFlex600 = FontFamily(
        Font(
            R.font.google_sans_flex,
            FontWeight.Bold,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(600),
                FontVariation.Setting("ROND", 100f)
            )
        )
    )

    Typography(
            displayLarge = TYPOGRAPHY.displayLarge.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            displayMedium = TYPOGRAPHY.displayMedium.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            displaySmall = TYPOGRAPHY.displaySmall.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            headlineLarge = TYPOGRAPHY.headlineLarge.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            headlineMedium = TYPOGRAPHY.headlineMedium.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            headlineSmall = TYPOGRAPHY.headlineSmall.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            titleLarge = TYPOGRAPHY.titleLarge.copy(
                fontFamily = googleFlex400,
                fontFeatureSettings = "ss02, dlig"
            ),
            titleMedium = TYPOGRAPHY.titleMedium.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            titleSmall = TYPOGRAPHY.titleSmall.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            bodyLarge = TYPOGRAPHY.bodyLarge.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            bodyMedium = TYPOGRAPHY.bodyMedium.copy(
                fontFamily = googleFlex400,
                fontFeatureSettings = "ss02, dlig"
            ),
            bodySmall = TYPOGRAPHY.bodySmall.copy(
                fontFamily = googleFlex400,
                fontFeatureSettings = "ss02, dlig"
            ),
            labelLarge = TYPOGRAPHY.labelLarge.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            labelMedium = TYPOGRAPHY.labelMedium.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            labelSmall = TYPOGRAPHY.labelSmall.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            )
        )
}

@Composable
fun getAppFonts(isNothingOs: Boolean = false): AppFonts = remember(isNothingOs) {
    if (isNothingOs) {
        val ndot = FontFamily(Font(R.font.ndot, FontWeight.Normal))
        val ntype = FontFamily(Font(R.font.ntype82, FontWeight.Normal))
        return@remember AppFonts(
            topBarTitle = ndot,
            annotatedString = ntype
        )
    }

    val robotoFlexTopBar = FontFamily(
        Font(
            R.font.google_sans_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(650),
                FontVariation.width(105f),
                FontVariation.Setting("ROND", 50f)
            )
        )
    )

    val annotatedStringFontFamily = FontFamily(
        Font(
            R.font.google_sans_flex,
            FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        ),
        Font(
            R.font.google_sans_flex,
            FontWeight.Bold,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(600),
                FontVariation.Setting("ROND", 100f)
            )
        )
    )

    AppFonts(
        topBarTitle = robotoFlexTopBar,
        annotatedString = annotatedStringFontFamily
    )
}

val LocalAppFonts = staticCompositionLocalOf<AppFonts> { error("AppFonts not provided") }
