package com.svce.issuegram.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.svce.issuegram.R
import com.svce.issuegram.data.IssueStore
import com.svce.issuegram.data.ProfileMock
import com.svce.issuegram.ui.components.Eyebrow
import com.svce.issuegram.ui.components.GroupedList
import com.svce.issuegram.ui.components.GroupedRow
import com.svce.issuegram.ui.navigation.FeedVersion
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    feedVersion: FeedVersion,
    onFeedVersionChange: (FeedVersion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }
    var emailUpdatesEnabled by rememberSaveable { mutableStateOf(false) }
    val myIssuesCount = IssueStore.issues.count { it.isMine }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.space4)
            .padding(top = Spacing.space8, bottom = Spacing.space6),
    ) {
        Eyebrow(stringResource(R.string.profile_eyebrow))
        Spacer(Modifier.height(Spacing.space2))
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(Spacing.space4))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.space5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ProfileMock.initials,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(Spacing.space4))
                Column {
                    Text(
                        text = ProfileMock.name,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.profile_role),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = ProfileMock.email,
                        style = ExpressiveTextStyles.dataSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.space6))

        GroupHeader(stringResource(R.string.profile_group_activity))
        Spacer(Modifier.height(Spacing.space2))
        GroupedList {
            ValueRow(
                index = 0,
                count = 3,
                label = stringResource(R.string.profile_my_issues),
                value = myIssuesCount.toString(),
            )
            ValueRow(
                index = 1,
                count = 3,
                label = stringResource(R.string.profile_following),
                value = ProfileMock.following.toString(),
            )
            ValueRow(
                index = 2,
                count = 3,
                label = stringResource(R.string.profile_likes_given),
                value = ProfileMock.likesGiven.toString(),
            )
        }

        Spacer(Modifier.height(Spacing.space6))

        GroupHeader(stringResource(R.string.profile_group_preferences))
        Spacer(Modifier.height(Spacing.space2))
        GroupedList {
            SwitchRow(
                index = 0,
                count = 2,
                label = stringResource(R.string.profile_notifications),
                supporting = stringResource(R.string.profile_notifications_support),
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it },
            )
            SwitchRow(
                index = 1,
                count = 2,
                label = stringResource(R.string.profile_email_updates),
                supporting = stringResource(R.string.profile_email_support),
                checked = emailUpdatesEnabled,
                onCheckedChange = { emailUpdatesEnabled = it },
            )
        }

        Spacer(Modifier.height(Spacing.space6))

        GroupHeader(stringResource(R.string.profile_group_feed))
        Spacer(Modifier.height(Spacing.space2))
        GroupedList {
            FeedVersion.entries.forEachIndexed { index, version ->
                VersionRow(
                    index = index,
                    count = FeedVersion.entries.size,
                    version = version,
                    selected = version == feedVersion,
                    onClick = { onFeedVersionChange(version) },
                )
            }
        }

        Spacer(Modifier.height(Spacing.space6))

        GroupHeader(stringResource(R.string.profile_group_about))
        Spacer(Modifier.height(Spacing.space2))
        GroupedList {
            ValueRow(
                index = 0,
                count = 1,
                label = stringResource(R.string.profile_version),
                value = "1.0",
            )
        }

        Spacer(Modifier.height(Spacing.space4))
        Text(
            text = stringResource(R.string.profile_signed_in_as, ProfileMock.email),
            style = ExpressiveTextStyles.dataSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.space1),
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.space1),
    )
}

@Composable
private fun ValueRow(
    index: Int,
    count: Int,
    label: String,
    value: String,
) {
    GroupedRow(index = index, count = count) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = ExpressiveTextStyles.dataMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VersionRow(
    index: Int,
    count: Int,
    version: FeedVersion,
    selected: Boolean,
    onClick: () -> Unit,
) {
    GroupedRow(
        index = index,
        count = count,
        onClick = onClick,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.semantics { this.selected = selected },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(version.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = stringResource(version.supportRes),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(Spacing.space3))
        RadioButton(selected = selected, onClick = null)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwitchRow(
    index: Int,
    count: Int,
    label: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    GroupedRow(index = index, count = count) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.space3))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            },
        )
    }
}
