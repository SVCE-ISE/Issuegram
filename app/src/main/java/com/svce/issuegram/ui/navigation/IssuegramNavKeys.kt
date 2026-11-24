package com.svce.issuegram.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object FeedKey : NavKey

@Serializable
data object MapKey : NavKey

@Serializable
data object CameraKey : NavKey

@Serializable
data object NotificationsKey : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data class IssueDetailKey(val ticketId: String) : NavKey
