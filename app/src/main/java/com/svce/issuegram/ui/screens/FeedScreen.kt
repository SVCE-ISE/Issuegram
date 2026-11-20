package com.svce.issuegram.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.svce.issuegram.R
import com.svce.issuegram.data.IssueStore
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.ui.components.EmptyState
import com.svce.issuegram.ui.components.IssueCard
import com.svce.issuegram.ui.components.icon
import com.svce.issuegram.ui.motion.ExpressiveHaptic
import com.svce.issuegram.ui.motion.rememberBreathing
import com.svce.issuegram.ui.motion.rememberHaptics
import com.svce.issuegram.ui.motion.rememberReducedMotion
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    onIssueClick: (String) -> Unit,
) {
    var selectedCategory by rememberSaveable { mutableStateOf<IssueCategory?>(null) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = rememberHaptics()
    val reducedMotion = rememberReducedMotion()

    val issues = selectedCategory
        ?.let { category -> IssueStore.issues.filter { it.category == category } }
        ?: IssueStore.issues

    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                    ) {
                        val pulse = rememberBreathing(periodMillis = 2800)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .graphicsLayer {
                                    scaleX = if (reducedMotion) 1f else pulse
                                    scaleY = if (reducedMotion) 1f else pulse
                                }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = stringResource(R.string.feed_eyebrow).uppercase(Locale.getDefault()),
                            style = ExpressiveTextStyles.eyebrow,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(Spacing.space2))
                    Text(
                        text = stringResource(R.string.feed_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.space1))
                    val openCount = remember { IssueStore.issues.count { it.status != IssueStatus.Resolved } }
                    val resolvedCount = remember { IssueStore.issues.count { it.status == IssueStatus.Resolved } }
                    Text(
                        text = stringResource(R.string.feed_pulse, openCount, resolvedCount),
                        style = ExpressiveTextStyles.dataSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.space4))
                }
            }

            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = Spacing.space1),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                ) {
                    CategoryChip(
                        label = stringResource(R.string.filter_all),
                        selected = selectedCategory == null,
                        onClick = {
                            haptic(ExpressiveHaptic.Tick)
                            selectedCategory = null
                        },
                    )
                    IssueCategory.entries.forEach { category ->
                        CategoryChip(
                            label = stringResource(category.labelRes),
                            selected = selectedCategory == category,
                            onClick = {
                                haptic(ExpressiveHaptic.Tick)
                                selectedCategory = if (selectedCategory == category) null else category
                            },
                            leadingIcon = true,
                            category = category,
                        )
                    }
                }
            }

            if (issues.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.space8),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.no_results_title),
                            body = stringResource(R.string.no_results_body),
                        )
                        Spacer(Modifier.height(Spacing.space2))
                        TextButton(onClick = {
                            haptic(ExpressiveHaptic.Tap)
                            selectedCategory = null
                        }) {
                            Text(stringResource(R.string.no_results_clear))
                        }
                    }
                }
            } else {
                items(issues, key = { it.ticketId }) { issue ->
                    IssueCard(
                        issue = issue,
                        onClick = { onIssueClick(issue.ticketId) },
                        modifier = Modifier.animateItem(
                            placementSpec = if (reducedMotion) {
                                null
                            } else {
                                MaterialTheme.motionScheme.defaultSpatialSpec()
                            },
                        ),
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            expanded = fabExpanded,
            onClick = {
                haptic(ExpressiveHaptic.Tap)
                showReport = true
            },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: Boolean = false,
    category: IssueCategory? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (leadingIcon && category != null) {
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
