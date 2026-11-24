package com.svce.issuegram.data.mapper

import com.google.firebase.auth.FirebaseUser
import com.svce.issuegram.domain.model.User

object UserMapper {
    fun toDomain(firebaseUser: FirebaseUser?): User? {
        if (firebaseUser == null) return null
        val email = firebaseUser.email.orEmpty()
        val displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@').takeIf {
                it.isNotBlank()
            }?.replace('.', ' ')?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
            ?: "Citizen Reporter"
        val username = email.substringBefore('@').ifBlank { "citizen_${firebaseUser.uid.take(6)}" }
        return User(
            id = firebaseUser.uid,
            displayName = displayName,
            email = email,
            username = username,
            avatarUrl = firebaseUser.photoUrl?.toString(),
            bio = "Civic Citizen Reporter",
            isModerator = false
        )
    }
}
