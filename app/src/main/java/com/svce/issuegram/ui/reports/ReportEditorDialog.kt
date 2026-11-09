package com.svce.issuegram.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.data.model.EditIssueRequest
import com.svce.issuegram.domain.CivicActions
import com.svce.issuegram.domain.ReportRules
import com.svce.issuegram.ui.camera.LocationPickerDialog
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun ReportEditorDialog(issue: CivicIssueDto, busy: Boolean, errorMessage: String?, onClearError: () -> Unit, onDismiss: () -> Unit, onSave: (CivicIssueDto, EditIssueRequest) -> Unit) {
    // Keep the revision that was opened; a background refresh must not hide an edit conflict.
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    var baseJson by rememberSaveable(issue.id) { mutableStateOf(json.encodeToString(issue)) }
    val original = remember(baseJson) { json.decodeFromString<CivicIssueDto>(baseJson) }
    var caption by rememberSaveable(issue.id) { mutableStateOf(original.caption) }
    var category by rememberSaveable(issue.id) { mutableStateOf(original.category) }
    var location by rememberSaveable(issue.id) { mutableStateOf(original.locationName) }
    var detail by rememberSaveable(issue.id) { mutableStateOf(original.subLocation) }
    var latitude by rememberSaveable(issue.id) { mutableDoubleStateOf(original.latitude) }
    var longitude by rememberSaveable(issue.id) { mutableDoubleStateOf(original.longitude) }
    var selectingLocation by remember { mutableStateOf(false) }
    var confirmingDiscard by remember { mutableStateOf(false) }
    val edit = runCatching { CivicActions.edit(caption, category, location, detail, latitude, longitude) }.getOrNull()
    val changed = caption != original.caption || category != original.category || location != original.locationName ||
        detail != original.subLocation || latitude != original.latitude || longitude != original.longitude
    val newerVersion = issue.updatedAt != original.updatedAt
    fun close() { if (!busy) { if (changed) confirmingDiscard = true else onDismiss() } }

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = ::close, enabled = !busy) { Text("Cancel") }
                    TextButton(onClick = { edit?.let { onSave(original, it) } }, enabled = !busy && changed && edit != null && !newerVersion) { Text(if (busy) "Saving…" else "Save changes") }
                }
                Text("Edit report", style = MaterialTheme.typography.headlineSmall)
                Text(original.ticketCode.ifBlank { "Report #${original.id}" }, style = MaterialTheme.typography.labelLarge)
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (newerVersion) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A newer version is available. Your typed changes are kept.", style = MaterialTheme.typography.titleSmall)
                        Text("Latest saved report: ${issue.caption}\n${issue.category} · ${issue.locationName}\n${issue.status.replace('_', ' ')}")
                        TextButton(enabled = !busy, onClick = {
                            // Adopt newer untouched fields; retain the fields the citizen has corrected.
                            if (caption == original.caption) caption = issue.caption
                            if (category == original.category) category = issue.category
                            if (location == original.locationName) location = issue.locationName
                            if (detail == original.subLocation) detail = issue.subLocation
                            if (latitude == original.latitude && longitude == original.longitude) { latitude = issue.latitude; longitude = issue.longitude }
                            baseJson = json.encodeToString(issue); onClearError()
                        }) { Text("Use latest version and keep my changes") }
                    }
                }
                OutlinedTextField(value = caption, onValueChange = { caption = it.take(500) }, enabled = !busy, label = { Text("Describe the issue") }, supportingText = { Text("${caption.trim().length}/500 · at least 5 characters") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Category", style = MaterialTheme.typography.titleSmall)
                ReportRules.categories.forEach { option ->
                    FilterChip(selected = category == option, onClick = { category = option }, enabled = !busy, label = { Text(option) })
                }
                OutlinedTextField(value = location, onValueChange = { location = it.take(160) }, enabled = !busy, label = { Text("Location name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = detail, onValueChange = { detail = it.take(240) }, enabled = !busy, label = { Text("Nearby landmark or details (optional)") }, modifier = Modifier.fillMaxWidth())
                Text(String.format(Locale.US, "%.5f, %.5f", latitude, longitude))
                OutlinedButton(enabled = !busy, onClick = { selectingLocation = true }) { Text("Correct location on map") }
                Text("The original photo remains attached.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (selectingLocation) LocationPickerDialog(latitude, longitude, onDismiss = { selectingLocation = false }, onSelected = { lat, lon ->
        latitude = lat; longitude = lon
        location = String.format(Locale.US, "%.5f, %.5f", lat, lon)
        detail = "Location corrected on map"; selectingLocation = false
    })
    if (confirmingDiscard) AlertDialog(onDismissRequest = { confirmingDiscard = false }, title = { Text("Discard your changes?") },
        text = { Text("Your report will keep its saved details.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard changes") } },
        dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text("Keep editing") } })
}
