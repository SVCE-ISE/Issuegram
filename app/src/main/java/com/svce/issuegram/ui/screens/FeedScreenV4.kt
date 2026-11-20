package com.svce.issuegram.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.svce.issuegram.R
import com.svce.issuegram.data.IssueStore
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.ui.components.CategoryAvatar
import com.svce.issuegram.ui.components.Eyebrow
import com.svce.issuegram.ui.components.GroupedList
import com.svce.issuegram.ui.components.GroupedRow
import com.svce.issuegram.ui.components.StatusBadge
import com.svce.issuegram.ui.components.icon
import com.svce.issuegram.ui.components.relativeTime
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing
import kotlinx.coroutines.launch

private enum class FeedSection(val labelRes: Int, val statuses: Set<IssueStatus>) {
    NeedsAttention(
        R.string.feed_section_needs_attention,
        setOf(IssueStatus.Open, IssueStatus.Reopened),
    ),
    InProgress(
        R.string.feed_section_in_progress,
        setOf(IssueStatus.Acknowledged, IssueStatus.Assigned, IssueStatus.InProgress),
    ),
    Resolved(
        R.string.feed_section_resolved,
        setOf(IssueStatus.Resolved),
    ),
}

/**
 * Feed v4: the kit's tab-list pattern, applied literally —
 * search pill, category filter chips, eyebrow section headers, grouped rows,
 * token spacing, spring list placement, and designed empty states.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedScreenV4(
    onIssueClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<IssueCategory?>(null) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filtered = IssueStore.issues
        .filter { issue ->
            (selectedCategory == null || issue.category == selectedCategory) &&
                (query.isBlank() || issue.matches(query))
        }
        .sortedByDescending { it.createdAt }

    val sections = FeedSection.entries.map { section ->
        section to filtered.filter { it.status in section.statuses }
    }.filter { it.second.isNotEmpty() }

    val openCount = IssueStore.issues.count { it.status != IssueStatus.Resolved }
    val resolvedCount = IssueStore.issues.count { it.status == IssueStatus.Resolved }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.space4,
                end = Spacing.space4,
                top = Spacing.space8,
                bottom = Spacing.space16 + Spacing.space12,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            item(key = "header") {
                Column {
                    Eyebrow(stringResource(R.string.feed_eyebrow))
                    Spacer(Modifier.height(Spacing.space2))
                    Text(
                        text = stringResource(R.string.feed_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.space2))
                    Text(
                        text = stringResource(R.string.feed_pulse, openCount, resolvedCount),
                        style = ExpressiveTextStyles.dataSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.space4))
                }
            }

            item(key = "search") {
                SearchField(
                    query = query,
                    onQueryChange = { query = it.take(80) },
                )
            }

            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                ) {
                    CategoryChip(
                        label = stringResource(R.string.filter_all),
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                    )
                    IssueCategory.entries.forEach { category ->
                        CategoryChip(
                            label = stringResource(category.labelRes),
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = if (selectedCategory == category) null else category
                            },
                            category = category,
                        )
                    }
                }
            }

            if (sections.isEmpty()) {
                item(key = "empty") {
                    EmptyResults(
                        query = query,
                        onClearSearch = { query = "" },
                        onClearFilters = {
                            query = ""
                            selectedCategory = null
                        },
                    )
                }
            } else {
                sections.forEach { (section, sectionIssues) ->
                    item(key = "header-${section.name}") {
                        Text(
                            text = stringResource(section.labelRes)
                                .uppercase(java.util.Locale.getDefault()),
                            style = ExpressiveTextStyles.eyebrow,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = Spacing.space1,
                                top = Spacing.space2,
                            ),
                        )
                    }
                    item(key = "rows-${section.name}") {
                        GroupedList {
                            sectionIssues.forEachIndexed { index, issue ->
                                GroupedRow(
                                    index = index,
                                    count = sectionIssues.size,
                                    onClick = { onIssueClick(issue.ticketId) },
                                ) {
                                    FeedRow(issue)
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showReport = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.space4),
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(R.string.fab_report)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = Spacing.space4,
                    end = Spacing.space4,
                    bottom = Spacing.space16 + Spacing.space12,
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
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.report_posted_snackbar, issue.ticketId),
                    )
                }
            },
        )
    }
}

private fun Issue.matches(query: String): Boolean {
    val q = query.trim()
    return title.contains(q, ignoreCase = true) ||
        description?.contains(q, ignoreCase = true) == true ||
        ticketId.contains(q, ignoreCase = true) ||
        area.contains(q, ignoreCase = true) ||
        locationName.contains(q, ignoreCase = true)
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val searchLabel = stringResource(R.string.search_placeholder)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.space4,
                end = Spacing.space1,
                top = Spacing.space1,
                bottom = Spacing.space1,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.space3))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Spacing.space2),
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = searchLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = searchLabel },
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.width(Spacing.space4))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    category: IssueCategory? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (category != null) {
            {
                Icon(
                    imageVector = category.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else if (selected) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeedRow(issue: Issue) {
    Row(verticalAlignment = Alignment.Top) {
        CategoryAvatar(category = issue.category, size = 40.dp)
        Spacer(Modifier.width(Spacing.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = issue.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.space1))
            Text(
                text = issue.description ?: issue.locationName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.space1))
            Text(
                text = stringResource(
                    R.string.feed_row_meta,
                    issue.ticketId,
                    issue.area,
                    relativeTime(issue.createdAt),
                ),
                style = ExpressiveTextStyles.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.space3))
        Column(horizontalAlignment = Alignment.End) {
            StatusBadge(status = issue.status)
            Spacer(Modifier.height(Spacing.space1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(Spacing.space0_5))
                Text(
                    text = issue.likes.toString(),
                    style = ExpressiveTextStyles.dataSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyResults(
    query: String,
    onClearSearch: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.space8),
    ) {
        Text(
            text = if (query.isNotBlank()) {
                stringResource(R.string.no_results_for, query)
            } else {
                stringResource(R.string.no_results_title)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.space2))
        Text(
            text = stringResource(R.string.no_results_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = if (query.isNotBlank()) onClearSearch else onClearFilters,
        ) {
            Text(
                stringResource(
                    if (query.isNotBlank()) R.string.search_clear else R.string.no_results_clear,
                ),
            )
        }
    }
}
