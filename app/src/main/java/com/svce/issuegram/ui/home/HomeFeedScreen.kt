@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.svce.issuegram.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.svce.issuegram.R
import com.svce.issuegram.ui.theme.IssuegramShapeDefaults
import com.svce.issuegram.ui.theme.IssuegramTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class IssueMilestone(
    val title: String,
    val actor: String,
    val timestamp: String,
    val isCompleted: Boolean,
)

@Composable
fun HomeFeedScreen(
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var isCaptionExpanded by remember { mutableStateOf(false) }
    var isResolved by remember { mutableStateOf(false) }
    var resolvedTimestamp by remember { mutableStateOf("Nov 6 · 3:56 PM") }

    val feedImages = listOf(
        R.drawable.feed_1,
        R.drawable.feed1_img2
    )
    val pagerState = rememberPagerState(pageCount = { feedImages.size })

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

    val feedPainter = painterResource(R.drawable.feed_1)
    val feedAspectRatio = if (feedPainter.intrinsicSize.height > 0f) {
        feedPainter.intrinsicSize.width / feedPainter.intrinsicSize.height
    } else {
        736f / 975f
    }

    val shapes = listOf(
        IssuegramShapeDefaults.topListItemShape,
        IssuegramShapeDefaults.middleListItemShape,
        IssuegramShapeDefaults.middleListItemShape,
        IssuegramShapeDefaults.bottomListItemShape,
    )

    val milestones = listOf(
        IssueMilestone(
            title = "Issue Reported",
            actor = "Logged by @zenzer0s",
            timestamp = "Nov 6 · 10:15 AM",
            isCompleted = true
        ),
        IssueMilestone(
            title = "Technician Assigned",
            actor = "Estate Audio Support • Team B",
            timestamp = "Nov 6 · 1:40 PM",
            isCompleted = true
        ),
        IssueMilestone(
            title = if (isResolved) "Issue Resolved" else "Resolution & Verification",
            actor = if (isResolved) "Replaced cabling verified on stage" else "Pending stage audio sound-check",
            timestamp = if (isResolved) resolvedTimestamp else "Est. Today · 5:00 PM",
            isCompleted = isResolved
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 12.dp),
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
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.profile_pic),
                    contentDescription = "Profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "zenzer0s",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "#IG-104",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
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
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Image(
                        painter = painterResource(feedImages[page]),
                        contentDescription = "Feed image ${page + 1}",
                        contentScale = ContentScale.Crop,
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
                        text = "${pagerState.currentPage + 1}/${feedImages.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Dots indicator
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(feedImages.size) { index ->
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
                        text = "The main auditorium audio system experienced intermittent feedback issues during the orientation today. Tech staff has scheduled an audio cable replacement before the upcoming fest.",
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
                                text = "Main Auditorium • Stage Left",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCaptionExpanded) {
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

                        // Detailed campus coordinates revealed when caption is expanded
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
                                    text = "12.9864° N, 80.0031° E",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "SVCE Campus • Block A, Fl 1",
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

        // Item 4: Minimalist Airy Campus Issue Status Timeline
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
                                text = if (isResolved) "RESOLVED" else "IN PROGRESS",
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
                                    text = milestone.timestamp,
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

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Subtle Secondary Action Pill Button
                Surface(
                    onClick = {
                        val now = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date())
                        resolvedTimestamp = now
                        isResolved = !isResolved
                    },
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

@Preview(showBackground = true)
@Composable
private fun HomeFeedPreview() {
    IssuegramTheme {
        HomeFeedScreen()
    }
}
