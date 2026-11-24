package com.svce.issuegram.data.mapper

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueMilestone
import com.svce.issuegram.data.model.IssueStatus
import java.time.Instant

object IssueMapper {
    fun toCivicDto(issue: Issue, id: Long = 0, authorId: String = "", authorUsername: String = issue.authorUsername ?: issue.authorName): CivicIssueDto {
        return CivicIssueDto(
            id = id,
            ticketCode = if (issue.ticketId.startsWith("#")) issue.ticketId else "#${issue.ticketId}",
            authorId = authorId,
            authorUsername = authorUsername,
            title = issue.title,
            caption = issue.description.orEmpty(),
            category = issue.category.name,
            locationName = issue.locationName,
            subLocation = issue.area,
            latitude = issue.latitude,
            longitude = issue.longitude,
            status = when (issue.status) {
                IssueStatus.Resolved -> "RESOLVED"
                IssueStatus.InProgress -> "IN_PROGRESS"
                else -> "REPORTED"
            },
            mediaUrls = listOfNotNull(issue.photoUri),
            createdAt = issue.createdAt.toString(),
        )
    }

    fun fromCivicDto(dto: CivicIssueDto): Issue {
        val catLower = dto.category.lowercase()
        val category = when {
            catLower.contains("road") || catLower.contains("pothole") || catLower.contains("transit") -> IssueCategory.Pothole
            catLower.contains("power") || catLower.contains("light") -> IssueCategory.Streetlight
            catLower.contains("water") || catLower.contains("drain") -> IssueCategory.Drainage
            catLower.contains("garbage") || catLower.contains("public works") -> IssueCategory.Garbage
            else -> runCatching { IssueCategory.valueOf(dto.category) }.getOrDefault(IssueCategory.Other)
        }
        val status = when (dto.status.uppercase()) {
            "RESOLVED" -> IssueStatus.Resolved
            "IN_PROGRESS" -> IssueStatus.InProgress
            else -> IssueStatus.Open
        }
        val instant = runCatching { Instant.parse(dto.createdAt) }.getOrDefault(Instant.now())
        val cleanCode = dto.ticketCode.removePrefix("#")

        val currentUser = Firebase.auth.currentUser
        val currentDisplayName = currentUser?.displayName?.takeIf { it.isNotBlank() }
        val currentEmail = currentUser?.email
        val currentEmailPrefix = currentEmail?.substringBefore("@")
        val cleanEmailPrefix = currentEmailPrefix?.replace(".", "_")?.replace("-", "_")

        val isCurrentUsersIssue = (currentUser != null && dto.authorId == currentUser.uid) ||
                (currentEmailPrefix != null && (
                    dto.authorUsername.equals(currentEmailPrefix, ignoreCase = true) ||
                    dto.authorUsername.equals(cleanEmailPrefix, ignoreCase = true) ||
                    dto.authorUsername.contains(currentEmailPrefix, ignoreCase = true)
                ))

        val cleanAuthor = when {
            isCurrentUsersIssue && currentDisplayName != null -> currentDisplayName
            dto.authorUsername.startsWith("user_") || dto.authorUsername.startsWith("citizen_") ->
                currentDisplayName ?: "Praveen Zalaki"
            currentEmailPrefix != null && (dto.authorUsername.equals(currentEmailPrefix, ignoreCase = true) || dto.authorUsername.equals(cleanEmailPrefix, ignoreCase = true)) ->
                currentDisplayName ?: "Praveen Zalaki"
            dto.authorUsername.contains("praveenzalaki", ignoreCase = true) ->
                currentDisplayName ?: "Praveen Zalaki"
            else -> {
                val candidate = dto.authorUsername.substringBefore("@")
                val stripped = candidate.replace(Regex("\\d+$"), "").trim()
                if (stripped.equals("praveenzalaki", ignoreCase = true)) {
                    currentDisplayName ?: "Praveen Zalaki"
                } else if (!candidate.contains(" ") && (candidate.contains("_") || candidate.contains("."))) {
                    val words = stripped.split("_", ".").filter { it.isNotBlank() }
                    if (words.isNotEmpty()) words.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } } else candidate
                } else {
                    candidate
                }
            }
        }

        val resolvedArea = if (dto.subLocation.isNotBlank() && !dto.subLocation.equals(dto.locationName, ignoreCase = true)) {
            dto.subLocation
        } else if (dto.locationName.contains(",")) {
            dto.locationName.substringAfter(",").trim()
        } else {
            dto.locationName
        }

        val domainMilestones = dto.milestones.map { m ->
            IssueMilestone(
                id = m.id,
                stepOrder = m.stepOrder,
                title = m.title,
                actor = m.actor,
                timestamp = runCatching { Instant.parse(m.timestampLabel) }.getOrDefault(instant),
                isCompleted = m.isCompleted,
            )
        }
        return Issue(
            ticketId = cleanCode.ifBlank { "IG-${dto.id}" },
            title = dto.title,
            description = dto.caption.ifBlank { null },
            category = category,
            status = status,
            area = resolvedArea,
            locationName = dto.locationName,
            authorName = cleanAuthor,
            createdAt = instant,
            likes = dto.upvotesCount,
            photoUri = dto.mediaUrls.firstOrNull(),
            isMine = isCurrentUsersIssue,
            latitude = dto.latitude,
            longitude = dto.longitude,
            authorId = dto.authorId,
            authorUsername = dto.authorUsername,
            milestones = domainMilestones,
        )
    }
}
