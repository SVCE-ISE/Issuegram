package com.svce.issuegram.domain.usecase.issue

import com.svce.issuegram.domain.repository.IssueRepository

class ToggleLikeUseCase(private val issueRepository: IssueRepository) {
    suspend operator fun invoke(ticketId: String): Result<Unit> = issueRepository.toggleLike(ticketId)
}
