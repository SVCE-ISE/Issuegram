package com.svce.issuegram.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.svce.issuegram.R
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.data.model.IssueFlagDto
import com.svce.issuegram.data.model.UserProfileDto
import com.svce.issuegram.ui.components.IssuegramMediaImage
import com.svce.issuegram.ui.theme.IssuegramShapeDefaults
import kotlinx.coroutines.launch

private const val UID_PREVIEW_PREFIX_LEN = 8
private const val UID_PREVIEW_SUFFIX_LEN = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel? = null,
    currentUser: UserProfileDto? = null,
    allIssues: List<CivicIssueDto> = emptyList(),
    onUpdateAvatar: (Uri) -> Unit = {},
    onUpdateName: (String, () -> Unit) -> Unit = { _, _ -> },
    onRemoveAvatar: () -> Unit = {},
    profileBusy: Boolean = false,
    onOpenIssue: (CivicIssueDto) -> Unit = {},
    onCreate: () -> Unit = {},
    errorMessage: String? = null,
    isModerator: Boolean = false,
    moderationFlags: List<IssueFlagDto> = emptyList(),
    onReviewFlag: (Long, String) -> Unit = { _, _ -> },
    onSignOut: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val vmUser by viewModel?.currentUser?.collectAsState() ?: remember { mutableStateOf(null) }
    val vmIssues by viewModel?.userPostedIssues?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val vmAllIssues by viewModel?.allIssues?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val vmBusy by viewModel?.isBusy?.collectAsState() ?: remember { mutableStateOf(false) }
    val vmError by viewModel?.errorMessage?.collectAsState() ?: remember { mutableStateOf(null) }

    val effectiveBusy = profileBusy || vmBusy
    val effectiveErrorMessage = errorMessage ?: vmError

    val activeUser = currentUser ?: vmUser?.let { domainUser ->
        UserProfileDto(
            id = domainUser.id,
            username = domainUser.username,
            displayName = domainUser.displayName,
            avatarUrl = domainUser.avatarUrl,
            email = domainUser.email,
            bio = domainUser.bio,
            isAdmin = domainUser.isAdmin
        )
    } ?: UserProfileDto(
        id = "user_me",
        username = "citizen",
        displayName = "Citizen Reporter",
        avatarUrl = null,
        email = "",
        bio = "Civic Citizen Reporter",
        isAdmin = false
    )
    val isAdminEffective = activeUser.isAdmin
    val username = activeUser.username
    val avatarRef = activeUser.avatarUrl
    val userEmail = activeUser.email.orEmpty()

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel?.updateAvatar(uri)
            onUpdateAvatar(uri)
        }
    }

    val userPostedIssues = remember(allIssues, activeUser.id, vmIssues) {
        if (allIssues.isNotEmpty()) {
            allIssues.filter { it.authorId == activeUser.id }
        } else {
            vmIssues.mapIndexed { idx, it ->
                com.svce.issuegram.data.mapper.IssueMapper.toCivicDto(
                    issue = it,
                    id = idx.toLong() + 1,
                    authorId = activeUser.id,
                    authorUsername = activeUser.username
                )
            }
        }
    }
    var issueToEdit by remember { mutableStateOf<CivicIssueDto?>(null) }
    var issueToDelete by remember { mutableStateOf<CivicIssueDto?>(null) }
    val deleteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (issueToDelete != null) {
        val target = issueToDelete!!
        ModalBottomSheet(
            onDismissRequest = { if (!effectiveBusy) issueToDelete = null },
            sheetState = deleteSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Delete Post?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ticket: ${target.ticketCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Are you sure you want to permanently delete \"${target.title}\"? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { issueToDelete = null },
                        enabled = !effectiveBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val code = target.ticketCode
                            issueToDelete = null
                            viewModel?.deleteIssue(code)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !effectiveBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (issueToEdit != null) {
        val target = issueToEdit!!
        var editTitle by remember(target) { mutableStateOf(target.title) }
        var editCaption by remember(target) { mutableStateOf(target.caption) }
        var editLocation by remember(target) { mutableStateOf(target.locationName) }

        val canSave = editTitle.trim().length in 3..100 && editCaption.trim().length in 5..500

        ModalBottomSheet(
            onDismissRequest = { if (!effectiveBusy) issueToEdit = null },
            sheetState = editSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Edit Post",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ticket: ${target.ticketCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it.take(100) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editCaption,
                    onValueChange = { editCaption = it.take(500) },
                    label = { Text("Description") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editLocation,
                    onValueChange = { editLocation = it.take(160) },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { issueToEdit = null },
                        enabled = !effectiveBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val ticketCode = target.ticketCode
                            val newT = editTitle.trim()
                            val newC = editCaption.trim()
                            val newL = editLocation.trim()
                            issueToEdit = null
                            viewModel?.editIssue(ticketCode, newT, newC, newL)
                        },
                        enabled = canSave && !effectiveBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    var editingName by remember { mutableStateOf(false) }
    var editedName by remember(activeUser.displayName) { mutableStateOf(activeUser.displayName) }
    if (editingName) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!effectiveBusy) editingName = false },
            title = { Text("Your name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it.take(36) },
                        enabled = !effectiveBusy,
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val nameLength = editedName.trim().length
                    if (nameLength !in 2..36) {
                        Text(
                            text = "Name must be between 2 and 36 characters",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    effectiveErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val sanitizedName = editedName.trim()
                        if (viewModel != null) {
                            viewModel.updateDisplayName(sanitizedName) { editingName = false }
                        } else {
                            onUpdateName(sanitizedName) { editingName = false }
                        }
                    },
                    enabled = !effectiveBusy && editedName.trim().length in 2..36
                ) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { editingName = false },
                    enabled = !effectiveBusy
                ) { Text("Cancel") }
            }
        )
    }

    val reportedCount = userPostedIssues.size
    val solvedCount = userPostedIssues.count { it.isResolved }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Profile Picture in the Centre (Tap to change profile picture)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(2.5.dp, Color(0xFF30363D)),
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .clickable(enabled = !effectiveBusy) {
                    avatarPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
        ) {
            IssuegramMediaImage(
                mediaRef = avatarRef,
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                fallbackDrawableRes = R.drawable.avatar_placeholder,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Real Name and Email below Profile Picture
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable(enabled = !effectiveBusy) {
                editedName = activeUser.displayName
                editingName = true
            }
        ) {
            Text(
                text = activeUser.displayName.takeIf { it.isNotBlank() } ?: username,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 0.3.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (isAdminEffective) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Admin Verified",
                    tint = Color(0xFF1D9BF0),
                    modifier = Modifier.size(18.dp)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1D9BF0).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF1D9BF0).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "ADMIN",
                        color = Color(0xFF1D9BF0),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (userEmail.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = userEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "@$username",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val userId = activeUser.id.takeIf { it.isNotBlank() && it != "user_me" }
        if (!userId.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            val clipboardManager = LocalClipboardManager.current
            val context = LocalContext.current
            val previewUid = if (userId.length > UID_PREVIEW_PREFIX_LEN + UID_PREVIEW_SUFFIX_LEN) {
                "${userId.take(UID_PREVIEW_PREFIX_LEN)}...${userId.takeLast(UID_PREVIEW_SUFFIX_LEN)}"
            } else {
                userId
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        clipboardManager.setText(AnnotatedString(userId))
                        Toast
                            .makeText(context, "User ID copied to clipboard", Toast.LENGTH_SHORT)
                            .show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "UID: $previewUid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy UID",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (effectiveBusy) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        if (isAdminEffective) {
            // ADMIN VIEW: 4 status data buttons showing Open, Acknowledged, Assigned, Resolved
            val openCount = remember(allIssues, vmAllIssues) {
                if (allIssues.isNotEmpty()) {
                    allIssues.count { it.status.equals("OPEN", ignoreCase = true) }
                } else {
                    vmAllIssues.count { it.status == com.svce.issuegram.data.model.IssueStatus.Open }
                }
            }
            val acknowledgedCount = remember(allIssues, vmAllIssues) {
                if (allIssues.isNotEmpty()) {
                    allIssues.count { it.status.equals("ACKNOWLEDGED", ignoreCase = true) }
                } else {
                    vmAllIssues.count { it.status == com.svce.issuegram.data.model.IssueStatus.Acknowledged }
                }
            }
            val assignedCount = remember(allIssues, vmAllIssues) {
                if (allIssues.isNotEmpty()) {
                    allIssues.count {
                        it.status.equals("ASSIGNED", ignoreCase = true) ||
                            it.status.equals("IN_PROGRESS", ignoreCase = true)
                    }
                } else {
                    vmAllIssues.count {
                        it.status == com.svce.issuegram.data.model.IssueStatus.Assigned ||
                            it.status == com.svce.issuegram.data.model.IssueStatus.InProgress
                    }
                }
            }
            val resolvedCount = remember(allIssues, vmAllIssues) {
                if (allIssues.isNotEmpty()) {
                    allIssues.count {
                        it.status.equals("RESOLVED", ignoreCase = true) || it.isResolved
                    }
                } else {
                    vmAllIssues.count { it.status == com.svce.issuegram.data.model.IssueStatus.Resolved }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Open & Acknowledged
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminStatusMetricCard(
                        modifier = Modifier.weight(1f),
                        count = openCount,
                        label = stringResource(R.string.status_open),
                        icon = Icons.Outlined.ErrorOutline,
                        iconTint = Color(0xFFEF5350),
                        iconBg = Color(0xFFEF5350).copy(alpha = 0.15f)
                    )
                    AdminStatusMetricCard(
                        modifier = Modifier.weight(1f),
                        count = acknowledgedCount,
                        label = stringResource(R.string.status_acknowledged),
                        icon = Icons.Outlined.Schedule,
                        iconTint = Color(0xFFFFA726),
                        iconBg = Color(0xFFFFA726).copy(alpha = 0.15f)
                    )
                }

                // Row 2: Assigned & Resolved
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminStatusMetricCard(
                        modifier = Modifier.weight(1f),
                        count = assignedCount,
                        label = stringResource(R.string.status_assigned),
                        icon = Icons.Outlined.AssignmentTurnedIn,
                        iconTint = Color(0xFF42A5F5),
                        iconBg = Color(0xFF42A5F5).copy(alpha = 0.15f)
                    )
                    AdminStatusMetricCard(
                        modifier = Modifier.weight(1f),
                        count = resolvedCount,
                        label = stringResource(R.string.status_resolved),
                        icon = Icons.Outlined.CheckCircle,
                        iconTint = Color(0xFF66BB6A),
                        iconBg = Color(0xFF66BB6A).copy(alpha = 0.15f)
                    )
                }
            }
        } else {
            // NORMAL USER VIEW: Reported & Solved metrics + Posted Issues List
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Item 1 (Left): Reported Issues
                Card(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        bottomStart = 16.dp,
                        topEnd = 4.dp,
                        bottomEnd = 4.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF0E3D4F), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_report_alert),
                                contentDescription = "Reported",
                                tint = Color(0xFF67B7F7),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$reportedCount",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.profile_reported_count),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Item 2 (Right): Solved Issues
                Card(
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        bottomStart = 4.dp,
                        topEnd = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF35254A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = "Solved",
                                tint = Color(0xFFB39DDB),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$solvedCount",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.profile_solved_count),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. List Items Section: All Posted Issues by User (Segmented Compact List)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.profile_posted_issues_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "${userPostedIssues.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (userPostedIssues.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.profile_swipe_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Segmented List Item Stack
            if (userPostedIssues.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.profile_empty_posted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    userPostedIssues.forEachIndexed { index, issue ->
                        key(issue.ticketCode) {
                            val itemShape = when {
                                userPostedIssues.size == 1 -> RoundedCornerShape(16.dp)
                                index == 0 -> IssuegramShapeDefaults.topListItemShape
                                index == userPostedIssues.size - 1 -> IssuegramShapeDefaults.bottomListItemShape
                                else -> IssuegramShapeDefaults.middleListItemShape
                            }

                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            issueToEdit = issue
                                            false
                                        }
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            issueToDelete = issue
                                            false
                                        }
                                        SwipeToDismissBoxValue.Settled -> false
                                    }
                                }
                            )

                            val timestampLabel = com.svce.issuegram.domain.displayTime(issue.createdAt)
                            val thumbRef = issue.mediaUrls.firstOrNull()

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection
                                    val color = when (direction) {
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                        SwipeToDismissBoxValue.Settled -> Color.Transparent
                                    }
                                    val alignment = when (direction) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        SwipeToDismissBoxValue.Settled -> Alignment.Center
                                    }
                                    val tint = when (direction) {
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
                                        SwipeToDismissBoxValue.Settled -> Color.Transparent
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(itemShape)
                                            .background(color)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = alignment
                                    ) {
                                        when (direction) {
                                            SwipeToDismissBoxValue.StartToEnd -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Edit,
                                                        contentDescription = "Edit",
                                                        tint = tint,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Edit",
                                                        color = tint,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
                                                }
                                            }
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Delete",
                                                        color = tint,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Delete",
                                                        tint = tint,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            else -> Unit
                                        }
                                    }
                                },
                                modifier = Modifier.clip(itemShape)
                            ) {
                                Card(
                                    onClick = { onOpenIssue(issue) },
                                    shape = itemShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Issue Image Thumbnail
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.size(46.dp)
                                        ) {
                                            IssuegramMediaImage(
                                                mediaRef = thumbRef,
                                                contentDescription = issue.title,
                                                contentScale = ContentScale.Crop,
                                                fallbackDrawableRes = R.drawable.image_placeholder,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        // Middle Info: ID + Title, Location & Timestamp
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = issue.ticketCode,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = issue.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_map_pin),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = listOf(issue.locationName, timestampLabel).filter {
                                                        it.isNotBlank()
                                                    }.joinToString(" • "),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        // Status Badge on Right
                                        Surface(
                                            shape = CircleShape,
                                            color = if (issue.isResolved) {
                                                Color(
                                                    0xFF4CAF50
                                                ).copy(alpha = 0.15f)
                                            } else {
                                                Color(0xFFFFA726).copy(alpha = 0.15f)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(
                                                            color = if (issue.isResolved) Color(0xFF4CAF50) else Color(0xFFFFA726),
                                                            shape = CircleShape
                                                        )
                                                )
                                                Text(
                                                    text = issue.status.replace('_', ' '),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    ),
                                                    color = if (issue.isResolved) Color(0xFF4CAF50) else Color(0xFFFFA726)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isModerator) {
            val pendingFlags = remember(moderationFlags) {
                moderationFlags.filter { it.moderationStatus == "PENDING" }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Moderation Queue",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "${pendingFlags.size} pending",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (pendingFlags.isEmpty()) {
                Text(
                    text = "No pending community flags.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pendingFlags.forEach { flag ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Issue #${flag.issueId} • ${flag.reason}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = com.svce.issuegram.domain.displayTime(flag.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { onReviewFlag(flag.id, "REVIEWED_KEPT") }
                                    ) { Text("Keep Post") }
                                    androidx.compose.material3.TextButton(
                                        onClick = { onReviewFlag(flag.id, "REVIEWED_ACTIONED") }
                                    ) { Text("Actioned") }
                                    androidx.compose.material3.TextButton(
                                        onClick = { onReviewFlag(flag.id, "DISMISSED") }
                                    ) { Text("Dismiss") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = {
                if (viewModel != null) {
                    viewModel.signOut()
                } else {
                    onSignOut()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_user),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.profile_sign_out), fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun AdminStatusMetricCard(
    modifier: Modifier = Modifier,
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
