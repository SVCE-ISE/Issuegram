package com.svce.issuegram.domain.repository

import com.svce.issuegram.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeCurrentUser(): Flow<User?>
    fun getCurrentUser(): User?
    suspend fun updateDisplayName(name: String): Result<Unit>
    suspend fun updateAvatarUri(uri: String?): Result<Unit>
    fun signOut()
}
