package com.svce.issuegram.ui.messages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.core.graphics.toColorInt
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.svce.issuegram.data.model.DeliveryState
import com.svce.issuegram.domain.displayTime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.svce.issuegram.R
import com.svce.issuegram.data.model.ChannelDto
import com.svce.issuegram.data.model.ChannelMessageDto
import com.svce.issuegram.ui.theme.IssuegramShapeDefaults

@Composable
fun MessagesScreen(
    modifier: Modifier = Modifier,
    channels: List<ChannelDto> = emptyList(),
    messagesByChannel: Map<String, List<ChannelMessageDto>> = emptyMap(),
    currentUserId: String = "",
    onOpenChannel: (String?) -> Unit = {},
    onRetryMessages: () -> Unit = {},
    onSendMessage: (channelId: String, message: String, onQueued: () -> Unit) -> Unit = { _, _, _ -> },
    onBackToHome: () -> Unit = {},
    initialChannelId: String? = null,
    initialMessage: String = "",
    sending: Boolean = false,
    onContextConsumed: () -> Unit = {},
) {
    var activeChatId by rememberSaveable { mutableStateOf(initialChannelId) }
    LaunchedEffect(initialChannelId) { if (initialChannelId != null) activeChatId = initialChannelId }

    val displayChannels = remember(channels) {
        channels
    }

    BackHandler(activeChatId != null) { activeChatId = null }
    DisposableEffect(activeChatId) {
        onOpenChannel(activeChatId)
        onDispose { onOpenChannel(null) }
    }
    AnimatedContent(
        targetState = activeChatId,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn() togetherWith
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut()
            } else {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn() togetherWith
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut()
            }
        },
        label = "ChatScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { chatId ->
        if (chatId == null) {
            MessageChannelsView(
                channels = displayChannels,
                onBack = onBackToHome,
                onSelectChannel = { channelId ->
                    activeChatId = channelId
                }
            )
        } else {
            val activeChannel = displayChannels.firstOrNull { it.id == chatId }
            val channelMsgs = messagesByChannel[chatId].orEmpty()
            AdminChatView(
                channelId = chatId,
                initialMessage = if (chatId == initialChannelId) initialMessage else "",
                onContextConsumed = onContextConsumed,
                sending = sending,
                channelTitle = activeChannel?.name ?: "Civic Dispatch Admin",
                channelSubtitle = activeChannel?.subtitle ?: "Civic Support Desk",
                messages = channelMsgs,
                currentUserId = currentUserId,
                onRetryMessages = onRetryMessages,
                onSendMessage = { msg, queued -> onSendMessage(chatId, msg, queued) },
                onBack = { activeChatId = null }
            )
        }
    }
}

@Composable
private fun MessageChannelsView(
    channels: List<ChannelDto>,
    onBack: () -> Unit,
    onSelectChannel: (String) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back to Home",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "Messages",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Segmented List Item Stack
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (channels.isEmpty()) Text("Support departments will appear here when available.", modifier = Modifier.padding(20.dp))
            channels.forEachIndexed { index, channel ->
                val itemShape = when {
                    channels.size == 1 -> RoundedCornerShape(16.dp)
                    index == 0 -> IssuegramShapeDefaults.topListItemShape
                    index == channels.size - 1 -> IssuegramShapeDefaults.bottomListItemShape
                    else -> IssuegramShapeDefaults.middleListItemShape
                }

                val badgeColor = parseHexColor(channel.badgeColorHex)
                val iconRes = when (channel.iconKey) {
                    "verified" -> R.drawable.ic_verified
                    "report_alert" -> R.drawable.ic_report_alert
                    "check" -> R.drawable.ic_check
                    "map_pin" -> R.drawable.ic_map_pin
                    else -> R.drawable.ic_message_circle
                }

                Card(
                    onClick = { onSelectChannel(channel.id) },
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
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Channel Circular Avatar (Colored pill style)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(badgeColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = if (channel.isAdmin) Color(0xFF67B7F7) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Middle Column: Bold Title & Subtitle underneath
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (channel.isAdmin) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_verified),
                                        contentDescription = "Verified Admin",
                                        tint = Color(0xFF67B7F7),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            Text(
                                text = channel.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right Column: Time & Unread Badge
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = channel.lastTimestampLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (channel.unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(Color(0xFF67B7F7), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${channel.unreadCount}",
                                        color = Color(0xFF0F2A30),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
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

@Composable
private fun AdminChatView(
    channelId: String,
    initialMessage: String,
    onContextConsumed: () -> Unit,
    sending: Boolean,
    channelTitle: String,
    channelSubtitle: String,
    messages: List<ChannelMessageDto>,
    currentUserId: String,
    onRetryMessages: () -> Unit,
    onSendMessage: (String, () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val drafts = remember(context) { com.svce.issuegram.data.local.MessageDraftStore(context) }
    var inputMessage by rememberSaveable(currentUserId, channelId) { mutableStateOf(drafts.read(currentUserId, channelId)) }
    LaunchedEffect(initialMessage) {
        if (initialMessage.isNotBlank()) {
            if (!inputMessage.contains(initialMessage)) inputMessage = listOf(inputMessage, initialMessage).filter { it.isNotBlank() }.joinToString("\n").take(2000)
            onContextConsumed()
        }
    }
    LaunchedEffect(inputMessage) { drafts.save(currentUserId, channelId, inputMessage) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {

        // Chat Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(0.5.dp, Color(0xFF30363D))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Department avatar
                Box(modifier = Modifier.size(40.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0E3D4F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_verified),
                            contentDescription = null,
                            tint = Color(0xFF67B7F7),
                            modifier = Modifier.size(20.dp)
                        )
                    }


                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = channelTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_verified),
                            contentDescription = "Verified",
                            tint = Color(0xFF67B7F7),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = channelSubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }

        // Messages Thread
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date Divider
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "Conversation",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (messages.isEmpty()) item { Text("Start a conversation with this department.", style = MaterialTheme.typography.bodySmall) }
            items(messages, key = { it.requestId ?: it.id.toString() }) { msg ->
                if (msg.senderId == currentUserId) {
                    // User Message Bubble (Right-aligned, Cyan tint)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 4.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            ),
                            color = Color(0xFF1E3A4B),
                            border = BorderStroke(1.dp, Color(0xFF67B7F7).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Text(
                                text = msg.message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    lineHeight = 18.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }

                        Text(
                            text = "${displayTime(msg.createdAt ?: msg.timestampLabel)} • ${when (msg.delivery) { DeliveryState.SENT -> "Sent"; DeliveryState.QUEUED -> "Waiting to send"; DeliveryState.FAILED -> "Failed — tap Retry above" }}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp, end = 4.dp)
                        )
                    }
                } else {
                    // Admin Message Bubble (Left-aligned, Dark SurfaceContainer)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            ),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = msg.message,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.5.sp,
                                        lineHeight = 18.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Embedded Ticket Snapshot Card
                                if (msg.ticketBadge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF161B22),
                                        border = BorderStroke(1.dp, Color(0xFF67B7F7).copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_map_pin),
                                                contentDescription = null,
                                                tint = Color(0xFF67B7F7),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = msg.ticketBadge,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.sp
                                                ),
                                                color = Color(0xFF67B7F7)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "${msg.senderUsername} • ${displayTime(msg.createdAt ?: msg.timestampLabel)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp, start = 4.dp)
                        )
                    }
                }
            }
        }

        if (messages.any { it.delivery == DeliveryState.FAILED }) {
            androidx.compose.material3.TextButton(onClick = onRetryMessages) { Text("Retry failed messages") }
        }
        // Bottom Chat Input Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(0.5.dp, Color(0xFF30363D))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it.take(2000) },
                    placeholder = {
                        Text(
                            text = "Message $channelTitle...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedBorderColor = Color(0xFF67B7F7),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(20.dp)
                )

                // Send Button
                IconButton(
                    onClick = {
                        if (inputMessage.isNotBlank()) {
                            val submitted = inputMessage
                            onSendMessage(submitted.trim()) {
                                if (inputMessage == submitted) { inputMessage = ""; drafts.save(currentUserId, channelId, "") }
                            }
                        }
                    },
                    enabled = !sending && inputMessage.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (inputMessage.isNotBlank()) Color(0xFF67B7F7) else Color(0xFF21262D),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_send),
                        contentDescription = "Send",
                        tint = if (inputMessage.isNotBlank()) Color(0xFF0F2A30) else Color(0xFF8B949E),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return runCatching {
        Color(hex.toColorInt())
    }.getOrDefault(Color(0xFF0E4B5A))
}
