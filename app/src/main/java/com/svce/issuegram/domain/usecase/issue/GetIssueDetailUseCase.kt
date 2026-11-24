package com.svce.issuegram.domain.usecase.issue

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.domain.repository.IssueRepository
import kotlinx.coroutines.flow.Flow

class GetIssueDetailUseCase(private val issueRepository: IssueRepository) {
    operator fun invoke(ticketId: String): Flow<Issue?> = issueRepository.getIssueById(ticketId)
}
