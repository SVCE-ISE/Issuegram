package com.svce.issuegram.data.repository

import com.svce.issuegram.data.datasource.remote.AuthRemoteDataSource
import com.svce.issuegram.domain.model.User
import com.svce.issuegram.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class AuthRepositoryImpl(private val authRemoteDataSource: AuthRemoteDataSource) : AuthRepository {

    override fun observeCurrentUser(): Flow<User?> = authRemoteDataSource.observeAuthState()

    override fun getCurrentUser(): User? = authRemoteDataSource.getCurrentUser()

    override suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        authRemoteDataSource.updateDisplayName(name)
    }

    override suspend fun updateAvatarUri(uri: String?): Result<Unit> = runCatching {
        authRemoteDataSource.updatePhotoUri(uri)
    }

    override fun signOut() {
        authRemoteDataSource.signOut()
    }
}
