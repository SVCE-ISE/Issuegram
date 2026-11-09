package com.svce.issuegram.ui.messages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.svce.issuegram.R
import com.svce.issuegram.ui.theme.IssuegramShapeDefaults
import kotlinx.coroutines.launch

private data class MessageChannel(
    val id: String,
    val name: String,
    val subtitle: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0,
    val isAdmin: Boolean = false,
    val badgeColor: Color = Color(0xFF0E4B5A),
    val iconRes: Int = R.drawable.ic_message_circle
)

private data class ChatBubble(
    val id: String,
    val sender: String,
    val message: String,
    val timestamp: String,
    val isFromUser: Boolean,
    val ticketBadge: String? = null
)

@Composable
fun MessagesScreen(
    modifier: Modifier = Modifier,
    onBackToHome: () -> Unit = {}
) {
    var activeChatId by remember { mutableStateOf<String?>(null) }

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
                onBack = onBackToHome,
                onSelectChannel = { channelId ->
                    activeChatId = channelId
                }
            )
        } else {
            AdminChatView(
                onBack = { activeChatId = null }
            )
        }
    }
}

@Composable
private fun MessageChannelsView(
    onBack: () -> Unit,
    onSelectChannel: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    val channels = listOf(
        MessageChannel(
            id = "admin",
            name = "Campus Admin",
            subtitle = "maintenance dispatch",
            lastMessage = "Technician dispatched to CS Lab 3 for #IG-106",
            timestamp = "2m ago",
            unreadCount = 1,
            isAdmin = true,
            badgeColor = Color(0xFF0E4B5A),
            iconRes = R.drawable.ic_verified
        ),
        MessageChannel(
            id = "it_helpdesk",
            name = "IT Lab Helpdesk",
            subtitle = "hardware & systems",
            lastMessage = "Replacement keycaps will arrive before morning lab.",
            timestamp = "1h ago",
            badgeColor = Color(0xFF453856),
            iconRes = R.drawable.ic_report_alert
        ),
        MessageChannel(
            id = "facilities",
            name = "Facilities Desk",
            subtitle = "hostel & campus infra",
            lastMessage = "Water dispenser filter maintenance completed.",
            timestamp = "Yesterday",
            badgeColor = Color(0xFF1B3B2B),
            iconRes = R.drawable.ic_check
        ),
        MessageChannel(
            id = "safety",
            name = "Safety & Security",
            subtitle = "electricals & control room",
            lastMessage = "Main Auditorium audio lines inspection scheduled.",
            timestamp = "Nov 5",
            badgeColor = Color(0xFF4A3520),
            iconRes = R.drawable.ic_map_pin
        )
    )

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

        // Segmented List Item Stack (4 Items)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            channels.forEachIndexed { index, channel ->
                val itemShape = when {
                    channels.size == 1 -> RoundedCornerShape(16.dp)
                    index == 0 -> IssuegramShapeDefaults.topListItemShape
                    index == channels.size - 1 -> IssuegramShapeDefaults.bottomListItemShape
                    else -> IssuegramShapeDefaults.middleListItemShape
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
                                .background(channel.badgeColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(channel.iconRes),
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
                                text = channel.timestamp,
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
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputMessage by remember { mutableStateOf("") }

    val messages = remember {
        mutableStateListOf(
            ChatBubble(
                id = "1",
                sender = "zenzer0s",
                message = "Hi, I reported issue #IG-106 regarding stuck mechanical keys on workstation 14 in CSE Lab 3. We have lab exams starting tomorrow morning.",
                timestamp = "5:26 PM",
                isFromUser = true
            ),
            ChatBubble(
                id = "2",
                sender = "Admin",
                message = "Hello @zenzer0s! We've received your ticket.",
                timestamp = "5:27 PM",
                isFromUser = false,
                ticketBadge = "#IG-106 • Keycaps Jammed (CS Lab 3)"
            ),
            ChatBubble(
                id = "3",
                sender = "Admin",
                message = "Technician Rajesh has been dispatched with replacement keycaps and cleaning kit. ETA: 15 minutes.",
                timestamp = "5:28 PM",
                isFromUser = false
            )
        )
    }

    LaunchedEffect(messages.size) {
        listState.animateScrollToItem(messages.size - 1)
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

                // Admin Avatar with Online Dot
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

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                            .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.surfaceContainer), CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Campus Maintenance Admin",
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
                        text = "Online • Estate Support Office",
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
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            items(messages) { msg ->
                if (msg.isFromUser) {
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
                            text = "${msg.timestamp} • Delivered",
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
                            text = "${msg.sender} • ${msg.timestamp}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp, start = 4.dp)
                        )
                    }
                }
            }
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
                    onValueChange = { inputMessage = it },
                    placeholder = {
                        Text(
                            text = "Message Campus Admin...",
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
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                )

                // Send Button
                IconButton(
                    onClick = {
                        if (inputMessage.isNotBlank()) {
                            messages.add(
                                ChatBubble(
                                    id = System.currentTimeMillis().toString(),
                                    sender = "zenzer0s",
                                    message = inputMessage.trim(),
                                    timestamp = "Just now",
                                    isFromUser = true
                                )
                            )
                            inputMessage = ""
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
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
