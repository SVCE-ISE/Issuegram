package com.svce.issuegram.domain.usecase.auth

import com.svce.issuegram.domain.model.User
import com.svce.issuegram.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class ObserveCurrentUserUseCase(private val authRepository: AuthRepository) {
    operator fun invoke(): Flow<User?> = authRepository.observeCurrentUser()
}
