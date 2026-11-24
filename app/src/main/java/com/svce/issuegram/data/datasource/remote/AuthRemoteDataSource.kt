package com.svce.issuegram.data.datasource.remote

import com.svce.issuegram.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRemoteDataSource {
    fun observeAuthState(): Flow<User?>
    fun getCurrentUser(): User?
    suspend fun updateDisplayName(name: String)
    suspend fun updatePhotoUri(uri: String?)
    fun signOut()
}
