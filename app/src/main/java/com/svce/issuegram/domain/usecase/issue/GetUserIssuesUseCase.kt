package com.svce.issuegram.domain.usecase.issue

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.domain.repository.IssueRepository
import kotlinx.coroutines.flow.Flow

class GetUserIssuesUseCase(private val issueRepository: IssueRepository) {
    operator fun invoke(userId: String, authorName: String): Flow<List<Issue>> =
        issueRepository.getUserIssues(userId, authorName)
}
