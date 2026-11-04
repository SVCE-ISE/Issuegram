@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.svce.issuegram.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

object CustomColors {
    var isDark = false
    var black = false
    var isNothingOs = false

    val topBarColors: TopAppBarColors
        @Composable get() =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surfaceContainerLow,
                scrolledContainerColor = colorScheme.surfaceContainerLow
            )

    val detailPaneTopBarColors: TopAppBarColors
        @Composable get() =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surfaceContainerLow,
                scrolledContainerColor = colorScheme.surfaceContainerLow
            )

    val cardContainerColor: androidx.compose.ui.graphics.Color
        @Composable get() = when {
            black -> colorScheme.surfaceContainerHigh
            isNothingOs && isDark -> androidx.compose.ui.graphics.Color(0xFF1B1D1F)
            isNothingOs && !isDark -> androidx.compose.ui.graphics.Color(0xFFFFFFFF)
            isDark -> colorScheme.surfaceContainerHigh
            else -> colorScheme.surfaceContainerLowest
        }

    val cardBorder: androidx.compose.foundation.BorderStroke
        @Composable get() = when {
            black -> androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.15f)
            )
            isNothingOs && isDark -> androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = androidx.compose.ui.graphics.Color(0xFF2B2D2F)
            )
            isNothingOs && !isDark -> androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = androidx.compose.ui.graphics.Color(0xFFD7D8D8)
            )
            isDark -> androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.2f)
            )
            else -> androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }

    val innerCardContainerColor: androidx.compose.ui.graphics.Color
        @Composable get() = when {
            black -> colorScheme.surfaceContainer
            isNothingOs && isDark -> androidx.compose.ui.graphics.Color(0xFF2B2D2F)
            isNothingOs && !isDark -> androidx.compose.ui.graphics.Color(0xFFE7E9E9)
            isDark -> colorScheme.surfaceContainerLow
            else -> colorScheme.surfaceContainerLow
        }

    val innerCardBorderColor: androidx.compose.ui.graphics.Color
        @Composable get() = when {
            black -> colorScheme.outlineVariant.copy(alpha = 0.2f)
            isNothingOs && isDark -> androidx.compose.ui.graphics.Color(0xFF33353A)
            isNothingOs && !isDark -> androidx.compose.ui.graphics.Color(0xFFC8C8C8)
            isDark -> colorScheme.outlineVariant.copy(alpha = 0.25f)
            else -> colorScheme.outlineVariant.copy(alpha = 0.45f)
        }

    val listItemColors: ListItemColors
        @Composable get() =
            ListItemDefaults.segmentedColors(
                containerColor = cardContainerColor,
                disabledContainerColor = cardContainerColor,
            )

    val switchColors: SwitchColors
        @Composable get() = SwitchDefaults.colors(
            checkedIconColor = colorScheme.primary,
        )
}
