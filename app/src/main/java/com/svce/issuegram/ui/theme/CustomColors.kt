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

    val topBarColors: TopAppBarColors
        @Composable get() =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.background,
                scrolledContainerColor = colorScheme.background
            )

    val detailPaneTopBarColors: TopAppBarColors
        @Composable get() =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.background,
                scrolledContainerColor = colorScheme.background
            )

    val cardContainerColor: androidx.compose.ui.graphics.Color
        @Composable get() = when {
            black -> colorScheme.surfaceContainerHigh
            isDark -> colorScheme.surfaceContainerHigh
            else -> colorScheme.surfaceContainerLowest
        }

    val cardBorder: androidx.compose.foundation.BorderStroke
        @Composable get() = when {
            black -> androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.15f)
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
            isDark -> colorScheme.surfaceContainerLow
            else -> colorScheme.surfaceContainerLow
        }

    val innerCardBorderColor: androidx.compose.ui.graphics.Color
        @Composable get() = when {
            black -> colorScheme.outlineVariant.copy(alpha = 0.2f)
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
