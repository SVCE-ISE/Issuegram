package com.svce.issuegram.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.ui.theme.Expressive
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing

internal data class StatusTone(
    val container: Color,
    val content: Color,
    val icon: ImageVector,
)

@Composable
internal fun IssueStatus.badgeTone(): StatusTone {
    val scheme = MaterialTheme.colorScheme
    val extended = Expressive.extendedColors
    return when (this) {
        IssueStatus.Open -> StatusTone(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, Icons.Outlined.RadioButtonUnchecked)
        IssueStatus.Acknowledged -> StatusTone(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Outlined.Visibility)
        IssueStatus.Assigned -> StatusTone(scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Outlined.PersonAdd)
        IssueStatus.InProgress -> StatusTone(scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Outlined.Build)
        IssueStatus.Resolved -> StatusTone(extended.positiveContainer, extended.onPositiveContainer, Icons.Outlined.CheckCircle)
        IssueStatus.Reopened -> StatusTone(scheme.errorContainer, scheme.onErrorContainer, Icons.Outlined.Replay)
    }
}

/** Mono, labelled status pill — never colour alone. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusBadge(status: IssueStatus, modifier: Modifier = Modifier) {
    val tone = status.badgeTone()
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tone.container,
        contentColor = tone.content,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.space2,
                vertical = Spacing.space1,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = tone.icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = Spacing.space1)
                    .size(14.dp),
            )
            Text(
                text = stringResource(status.labelRes),
                style = ExpressiveTextStyles.dataSmall,
            )
        }
    }
}
