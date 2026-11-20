package com.svce.issuegram.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.svce.issuegram.R
import com.svce.issuegram.data.IssueStore
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.ui.components.rememberPhotoBitmap
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportIssueSheet(
    onDismiss: () -> Unit,
    onPosted: (Issue) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<IssueCategory?>(null) }
    var location by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val photoBitmap = rememberPhotoBitmap(photoUri)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) photoUri = uri.toString() }

    val photoMissing = attempted && photoUri == null
    val titleMissing = attempted && title.isBlank()
    val categoryMissing = attempted && category == null
    val locationMissing = attempted && location.isBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = Spacing.space6, end = Spacing.space6, bottom = Spacing.space6),
        ) {
            Text(
                text = stringResource(R.string.report_eyebrow),
                style = ExpressiveTextStyles.eyebrow,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.space2))
            Text(
                text = stringResource(R.string.report_title),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(Spacing.space4))

            Surface(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (photoMissing) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                } else {
                    null
                },
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photoBitmap != null) {
                            Image(
                                bitmap = photoBitmap,
                                contentDescription = stringResource(R.string.report_photo_change),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AddAPhoto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(Spacing.space3))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (photoBitmap != null) {
                                    R.string.report_photo_change_title
                                } else {
                                    R.string.report_photo_add
                                },
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.report_photo_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (photoMissing) {
                Text(
                    text = stringResource(R.string.report_photo_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.space1, start = Spacing.space1),
                )
            }

            Spacer(Modifier.height(Spacing.space4))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(100) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.report_title_label)) },
                placeholder = { Text(stringResource(R.string.report_title_placeholder)) },
                singleLine = true,
                isError = titleMissing,
                supportingText = if (titleMissing) {
                    { Text(stringResource(R.string.report_title_error)) }
                } else {
                    null
                },
            )

            Spacer(Modifier.height(Spacing.space4))

            Text(
                text = stringResource(R.string.report_category_label),
                style = MaterialTheme.typography.labelLarge,
                color = if (categoryMissing) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(Spacing.space2))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                verticalArrangement = Arrangement.spacedBy(Spacing.space2),
            ) {
                IssueCategory.entries.forEach { option ->
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        shape = CircleShape,
                        label = {
                            Text(
                                text = stringResource(option.labelRes),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
            if (categoryMissing) {
                Text(
                    text = stringResource(R.string.report_category_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.space1),
                )
            }

            Spacer(Modifier.height(Spacing.space4))

            OutlinedTextField(
                value = location,
                onValueChange = { location = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.report_location_label)) },
                placeholder = { Text(stringResource(R.string.report_location_placeholder)) },
                singleLine = true,
                isError = locationMissing,
                supportingText = if (locationMissing) {
                    { Text(stringResource(R.string.report_location_error)) }
                } else {
                    null
                },
            )

            Spacer(Modifier.height(Spacing.space4))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.report_description_label)) },
                placeholder = { Text(stringResource(R.string.report_description_placeholder)) },
                minLines = 2,
                supportingText = {
                    Text(stringResource(R.string.report_description_counter, description.length))
                },
            )

            Spacer(Modifier.height(Spacing.space4))

            Button(
                onClick = {
                    attempted = true
                    val selectedCategory = category
                    if (photoUri != null && title.isNotBlank() && selectedCategory != null && location.isNotBlank()) {
                        onPosted(
                            IssueStore.add(
                                title = title,
                                description = description,
                                category = selectedCategory,
                                locationName = location,
                                photoUri = photoUri,
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.report_post),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
