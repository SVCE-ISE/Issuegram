package com.svce.issuegram.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    val id: String = "",
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String = "Civic Citizen Reporter",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateProfileRequest(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String = "Civic Citizen Reporter",
)

@Serializable
data class IssueMilestoneDto(
    val id: Long = 0,
    @SerialName("issue_id") val issueId: Long = 0,
    @SerialName("step_order") val stepOrder: Int,
    val title: String,
    val actor: String,
    @SerialName("timestamp_label") val timestampLabel: String,
    @SerialName("is_completed") val isCompleted: Boolean,
)

@Serializable
data class CivicIssueDto(
    val id: Long = 0,
    @SerialName("ticket_code") val ticketCode: String = "",
    @SerialName("author_id") val authorId: String,
    @SerialName("author_username") val authorUsername: String,
    @SerialName("author_avatar_url") val authorAvatarUrl: String? = null,
    val title: String,
    val caption: String,
    val category: String = "Civic Infrastructure",
    @SerialName("location_name") val locationName: String,
    @SerialName("sub_location") val subLocation: String = "",
    val latitude: Double,
    val longitude: Double,
    @SerialName("is_gps_verified") val isGpsVerified: Boolean = false,
    val status: String = "REPORTED",
    @SerialName("media_urls") val mediaUrls: List<String> = emptyList(),
    @SerialName("media_type") val mediaType: String = "PHOTO",
    @SerialName("upvotes_count") val upvotesCount: Int = 0,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("issue_milestones") val milestones: List<IssueMilestoneDto> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val isResolved: Boolean
        get() = status == "RESOLVED"
}

@Serializable
data class CreateIssueRequest(
    @SerialName("request_id") val requestId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_username") val authorUsername: String,
    @SerialName("author_avatar_url") val authorAvatarUrl: String? = null,
    val title: String,
    val caption: String,
    val category: String,
    @SerialName("location_name") val locationName: String,
    @SerialName("sub_location") val subLocation: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("is_gps_verified") val isGpsVerified: Boolean = false,
    @SerialName("media_urls") val mediaUrls: List<String>,
    @SerialName("media_type") val mediaType: String = "PHOTO",
)

@Serializable
data class UpdateIssueStatusRequest(
    val status: String,
)

@Serializable
data class EditIssueRequest(
    val title: String,
    val caption: String,
    val category: String,
    @SerialName("location_name") val locationName: String,
    @SerialName("sub_location") val subLocation: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: String = "",
    @SerialName("last_message") val lastMessage: String = "Contact this department",
    @SerialName("last_timestamp_label") val lastTimestampLabel: String = "",
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("badge_color_hex") val badgeColorHex: String = "#0E4B5A",
    @SerialName("icon_key") val iconKey: String = "verified",
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class ChannelMessageDto(
    val id: Long = 0,
    @SerialName("channel_id") val channelId: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_username") val senderUsername: String,
    val message: String,
    @SerialName("timestamp_label") val timestampLabel: String,
    @SerialName("is_from_admin") val isFromAdmin: Boolean = false,
    @SerialName("ticket_badge") val ticketBadge: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    val delivery: DeliveryState = DeliveryState.SENT,
)

@Serializable
data class SendMessageRequest(
    @SerialName("request_id") val requestId: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("channel_id") val channelId: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_username") val senderUsername: String,
    val message: String,
    @SerialName("timestamp_label") val timestampLabel: String,
    @SerialName("is_from_admin") val isFromAdmin: Boolean = false,
    @SerialName("ticket_badge") val ticketBadge: String? = null,
)

@Serializable
enum class DeliveryState { QUEUED, FAILED, SENT }

@Serializable
data class ConversationDto(
    val id: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("channel_id") val channelId: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class StaffMembershipDto(@SerialName("channel_id") val channelId: String)

@Serializable
data class ModeratorDto(@SerialName("user_id") val userId: String)

@Serializable
data class IssueConfirmationDto(@SerialName("issue_id") val issueId: Long)

@Serializable
data class IssueFlagDto(
    val id: Long = 0,
    @SerialName("issue_id") val issueId: Long,
    @SerialName("reporter_id") val reporterId: String,
    val reason: String,
    @SerialName("moderation_status") val moderationStatus: String = "PENDING",
    @SerialName("review_notes") val reviewNotes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AuthUser(val id: String)

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("expires_at") val expiresAt: Long = 0,
    val user: AuthUser,
) {
    fun withExpiry(nowSeconds: Long) = copy(expiresAt = expiresAt.takeIf { it > 0 } ?: (nowSeconds + expiresIn))
}
