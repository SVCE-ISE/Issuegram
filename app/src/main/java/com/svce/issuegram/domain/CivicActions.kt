package com.svce.issuegram.domain

import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.data.model.EditIssueRequest

object CivicActions {
    val flagReasons = listOf("Spam or advertising", "Harmful or private content", "Misleading report", "Duplicate report")

    fun requireOwner(issue: CivicIssueDto, userId: String) {
        check(userId.isNotBlank() && issue.authorId == userId) { "You can only edit or delete your own reports." }
    }

    fun edit(caption: String, category: String, location: String, detail: String, lat: Double, lon: Double): EditIssueRequest {
        ReportRules.validate(caption, category, location, lat, lon)
        require(location.trim().length <= 160 && detail.trim().length <= 240) { "Shorten the location details." }
        return EditIssueRequest(caption.trim().lineSequence().first().take(52), caption.trim(), category, location.trim(), detail.trim(), lat, lon)
    }

    fun matches(issue: CivicIssueDto, query: String, category: String, status: String): Boolean =
        (category == "All" || issue.category == category) &&
            (status == "All" || if (status == "Resolved") issue.isResolved else !issue.isResolved) &&
            (query.isBlank() || listOf(issue.ticketCode, issue.title, issue.caption, issue.locationName, issue.subLocation)
                .any { it.contains(query.trim(), ignoreCase = true) })

    fun shareText(issue: CivicIssueDto): String = buildString {
        appendLine("Issuegram · ${issue.ticketCode.ifBlank { "Report #${issue.id}" }}")
        appendLine(issue.caption)
        appendLine("${issue.category} · ${issue.status.replace('_', ' ')}")
        appendLine(issue.locationName)
        append("https://www.google.com/maps/search/?api=1&query=${issue.latitude},${issue.longitude}")
    }
}
