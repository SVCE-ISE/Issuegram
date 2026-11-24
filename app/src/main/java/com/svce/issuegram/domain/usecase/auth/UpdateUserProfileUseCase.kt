package com.svce.issuegram.domain.usecase.auth

import com.svce.issuegram.domain.repository.AuthRepository

class UpdateUserProfileUseCase(private val authRepository: AuthRepository) {
    suspend fun updateName(name: String): Result<Unit> = authRepository.updateDisplayName(name)

    suspend fun updateAvatar(uri: String?): Result<Unit> = authRepository.updateAvatarUri(uri)
}
