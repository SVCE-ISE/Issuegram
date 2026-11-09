@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.svce.issuegram.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.svce.issuegram.R
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.domain.displayTime
import com.svce.issuegram.ui.components.IssuegramMediaImage
import com.svce.issuegram.ui.theme.IssuegramShapeDefaults
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeFeedScreen(
    modifier: Modifier = Modifier,
    issues: List<CivicIssueDto> = emptyList(),
    onToggleResolve: (CivicIssueDto) -> Unit = {},
    canResolve: (CivicIssueDto) -> Boolean = { false },
    confirmedIssueIds: Set<Long> = emptySet(),
    onToggleConfirm: (CivicIssueDto) -> Unit = {},
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onOpenIssue: (CivicIssueDto) -> Unit = {},
    onCreate: () -> Unit = {},
    actions: @Composable (CivicIssueDto) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var status by rememberSaveable { mutableStateOf("All") }
    val displayIssues = remember(issues, query, category, status) {
        issues.filter { com.svce.issuegram.domain.CivicActions.matches(it, query, category, status) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.OutlinedTextField(value = query, onValueChange = { query = it.take(100) },
                    label = { Text("Search loaded reports") }, placeholder = { Text("Ticket, issue or location") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Active", "Resolved").forEach { option ->
                        androidx.compose.material3.FilterChip(selected = status == option, onClick = { status = option }, label = { Text(option) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("All") + com.svce.issuegram.domain.ReportRules.categories).forEach { option ->
                        androidx.compose.material3.FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
                    }
                }
            }
        }
        items(
            items = displayIssues,
            key = { it.id }
        ) { issue ->
            CivicIssueFeedPostCard(
                issue = issue,
                onToggleResolve = { onToggleResolve(issue) },
                canResolve = canResolve(issue),
                isConfirmed = issue.id in confirmedIssueIds,
                onToggleConfirm = { onToggleConfirm(issue) },
                onOpen = { onOpenIssue(issue) },
                actions = { actions(issue) }
            )
        }
        if (displayIssues.isEmpty()) item {
            Text(if (issues.isEmpty()) "No reports yet. Be the first to report an issue nearby." else "No loaded reports match your search or filters.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (issues.isEmpty()) androidx.compose.material3.Button(onClick = onCreate) { Text("Report an issue") }
            else androidx.compose.material3.TextButton(onClick = { query = ""; category = "All"; status = "All" }) { Text("Clear search and filters") }
        }
        if (hasMore) item {
            androidx.compose.material3.TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("Load more reports") }
        }
    }
}

@Composable
fun CivicIssueFeedPostCard(
    issue: CivicIssueDto,
    onToggleResolve: () -> Unit,
    canResolve: Boolean,
    isConfirmed: Boolean = false,
    onToggleConfirm: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    var isCaptionExpanded by rememberSaveable(issue.id) { mutableStateOf(false) }
    val isResolved = issue.isResolved

    val mediaList = issue.mediaUrls.ifEmpty { listOf("") }
    var viewingPhoto by rememberSaveable(issue.id) { mutableStateOf<Int?>(null) }
    viewingPhoto?.let { index ->
        if (issue.mediaUrls.isNotEmpty()) com.svce.issuegram.ui.reports.EvidenceViewer(issue.mediaUrls, index, onDismiss = { viewingPhoto = null })
    }
    val pagerState = rememberPagerState(pageCount = { mediaList.size })

    val arrowRotation by animateFloatAsState(
        targetValue = if (isCaptionExpanded) 180f else 0f,
        label = "arrowRotation"
    )

    val arrowShape = if (isCaptionExpanded) {
        MaterialTheme.shapes.small
    } else {
        MaterialTheme.shapes.extraLargeIncreased
    }

    val arrowContainerColor = if (isCaptionExpanded) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val arrowContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    // Preserve the designed card proportions without decoding a sample photo.
    val feedAspectRatio = 736f / 975f

    val shapes = listOf(
        IssuegramShapeDefaults.topListItemShape,
        IssuegramShapeDefaults.middleListItemShape,
        IssuegramShapeDefaults.middleListItemShape,
        IssuegramShapeDefaults.bottomListItemShape,
    )

    val milestones = issue.milestones.sortedBy { it.stepOrder }

    val latDir = if (issue.latitude >= 0) "N" else "S"
    val lonDir = if (issue.longitude >= 0) "E" else "W"
    val formattedCoords = String.format(
        Locale.US,
        "%.4f° %s, %.4f° %s",
        abs(issue.latitude),
        latDir,
        abs(issue.longitude),
        lonDir
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Item 1: Top card with profile picture and username
        Card(
            shape = shapes[0],
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = onOpen != null) { onOpen?.invoke() }
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IssuegramMediaImage(
                    mediaRef = issue.authorAvatarUrl,
                    contentDescription = "Profile picture",
                    contentScale = ContentScale.Crop,
                    fallbackDrawableRes = R.drawable.avatar_placeholder,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = issue.authorUsername,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = issue.ticketCode.ifBlank { "#${issue.id}" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                actions()
            }
        }

        // Item 2: Multi-image feed carousel with dynamic aspect ratio and page indicator
        Card(
            shape = shapes[1],
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(feedAspectRatio)
        ) {
            Box(modifier = Modifier.fillMaxSize().clickable(enabled = issue.mediaUrls.isNotEmpty(), onClickLabel = "Open report photos") { viewingPhoto = pagerState.currentPage }) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    IssuegramMediaImage(
                        mediaRef = mediaList[page],
                        contentDescription = "Feed image ${page + 1}",
                        contentScale = ContentScale.Crop,
                        fallbackDrawableRes = R.drawable.image_placeholder,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Page indicator badge (e.g. 1/2, 2/2)
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${mediaList.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (mediaList.size > 1) {
                    // Dots indicator
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(mediaList.size) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 6.dp else 4.dp)
                                    .background(
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Item 3: Caption card with animated tap-to-expand, subtle arrow icon, and full-width expandable location surface
        Card(
            onClick = { isCaptionExpanded = !isCaptionExpanded },
            shape = shapes[2],
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = issue.caption,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isCaptionExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(24.dp)
                            .background(
                                color = arrowContainerColor,
                                shape = arrowShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_down),
                            contentDescription = if (isCaptionExpanded) "Collapse caption" else "Expand caption",
                            tint = arrowContentColor,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(arrowRotation)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Full-width Location Surface badge that expands with GPS coordinates
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .animateContentSize()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_map_pin),
                                contentDescription = "Location",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = issue.locationName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCaptionExpanded && issue.isGpsVerified) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "GPS VERIFIED",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Detailed coordinates revealed when caption is expanded
                        if (isCaptionExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formattedCoords,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = issue.subLocation,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Item 4: Minimalist Airy Civic Issue Status Timeline
        Card(
            shape = shapes[3],
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Header: Status title + subtle live status pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status Timeline",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Subtle Live Status Pill
                    val statusContainerColor by animateColorAsState(
                        targetValue = if (isResolved) Color(0xFF1B5E20).copy(alpha = 0.85f)
                        else Color(0xFFE65100).copy(alpha = 0.85f),
                        label = "statusBg"
                    )
                    val statusTextColor by animateColorAsState(
                        targetValue = if (isResolved) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                        label = "statusText"
                    )

                    Surface(
                        shape = CircleShape,
                        color = statusContainerColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusTextColor, CircleShape)
                            )
                            Text(
                                text = issue.status.replace("_", " "),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    fontSize = 10.sp
                                ),
                                color = statusTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (milestones.isEmpty()) Text("Department updates will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Minimalist Airy Timeline Flow
                milestones.forEachIndexed { index, milestone ->
                    val isLast = index == milestones.size - 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left Node & Delicate Connector Line
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(18.dp)
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val nodeColor = if (milestone.isCompleted) {
                                if (index == 2) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }

                            if (milestone.isCompleted) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(nodeColor, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = BorderStroke(1.5.dp, nodeColor),
                                        modifier = Modifier.fillMaxSize()
                                    ) {}
                                }
                            }

                            if (!isLast) {
                                Box(
                                    modifier = Modifier
                                        .width(1.5.dp)
                                        .height(38.dp)
                                        .background(
                                            if (milestone.isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Event content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = if (!isLast) 14.dp else 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = milestone.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (milestone.isCompleted) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (milestone.isCompleted) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = displayTime(milestone.timestampLabel),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = if (milestone.isCompleted) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = milestone.actor,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (milestone.isCompleted) 0.8f else 0.5f
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (issue.upvotesCount == 1) "1 citizen confirmed this issue"
                        else "${issue.upvotesCount} citizens confirmed this issue",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onToggleConfirm != null) {
                        androidx.compose.material3.FilterChip(
                            selected = isConfirmed,
                            onClick = onToggleConfirm,
                            label = {
                                Text(
                                    text = if (isConfirmed) "Confirmed (${issue.upvotesCount})"
                                    else "I also see this (${issue.upvotesCount})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        )
                    }
                }

                if (canResolve) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Only verified department staff receive status controls.
                Surface(
                    onClick = onToggleResolve,
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isResolved) R.drawable.ic_clock else R.drawable.ic_check
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isResolved) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isResolved) "Reopen Issue" else "Mark Issue as Resolved",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.2.sp
                            ),
                            color = if (isResolved) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                }
            }
        }
    }
}
