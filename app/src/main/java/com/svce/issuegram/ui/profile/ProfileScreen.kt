package com.svce.issuegram.ui.profile

import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.svce.issuegram.R
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.data.model.IssueFlagDto
import com.svce.issuegram.data.model.UserProfileDto
import com.svce.issuegram.ui.components.IssuegramMediaImage
import com.svce.issuegram.ui.theme.IssuegramShapeDefaults

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
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
) {
    val scrollState = rememberScrollState()

    var firebaseUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            firebaseUser = auth.currentUser
        }
        val auth = Firebase.auth
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    var customAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var customDisplayName by remember { mutableStateOf<String?>(null) }

    val activeUser = currentUser ?: remember(firebaseUser, customAvatarUri, customDisplayName) {
        val email = firebaseUser?.email.orEmpty()
        val derivedUsername = email.substringBefore('@').ifBlank { "citizen" }
        val name = customDisplayName
            ?: firebaseUser?.displayName?.takeIf { it.isNotBlank() }
            ?: com.svce.issuegram.data.ProfileMock.name
        val photo = customAvatarUri?.toString()
            ?: firebaseUser?.photoUrl?.toString()

        UserProfileDto(
            id = firebaseUser?.uid ?: "user_me",
            username = derivedUsername,
            displayName = name,
            avatarUrl = photo,
            email = email.ifBlank { com.svce.issuegram.data.ProfileMock.email },
            bio = "Civic Citizen Reporter"
        )
    }
    val username = activeUser.username
    val avatarRef = activeUser.avatarUrl
    val userEmail = activeUser.email ?: firebaseUser?.email.orEmpty()

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            customAvatarUri = uri
            onUpdateAvatar(uri)
        }
    }

    val userPostedIssues = remember(allIssues, activeUser.id, com.svce.issuegram.data.IssueStore.issues.size) {
        if (allIssues.isNotEmpty()) {
            allIssues.filter { it.authorId == activeUser.id }
        } else {
            com.svce.issuegram.data.IssueStore.issues.filter {
                it.isMine || it.authorName == "Priya S." || it.authorName == "You" || it.authorName == activeUser.displayName
            }.mapIndexed { idx, it ->
                CivicIssueDto(
                    id = idx.toLong() + 1,
                    ticketCode = it.ticketId,
                    authorId = activeUser.id,
                    authorUsername = activeUser.username,
                    title = it.title,
                    caption = it.description.orEmpty(),
                    category = it.category.name,
                    locationName = it.locationName,
                    latitude = 12.9716,
                    longitude = 77.5946,
                    status = when (it.status) {
                        com.svce.issuegram.data.model.IssueStatus.Resolved -> "RESOLVED"
                        com.svce.issuegram.data.model.IssueStatus.InProgress -> "IN_PROGRESS"
                        else -> "REPORTED"
                    },
                    mediaUrls = listOfNotNull(it.photoUri),
                    createdAt = it.createdAt.toString(),
                )
            }
        }
    }
    var editingName by remember { mutableStateOf(false) }
    var editedName by remember(activeUser.displayName) { mutableStateOf(activeUser.displayName) }
    if (editingName) androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!profileBusy) editingName = false },
        title = { Text("Your name") },
        text = { Column {
            androidx.compose.material3.OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it.take(36) },
                enabled = !profileBusy,
                label = { Text("Name") },
                singleLine = true
            )
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    customDisplayName = editedName
                    onUpdateName(editedName) { editingName = false }
                    editingName = false
                },
                enabled = !profileBusy && editedName.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = { editingName = false },
                enabled = !profileBusy
            ) { Text("Cancel") }
        }
    )

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
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = Modifier
                .size(92.dp)
                .clickable(enabled = !profileBusy) {
                    avatarPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(2.5.dp, Color(0xFF30363D)),
                modifier = Modifier.fillMaxSize()
            ) {
                IssuegramMediaImage(
                    mediaRef = avatarRef,
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    fallbackDrawableRes = R.drawable.avatar_placeholder,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Surface(
                shape = CircleShape,
                color = Color(0xFF67B7F7),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_image_plus),
                        contentDescription = "Change Profile Picture",
                        tint = Color(0xFF0F2A30),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Real Name and Email below Profile Picture
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable(enabled = !profileBusy) {
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
        Spacer(modifier = Modifier.height(20.dp))

        if (profileBusy) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (customAvatarUri != null || activeUser.avatarUrl != null) androidx.compose.material3.TextButton(
            onClick = {
                customAvatarUri = null
                onRemoveAvatar()
            },
            enabled = !profileBusy
        ) { Text("Remove profile photo") }

        // 3. Compact Horizontal Alignment of the Two Items Below
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
                    // Circular Icon Badge (Deep Teal)
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

                    // Text Column: Number above, label below
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
                            text = "reported",
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
                    // Circular Icon Badge (Deep Purple)
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

                    // Text Column: Number above, label below
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
                            text = "solved",
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
                text = "Posted Issues",
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

        Spacer(modifier = Modifier.height(6.dp))

        // Segmented List Item Stack
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            userPostedIssues.forEachIndexed { index, issue ->
                val itemShape = when {
                    userPostedIssues.size == 1 -> RoundedCornerShape(16.dp)
                    index == 0 -> IssuegramShapeDefaults.topListItemShape
                    index == userPostedIssues.size - 1 -> IssuegramShapeDefaults.bottomListItemShape
                    else -> IssuegramShapeDefaults.middleListItemShape
                }

                val timestampLabel = com.svce.issuegram.domain.displayTime(issue.createdAt)
                val thumbRef = issue.mediaUrls.firstOrNull()

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
                                    text = listOf(issue.locationName, timestampLabel).filter { it.isNotBlank() }.joinToString(" • "),
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
                            color = if (issue.isResolved) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFFA726).copy(alpha = 0.15f)
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
        Spacer(modifier = Modifier.height(96.dp))
    }
}
