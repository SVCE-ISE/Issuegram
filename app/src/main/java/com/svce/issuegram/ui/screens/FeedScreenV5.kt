@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.svce.issuegram.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.svce.issuegram.R
import com.svce.issuegram.data.IssueStore
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.ui.components.CategoryAvatar
import com.svce.issuegram.ui.components.badgeTone
import com.svce.issuegram.ui.components.rememberPhotoBitmap
import com.svce.issuegram.ui.components.relativeTime
import com.svce.issuegram.ui.motion.ExpressiveHaptic
import com.svce.issuegram.ui.motion.rememberBreathing
import com.svce.issuegram.ui.motion.rememberHaptics
import com.svce.issuegram.ui.theme.Expressive
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Feed v5 — the ward, as an editorial board.
 *
 * An oversized Fraunces masthead and live counters replace the usual title + chips stack,
 * the category row is a typographic index rather than a row of pills, the most-supported
 * report gets an inverted spotlight, and everything else lives on a two-column masonry board
 * with serif headlines. Mono type is reserved for the data: tickets, times, counts.
 */
@Composable
fun FeedScreenV5(
    onIssueClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<IssueCategory?>(null) }
    var showReport by rememberSaveable { mutableStateOf(false) }

    val gridState = rememberLazyStaggeredGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = rememberHaptics()
    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    val allIssues = IssueStore.issues
    val filtered = allIssues
        .filter { issue ->
            (selectedCategory == null || issue.category == selectedCategory) &&
                (query.isBlank() || issue.matchesV5(query))
        }
    val spotlight = filtered
        .sortedWith(
            compareBy<Issue> { it.status == IssueStatus.Resolved }
                .thenByDescending { it.likes }
                .thenByDescending { it.createdAt },
        )
        .firstOrNull()
    val board = filtered
        .sortedByDescending { it.createdAt }
        .filterNot { it.ticketId == spotlight?.ticketId }

    val openCount = allIssues.count { it.status != IssueStatus.Resolved }
    val fixedCount = allIssues.count { it.status == IssueStatus.Resolved }
    val voices = numberFormat.format(allIssues.sumOf { it.likes })
    val categoryCounts = IssueCategory.entries
        .mapNotNull { category ->
            val count = allIssues.count { it.category == category }
            if (count > 0) category to count else null
        }
        .toMap()

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(
                start = Spacing.space5,
                end = Spacing.space5,
                top = Spacing.space6,
                bottom = 120.dp,
            ),
            verticalItemSpacing = Spacing.space3,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            item(key = "masthead", span = StaggeredGridItemSpan.FullLine) {
                FeedMasthead(
                    query = query,
                    searchOpen = searchOpen,
                    onToggleSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) {
                            query = ""
                            keyboard?.hide()
                        }
                    },
                    onQueryChange = { query = it.take(80) },
                    selectedCategory = selectedCategory,
                    onSelectCategory = { category ->
                        haptic(ExpressiveHaptic.Tap)
                        selectedCategory = category
                    },
                    openCount = openCount,
                    fixedCount = fixedCount,
                    voices = voices,
                    categoryCounts = categoryCounts,
                    totalCount = allIssues.size,
                )
            }

            if (filtered.isEmpty()) {
                item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                    EmptyBoard(
                        onClear = {
                            query = ""
                            selectedCategory = null
                            searchOpen = false
                            keyboard?.hide()
                        },
                    )
                }
            } else {
                spotlight?.let { issue ->
                    item(key = "spotlight-${issue.ticketId}", span = StaggeredGridItemSpan.FullLine) {
                        SpotlightCard(
                            issue = issue,
                            onClick = { onIssueClick(issue.ticketId) },
                        )
                    }
                }

                if (board.isNotEmpty()) {
                    item(key = "board-header", span = StaggeredGridItemSpan.FullLine) {
                        BoardHeader(
                            count = board.size,
                            isSearch = query.isNotBlank(),
                        )
                    }
                    items(board, key = { "board-${it.ticketId}" }) { issue ->
                        BoardCard(
                            issue = issue,
                            onClick = { onIssueClick(issue.ticketId) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "board-end", span = StaggeredGridItemSpan.FullLine) {
                        EndMark()
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showReport = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.space4),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.fab_report),
                modifier = Modifier.size(26.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = Spacing.space4,
                    end = Spacing.space4,
                    bottom = 88.dp,
                ),
        )
    }

    if (showReport) {
        ReportIssueSheet(
            onDismiss = { showReport = false },
            onPosted = { issue ->
                showReport = false
                query = ""
                selectedCategory = null
                searchOpen = false
                scope.launch {
                    gridState.animateScrollToItem(0)
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.report_posted_snackbar, issue.ticketId),
                    )
                }
            },
        )
    }
}

private fun Issue.matchesV5(query: String): Boolean {
    val q = query.trim()
    return title.contains(q, ignoreCase = true) ||
        description?.contains(q, ignoreCase = true) == true ||
        ticketId.contains(q, ignoreCase = true) ||
        area.contains(q, ignoreCase = true) ||
        locationName.contains(q, ignoreCase = true)
}

@Composable
private fun FeedMasthead(
    query: String,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    selectedCategory: IssueCategory?,
    onSelectCategory: (IssueCategory?) -> Unit,
    openCount: Int,
    fixedCount: Int,
    voices: String,
    categoryCounts: Map<IssueCategory, Int>,
    totalCount: Int,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(locale) {
        DateTimeFormatter.ofPattern("EEE d MMM", locale).format(LocalDate.now())
    }
    val line1 = stringResource(R.string.feed_v5_title_line1)
    val line2 = stringResource(R.string.feed_v5_title_line2)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val title = buildAnnotatedString {
        withStyle(SpanStyle(color = onSurface)) { append(line1) }
        append("\n")
        withStyle(SpanStyle(color = primary)) { append(line2) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name).uppercase(locale),
                style = ExpressiveTextStyles.eyebrow,
                color = primary,
            )
            Spacer(Modifier.width(Spacing.space3))
            LivePill()
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = onToggleSearch,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = stringResource(
                            if (searchOpen) R.string.feed_v5_close_search else R.string.search_placeholder,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SearchLine(
                query = query,
                onQueryChange = onQueryChange,
            )
        }

        AnimatedVisibility(
            visible = !searchOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.space4))

                Text(
                    text = stringResource(R.string.feed_v5_dateline, date),
                    style = ExpressiveTextStyles.dataSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Spacing.space3))

                Text(
                    text = title,
                    style = MaterialTheme.typography.wardMasthead,
                    color = onSurface,
                )

                Spacer(Modifier.height(Spacing.space3))

                Text(
                    text = stringResource(R.string.feed_v5_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Spacing.space5))

                StatStrip(
                    openCount = openCount,
                    fixedCount = fixedCount,
                    voices = voices,
                )

                Spacer(Modifier.height(Spacing.space5))

                CategoryIndex(
                    selectedCategory = selectedCategory,
                    onSelectCategory = onSelectCategory,
                    totalCount = totalCount,
                    categoryCounts = categoryCounts,
                )
            }
        }
    }
}

@Composable
private fun LivePill() {
    val pulse = rememberBreathing(periodMillis = 2800)
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.65f * pulse),
                    ),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.feed_v5_live).uppercase(locale),
                style = ExpressiveTextStyles.eyebrow.copy(fontSize = 9.sp, letterSpacing = 0.9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchLine(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val placeholder = stringResource(R.string.search_placeholder)
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.padding(top = Spacing.space3)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = onSurface),
                    cursorBrush = SolidColor(primary),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = placeholder },
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = primary.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun StatStrip(
    openCount: Int,
    fixedCount: Int,
    voices: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat(
                value = openCount.toString(),
                label = stringResource(R.string.feed_v5_stat_open),
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            Stat(
                value = fixedCount.toString(),
                label = stringResource(R.string.feed_v5_stat_fixed),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            )
            StatDivider()
            Stat(
                value = voices,
                label = stringResource(R.string.feed_v5_stat_voices),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            )
        }
        RatioBar(openCount = openCount, fixedCount = fixedCount)
    }
}

@Composable
private fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.statNumeral,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label.uppercase(locale),
            style = ExpressiveTextStyles.eyebrow.copy(fontSize = 9.sp, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    )
}

@Composable
private fun RatioBar(openCount: Int, fixedCount: Int) {
    if (openCount + fixedCount == 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (openCount > 0) {
            Box(
                modifier = Modifier
                    .weight(openCount.toFloat())
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        if (fixedCount > 0) {
            Box(
                modifier = Modifier
                    .weight(fixedCount.toFloat())
                    .fillMaxHeight()
                    .background(Expressive.extendedColors.positive),
            )
        }
    }
}

@Composable
private fun CategoryIndex(
    selectedCategory: IssueCategory?,
    onSelectCategory: (IssueCategory?) -> Unit,
    totalCount: Int,
    categoryCounts: Map<IssueCategory, Int>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CategoryTab(
            label = stringResource(R.string.feed_v5_index_all),
            count = totalCount,
            selected = selectedCategory == null,
            onClick = { onSelectCategory(null) },
        )
        IssueCategory.entries.forEach { category ->
            val count = categoryCounts[category] ?: 0
            if (count > 0) {
                CategoryTab(
                    label = stringResource(category.labelRes),
                    count = count,
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                )
            }
        }
    }
}

@Composable
private fun CategoryTab(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) primary else onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = count.toString().padStart(2, '0'),
                style = ExpressiveTextStyles.dataSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 0.4.sp,
                ),
                color = if (selected) {
                    primary.copy(alpha = 0.7f)
                } else {
                    onSurfaceVariant.copy(alpha = 0.55f)
                },
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = count.toString()
                },
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(if (selected) primary else Color.Transparent),
        )
    }
}

@Composable
private fun SpotlightCard(
    issue: Issue,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tone = issue.status.badgeTone()
    val locale = LocalConfiguration.current.locales[0]

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
    ) {
        Box {
            Box(modifier = Modifier.matchParentSize()) {
                CategoryAvatar(
                    category = issue.category,
                    size = 190.dp,
                    containerColor = scheme.inversePrimary.copy(alpha = 0.10f),
                    contentColor = Color.Transparent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 54.dp, y = (-58).dp),
                )
            }

            Column(modifier = Modifier.padding(Spacing.space5)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(
                        category = issue.category,
                        size = 34.dp,
                        containerColor = scheme.inverseOnSurface.copy(alpha = 0.12f),
                        contentColor = scheme.inversePrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.feed_most_supported)
                                .uppercase(locale),
                            style = ExpressiveTextStyles.eyebrow.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                            ),
                            color = scheme.inversePrimary,
                        )
                        Text(
                            text = "${issue.ticketId} · ${
                                stringResource(issue.category.labelRes).uppercase(locale)
                            }",
                            style = ExpressiveTextStyles.dataSmall.copy(fontSize = 10.sp),
                            color = scheme.inverseOnSurface.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(Spacing.space2))
                    StatusDotLabel(
                        status = issue.status,
                        dotColor = tone.container,
                        textColor = scheme.inverseOnSurface.copy(alpha = 0.88f),
                    )
                }

                Spacer(Modifier.height(Spacing.space5))

                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.featureTitle,
                    color = scheme.inverseOnSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!issue.description.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.space2))
                    Text(
                        text = issue.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.inverseOnSurface.copy(alpha = 0.76f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(Spacing.space5))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        tint = scheme.inverseOnSurface.copy(alpha = 0.64f),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = issue.likes.toString(),
                        style = ExpressiveTextStyles.dataSmall,
                        color = scheme.inverseOnSurface.copy(alpha = 0.84f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(10.dp)
                            .background(scheme.inverseOnSurface.copy(alpha = 0.20f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = scheme.inverseOnSurface.copy(alpha = 0.64f),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = issue.locationName,
                        style = ExpressiveTextStyles.dataSmall,
                        color = scheme.inverseOnSurface.copy(alpha = 0.74f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDotLabel(
    status: IssueStatus,
    dotColor: Color,
    textColor: Color,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = stringResource(status.labelRes).uppercase(locale),
            style = ExpressiveTextStyles.dataSmall.copy(
                fontSize = 10.sp,
                lineHeight = 13.sp,
                letterSpacing = 0.6.sp,
            ),
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoardHeader(
    count: Int,
    isSearch: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(
                    if (isSearch) R.string.feed_v5_search_results else R.string.feed_v5_board_title,
                ),
                style = MaterialTheme.typography.boardSection,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(R.plurals.feed_v5_more_reports, count, count),
                style = ExpressiveTextStyles.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.space2))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        )
    }
}

@Composable
private fun BoardCard(
    issue: Issue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = rememberPhotoBitmap(issue.photoUri)
    val cover = categoryCover(issue.category)
    val tone = issue.status.badgeTone()
    val time = relativeTime(issue.createdAt)
    val locale = LocalConfiguration.current.locales[0]
    val category = stringResource(issue.category.labelRes).uppercase(locale)
    val scheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = scheme.surfaceContainerLow,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(cover.container),
            ) {
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = stringResource(R.string.issue_photo_desc, issue.ticketId),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CategoryAvatar(
                        category = issue.category,
                        size = 112.dp,
                        containerColor = cover.content.copy(alpha = 0.10f),
                        contentColor = Color.Transparent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 24.dp, y = (-18).dp),
                    )
                    CategoryAvatar(
                        category = issue.category,
                        size = 48.dp,
                        containerColor = cover.content.copy(alpha = 0.15f),
                        contentColor = cover.content,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(Spacing.space3),
                    )
                }
                CoverChip(
                    text = issue.ticketId,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                )
                CoverChip(
                    text = time,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                )
            }

            Column(
                modifier = Modifier.padding(
                    horizontal = Spacing.space3,
                    vertical = 10.dp,
                ),
            ) {
                Text(
                    text = category,
                    style = ExpressiveTextStyles.dataSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        letterSpacing = 0.8.sp,
                    ),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.space1))
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.boardCardTitle,
                    color = scheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDotLabel(
                        status = issue.status,
                        dotColor = tone.content,
                        textColor = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = issue.likes.toString(),
                        style = ExpressiveTextStyles.dataSmall.copy(fontSize = 10.sp),
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.52f),
        contentColor = Color.White,
    ) {
        Text(
            text = text,
            style = ExpressiveTextStyles.dataSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
                letterSpacing = 0.3.sp,
            ),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

private data class CoverTone(
    val container: Color,
    val content: Color,
)

@Composable
private fun categoryCover(category: IssueCategory): CoverTone {
    val scheme = MaterialTheme.colorScheme
    return when (category) {
        IssueCategory.Pothole,
        IssueCategory.Road,
        -> CoverTone(scheme.primaryContainer, scheme.onPrimaryContainer)

        IssueCategory.Streetlight,
        IssueCategory.Safety,
        -> CoverTone(scheme.tertiaryContainer, scheme.onTertiaryContainer)

        IssueCategory.Water,
        IssueCategory.Drainage,
        -> CoverTone(scheme.secondaryContainer, scheme.onSecondaryContainer)

        IssueCategory.Garbage,
        IssueCategory.Other,
        -> CoverTone(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyBoard(onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.space8, bottom = Spacing.space6),
    ) {
        Text(
            text = stringResource(R.string.feed_v5_empty_title),
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 28.sp,
                lineHeight = 32.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.space2))
        Text(
            text = stringResource(R.string.feed_v5_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.space1))
        TextButton(
            onClick = onClear,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = Spacing.space2),
        ) {
            Text(stringResource(R.string.feed_v5_clear_filters))
        }
    }
}

@Composable
private fun EndMark() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Text(
            text = stringResource(R.string.feed_v5_end),
            style = ExpressiveTextStyles.dataSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = Spacing.space3),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

private val Typography.wardMasthead: TextStyle
    get() = displayMedium.copy(
        fontSize = 42.sp,
        lineHeight = 47.sp,
        letterSpacing = (-0.7).sp,
    )

private val Typography.featureTitle: TextStyle
    get() = displaySmall.copy(
        fontSize = 30.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.3).sp,
    )

private val Typography.boardSection: TextStyle
    get() = displaySmall.copy(
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.2).sp,
    )

private val Typography.boardCardTitle: TextStyle
    get() = displaySmall.copy(
        fontSize = 19.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight(600),
        letterSpacing = (-0.1).sp,
    )

private val Typography.statNumeral: TextStyle
    get() = displaySmall.copy(
        fontSize = 30.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight(600),
        letterSpacing = (-0.4).sp,
    )
