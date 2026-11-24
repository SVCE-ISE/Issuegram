package com.svce.issuegram.domain.usecase.issue

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.domain.repository.IssueRepository

class CreateIssueUseCase(private val issueRepository: IssueRepository) {
    suspend operator fun invoke(
        title: String,
        description: String?,
        category: IssueCategory,
        locationName: String,
        photoUri: String?,
        latitude: Double = 12.9716,
        longitude: Double = 77.5946,
        authorName: String = "Praveen Zalaki",
        authorUsername: String? = null
    ): Result<Issue> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.length < 3) {
            return Result.failure(IllegalArgumentException("Title must be at least 3 characters"))
        }
        val trimmedLocation = locationName.trim().ifBlank { "Location reported" }
        return issueRepository.createIssue(
            title = trimmedTitle,
            description = description?.trim()?.ifBlank { null },
            category = category,
            locationName = trimmedLocation,
            photoUri = photoUri,
            latitude = latitude,
            longitude = longitude,
            authorName = authorName,
            authorUsername = authorUsername
        )
    }
}
