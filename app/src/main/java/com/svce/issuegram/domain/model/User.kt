package com.svce.issuegram.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class User(
    val id: String,
    val displayName: String,
    val email: String,
    val username: String = email.substringBefore('@').ifBlank { "citizen" },
    val avatarUrl: String? = null,
    val bio: String = "Civic Citizen Reporter",
    val isModerator: Boolean = false
)
