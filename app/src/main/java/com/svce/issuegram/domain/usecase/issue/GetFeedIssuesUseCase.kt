package com.svce.issuegram.domain.usecase.issue

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.domain.repository.IssueRepository
import kotlinx.coroutines.flow.Flow

class GetFeedIssuesUseCase(private val issueRepository: IssueRepository) {
    operator fun invoke(): Flow<List<Issue>> = issueRepository.getFeedIssues()
}
