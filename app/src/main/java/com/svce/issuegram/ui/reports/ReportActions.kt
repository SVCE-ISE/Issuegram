package com.svce.issuegram.ui.reports

import androidx.core.net.toUri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.svce.issuegram.R
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.data.model.EditIssueRequest
import com.svce.issuegram.domain.CivicActions

@Composable
fun ReportActions(
    issue: CivicIssueDto,
    userId: String,
    busy: Boolean,
    onOpen: () -> Unit,
    onContact: () -> Unit,
    onEdit: (CivicIssueDto, EditIssueRequest, () -> Unit) -> Unit,
    onDelete: (CivicIssueDto, () -> Unit) -> Unit,
    onFlag: (CivicIssueDto, String, () -> Unit) -> Unit,
    onDeleted: () -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var dialog by rememberSaveable(issue.id) { mutableStateOf<String?>(null) }
    var reason by rememberSaveable(issue.id) { mutableStateOf(CivicActions.flagReasons.first()) }
    val owned = userId.isNotBlank() && issue.authorId == userId
    fun launch(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "No app is available for this action.", Toast.LENGTH_SHORT).show() }
    }
    Box {
        IconButton(onClick = { expanded = true }, enabled = !busy) {
            Icon(painterResource(R.drawable.ic_more_vertical), contentDescription = "Report actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("View report") }, onClick = { expanded = false; onOpen() })
            DropdownMenuItem(text = { Text("Share report") }, onClick = {
                expanded = false
                launch(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, CivicActions.shareText(issue)) }, "Share report"))
            })
            DropdownMenuItem(text = { Text("Copy ticket number") }, onClick = {
                expanded = false
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Issuegram ticket", issue.ticketCode.ifBlank { "#${issue.id}" }))
            })
            DropdownMenuItem(text = { Text("Open location in Maps") }, onClick = {
                expanded = false
                launch(Intent(Intent.ACTION_VIEW, "https://www.google.com/maps/search/?api=1&query=${issue.latitude},${issue.longitude}".toUri()))
            })
            DropdownMenuItem(text = { Text("Contact department") }, onClick = { expanded = false; onContact() })
            HorizontalDivider()
            if (owned) {
                DropdownMenuItem(text = { Text("Edit report") }, onClick = { expanded = false; onClearError(); dialog = "edit" })
                DropdownMenuItem(text = { Text("Delete report", color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; onClearError(); dialog = "delete" })
            } else {
                DropdownMenuItem(text = { Text("Report a problem with this post") }, onClick = { expanded = false; onClearError(); dialog = "flag" })
            }
        }
    }
    if (dialog == "edit" && owned) ReportEditorDialog(issue, busy, errorMessage, onClearError, onDismiss = { dialog = null }, onSave = { original, edit -> onEdit(original, edit) { dialog = null } })
    if (dialog == "delete" && owned) AlertDialog(
        onDismissRequest = { if (!busy) dialog = null },
        title = { Text("Delete this report?") },
        text = { Column { Text("${issue.ticketCode.ifBlank { "Report #${issue.id}" }} will be removed from the public feed, map and your profile. This cannot be undone.")
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { onDelete(issue) { dialog = null; onDeleted() } }) { Text(if (busy) "Deleting…" else "Delete report") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { dialog = null }) { Text("Cancel") } },
    )
    if (dialog == "flag" && !owned) AlertDialog(
        onDismissRequest = { if (!busy) dialog = null },
        title = { Text("Report this post") },
        text = { Column {
            Text("Choose a reason for the moderation team.")
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            CivicActions.flagReasons.forEach { option ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = reason == option, onClick = { reason = option }, enabled = !busy)
                    TextButton(enabled = !busy, onClick = { reason = option }) { Text(option) }
                }
            }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { onFlag(issue, reason) { dialog = null } }) { Text(if (busy) "Sending…" else "Send for review") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { dialog = null }) { Text("Cancel") } },
    )
}
