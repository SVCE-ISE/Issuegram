package com.svce.issuegram.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.svce.issuegram.R
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.ui.components.EmptyState
import com.svce.issuegram.ui.components.IssueCard
import com.svce.issuegram.ui.motion.rememberReducedMotion
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyIssuesScreen(modifier: Modifier = Modifier, issues: List<Issue> = emptyList(), onIssueClick: (String) -> Unit) {
    val myIssues = issues.filter { it.isMine }
    val listState = rememberLazyListState()
    val reducedMotion = rememberReducedMotion()

    if (myIssues.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(
                    start = Spacing.space4,
                    end = Spacing.space4,
                    top = Spacing.space8
                )
            ) {
                Text(
                    text = stringResource(R.string.my_issues_eyebrow).uppercase(Locale.getDefault()),
                    style = ExpressiveTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Spacing.space2))
                Text(
                    text = stringResource(R.string.my_issues_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            EmptyState(
                icon = Icons.Outlined.Assignment,
                title = stringResource(R.string.my_issues_empty_title),
                body = stringResource(R.string.my_issues_empty_body),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = Spacing.space4,
            end = Spacing.space4,
            top = Spacing.space8,
            bottom = Spacing.space6
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.space3)
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = stringResource(R.string.my_issues_eyebrow).uppercase(Locale.getDefault()),
                    style = ExpressiveTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Spacing.space2))
                Text(
                    text = stringResource(R.string.my_issues_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.space2))
                Text(
                    text = pluralStringResource(R.plurals.my_issues_count, myIssues.size, myIssues.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.space2))
            }
        }
        items(myIssues, key = { it.ticketId }) { issue ->
            IssueCard(
                issue = issue,
                onClick = { onIssueClick(issue.ticketId) },
                modifier = Modifier.animateItem(
                    placementSpec = if (reducedMotion) {
                        null
                    } else {
                        MaterialTheme.motionScheme.defaultSpatialSpec()
                    }
                )
            )
        }
    }
}
