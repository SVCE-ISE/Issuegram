package com.svce.issuegram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.svce.issuegram.data.model.IssueCategory

internal fun IssueCategory.icon(): ImageVector = when (this) {
    IssueCategory.Pothole -> Icons.Outlined.Warning
    IssueCategory.Streetlight -> Icons.Outlined.Lightbulb
    IssueCategory.Garbage -> Icons.Outlined.Delete
    IssueCategory.Water -> Icons.Outlined.WaterDrop
    IssueCategory.Drainage -> Icons.Outlined.Waves
    IssueCategory.Road -> Icons.Outlined.DirectionsCar
    IssueCategory.Safety -> Icons.Outlined.Security
    IssueCategory.Other -> Icons.Outlined.MoreHoriz
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IssueCategory.shape() = when (this) {
    IssueCategory.Pothole -> MaterialShapes.Cookie9Sided
    IssueCategory.Streetlight -> MaterialShapes.SoftBurst
    IssueCategory.Garbage -> MaterialShapes.Clover4Leaf
    IssueCategory.Water -> MaterialShapes.Sunny
    IssueCategory.Drainage -> MaterialShapes.ClamShell
    IssueCategory.Road -> MaterialShapes.Diamond
    IssueCategory.Safety -> MaterialShapes.Pentagon
    IssueCategory.Other -> MaterialShapes.Circle
}.toShape()

/** One library shape per category, used as the category avatar everywhere. */
@Composable
fun CategoryAvatar(
    category: IssueCategory,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(category.shape())
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category.icon(),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
