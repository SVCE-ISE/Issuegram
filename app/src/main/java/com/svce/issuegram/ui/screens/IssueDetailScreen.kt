package com.svce.issuegram.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.svce.issuegram.R
import com.svce.issuegram.data.IssueStore
import com.svce.issuegram.data.TimelineEvent
import com.svce.issuegram.data.timeline
import com.svce.issuegram.ui.components.CategoryAvatar
import com.svce.issuegram.ui.components.Eyebrow
import com.svce.issuegram.ui.components.StatusBadge
import com.svce.issuegram.ui.components.absoluteDateTime
import com.svce.issuegram.ui.components.badgeTone
import com.svce.issuegram.ui.components.rememberPhotoBitmap
import com.svce.issuegram.ui.components.shareIssue
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IssueDetailScreen(ticketId: String, onBack: () -> Unit) {
    val issue = IssueStore.issues.firstOrNull { it.ticketId == ticketId }
    if (issue == null) {
        NotFound(onBack)
        return
    }

    val context = LocalContext.current
    var liked by rememberSaveable(ticketId) { mutableStateOf(false) }
    var following by rememberSaveable(ticketId) { mutableStateOf(false) }
    val likeCount = issue.likes + if (liked) 1 else 0
    val photo = rememberPhotoBitmap(issue.photoUri)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.space4, vertical = Spacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                ) {
                    IconToggleButton(checked = liked, onCheckedChange = { liked = it }) {
                        Icon(
                            imageVector = if (liked) Icons.Outlined.ThumbUp else Icons.Outlined.ThumbUpOffAlt,
                            contentDescription = stringResource(R.string.like_issue, issue.ticketId),
                            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = likeCount.toString(),
                        style = ExpressiveTextStyles.dataSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { following = !following }) {
                        Text(
                            text = stringResource(
                                if (following) R.string.detail_following else R.string.detail_follow,
                            ),
                        )
                    }
                    Button(onClick = { shareIssue(context, issue.ticketId, issue.title) }) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.space2))
                        Text(stringResource(R.string.detail_share))
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.space4),
        ) {
            Spacer(Modifier.height(Spacing.space1))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = stringResource(R.string.issue_photo_desc, issue.ticketId),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CategoryAvatar(category = issue.category, size = 112.dp)
                }
            }

            Spacer(Modifier.height(Spacing.space4))

            Text(
                text = issue.title,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.space2))
            Text(
                text = stringResource(
                    R.string.issue_meta,
                    issue.ticketId,
                    stringResource(issue.category.labelRes),
                ),
                style = ExpressiveTextStyles.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.space1))
            Text(
                text = absoluteDateTime(issue.createdAt),
                style = ExpressiveTextStyles.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.space3))
            StatusBadge(status = issue.status)

            Spacer(Modifier.height(Spacing.space6))
            Eyebrow(stringResource(R.string.detail_status_eyebrow))
            Spacer(Modifier.height(Spacing.space3))
            Timeline(events = issue.timeline())

            Spacer(Modifier.height(Spacing.space6))
            Eyebrow(stringResource(R.string.detail_location_eyebrow))
            Spacer(Modifier.height(Spacing.space2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Spacing.space2))
                Column {
                    Text(
                        text = issue.locationName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = issue.area,
                        style = ExpressiveTextStyles.dataSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            issue.description?.let { description ->
                Spacer(Modifier.height(Spacing.space6))
                Eyebrow(stringResource(R.string.detail_about_eyebrow))
                Spacer(Modifier.height(Spacing.space2))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(Spacing.space6))
            Text(
                text = stringResource(R.string.detail_reported_by, issue.authorName),
                style = ExpressiveTextStyles.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.space6))
        }
    }
}

@Composable
private fun Timeline(events: List<TimelineEvent>) {
    Column {
        events.forEachIndexed { index, event ->
            val tone = event.status.badgeTone()
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(tone.container),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tone.icon,
                            contentDescription = null,
                            tint = tone.content,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (index != events.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.space3))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = Spacing.space1, bottom = Spacing.space4),
                ) {
                    Text(
                        text = stringResource(event.status.labelRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = absoluteDateTime(event.at),
                        style = ExpressiveTextStyles.dataSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotFound(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = Spacing.space6),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.detail_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
