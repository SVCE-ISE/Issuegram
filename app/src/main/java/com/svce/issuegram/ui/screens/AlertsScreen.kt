package com.svce.issuegram.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.svce.issuegram.R
import com.svce.issuegram.data.AlertKind
import com.svce.issuegram.data.AppNotification
import com.svce.issuegram.ui.components.EmptyState
import com.svce.issuegram.ui.components.Eyebrow
import com.svce.issuegram.ui.components.GroupedList
import com.svce.issuegram.ui.components.GroupedRow
import com.svce.issuegram.ui.components.badgeTone
import com.svce.issuegram.ui.components.relativeTime
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val BUCKET_TODAY = 0
private const val BUCKET_YESTERDAY = 1
private const val BUCKET_EARLIER = 2

private fun bucketOf(at: Instant): Int {
    val date = at.atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, LocalDate.now())
    return when {
        days <= 0 -> BUCKET_TODAY
        days == 1L -> BUCKET_YESTERDAY
        else -> BUCKET_EARLIER
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlertsScreen(
    modifier: Modifier = Modifier,
    bottomBarPadding: Dp = 0.dp,
    onBack: (() -> Unit)? = null,
    onIssueClick: (String) -> Unit,
    viewModel: AlertsViewModel? = null
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }

    val uiState by viewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }
    var localAlerts by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    val alerts = remember(uiState, localAlerts) {
        uiState?.notifications?.map { com.svce.issuegram.data.mapper.NotificationMapper.toData(it) } ?: localAlerts
    }
    val groups = listOf(
        BUCKET_TODAY to alerts.filter { bucketOf(it.at) == BUCKET_TODAY },
        BUCKET_YESTERDAY to alerts.filter { bucketOf(it.at) == BUCKET_YESTERDAY },
        BUCKET_EARLIER to alerts.filter { bucketOf(it.at) == BUCKET_EARLIER }
    )

    if (alerts.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.NotificationsNone,
            title = stringResource(R.string.alerts_empty_title),
            body = stringResource(R.string.alerts_empty_body),
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = Spacing.space4,
            end = Spacing.space4,
            top = Spacing.space5,
            bottom = bottomBarPadding + Spacing.space6
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.space3)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Eyebrow(stringResource(R.string.alerts_eyebrow))
                    Spacer(Modifier.height(Spacing.space2))
                    Text(
                        text = stringResource(R.string.alerts_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (alerts.any { it.unread }) {
                    TextButton(
                        onClick = {
                            if (viewModel != null) {
                                viewModel.markAllAsRead()
                            } else {
                                localAlerts = localAlerts.map { it.copy(unread = false) }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.alerts_mark_all_read))
                    }
                }
            }
        }

        groups.forEach { (bucket, items) ->
            if (items.isNotEmpty()) {
                item(key = "group-$bucket") {
                    Text(
                        text = stringResource(
                            when (bucket) {
                                BUCKET_TODAY -> R.string.alerts_group_today
                                BUCKET_YESTERDAY -> R.string.alerts_group_yesterday
                                else -> R.string.alerts_group_earlier
                            }
                        ).uppercase(java.util.Locale.getDefault()),
                        style = ExpressiveTextStyles.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = Spacing.space1,
                            top = Spacing.space2
                        )
                    )
                }
                item(key = "rows-$bucket") {
                    GroupedList {
                        items.forEachIndexed { index, notification ->
                            GroupedRow(
                                index = index,
                                count = items.size,
                                onClick = { onIssueClick(notification.ticketId) }
                            ) {
                                AlertRow(notification)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlertRow(notification: AppNotification) {
    val tone = notification.status?.badgeTone()
    val container = tone?.container ?: MaterialTheme.colorScheme.secondaryContainer
    val content = tone?.content ?: MaterialTheme.colorScheme.onSecondaryContainer
    val unreadLabel = stringResource(R.string.alerts_unread)
    val typeLabel = when (notification.kind) {
        AlertKind.Status -> stringResource(notification.status?.labelRes ?: R.string.alerts_reply_label)
        AlertKind.Reply -> stringResource(R.string.alerts_reply_label)
    }

    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tone?.icon ?: Icons.Outlined.Forum,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(Spacing.space3))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.issueTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (notification.unread) {
                    Spacer(Modifier.width(Spacing.space2))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .semantics { contentDescription = unreadLabel }
                    )
                }
            }
            Spacer(Modifier.height(Spacing.space1))
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.space1))
            Text(
                text = stringResource(
                    R.string.alert_meta,
                    typeLabel,
                    notification.ticketId,
                    relativeTime(notification.at)
                ),
                style = ExpressiveTextStyles.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
