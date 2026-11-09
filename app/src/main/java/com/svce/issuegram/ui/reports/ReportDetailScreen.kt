package com.svce.issuegram.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.ui.home.CivicIssueFeedPostCard

@Composable
fun ReportDetailScreen(issue: CivicIssueDto?, loading: Boolean, canResolve: Boolean, onToggleResolve: () -> Unit,
                       isConfirmed: Boolean = false, onToggleConfirm: (() -> Unit)? = null,
                       onBack: () -> Unit, onRefresh: () -> Unit, actions: @Composable (CivicIssueDto) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text("Back") }
                TextButton(onClick = onRefresh, enabled = !loading) { Text("Refresh report") }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (issue == null && !loading) item { Text("This report is no longer available, or could not be loaded. Refresh to try again.", Modifier.padding(16.dp)) }
        issue?.let { current -> item {
            CivicIssueFeedPostCard(current, onToggleResolve, canResolve, isConfirmed = isConfirmed, onToggleConfirm = onToggleConfirm, actions = { actions(current) })
        } }
    }
}
