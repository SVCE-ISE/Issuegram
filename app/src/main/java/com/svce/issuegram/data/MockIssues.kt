package com.svce.issuegram.data

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

/** In-memory feed data until Firestore is wired in. */
object MockIssues {

    private fun daysAgo(days: Long): Instant = Instant.now().minus(days, ChronoUnit.DAYS)

    val all: List<Issue> = listOf(
        Issue(
            ticketId = "IG-2481",
            title = "Overflowing drain near the bus stop",
            description = "Water has been standing for three days and is spilling onto the footpath. Mosquitoes are breeding around it.",
            category = IssueCategory.Drainage,
            status = IssueStatus.InProgress,
            area = "Anna Nagar",
            locationName = "2nd Avenue bus stop",
            authorName = "Priya S.",
            createdAt = daysAgo(2),
            likes = 34,
        ),
        Issue(
            ticketId = "IG-2479",
            title = "Streetlight not working on lane 4",
            description = "The whole stretch is dark after 7 PM. Hard to walk after the shops close.",
            category = IssueCategory.Streetlight,
            status = IssueStatus.Assigned,
            area = "Anna Nagar",
            locationName = "Lane 4, near the park gate",
            authorName = "Karthik R.",
            createdAt = daysAgo(3),
            likes = 21,
        ),
        Issue(
            ticketId = "IG-2476",
            title = "Deep pothole swallowing two-wheelers",
            description = null,
            category = IssueCategory.Pothole,
            status = IssueStatus.Acknowledged,
            area = "T. Nagar",
            locationName = "Usman Road junction",
            authorName = "Divya M.",
            createdAt = daysAgo(4),
            likes = 58,
        ),
        Issue(
            ticketId = "IG-2472",
            title = "Garbage not collected for a week",
            description = "The bin at the corner is overflowing and dogs are scattering waste on the street.",
            category = IssueCategory.Garbage,
            status = IssueStatus.Open,
            area = "T. Nagar",
            locationName = "Ranganathan Street corner",
            authorName = "Arjun P.",
            createdAt = daysAgo(5),
            likes = 42,
        ),
        Issue(
            ticketId = "IG-2468",
            title = "Broken road surface after pipeline work",
            description = "The patch was never repaved properly. Loose gravel is a hazard for cyclists.",
            category = IssueCategory.Road,
            status = IssueStatus.Resolved,
            area = "Adyar",
            locationName = "Sardar Patel Road, opposite depot",
            authorName = "Meera K.",
            createdAt = daysAgo(9),
            likes = 27,
        ),
        Issue(
            ticketId = "IG-2463",
            title = "Sewage mixing with the drinking water line",
            description = "Strong smell from the tap since Monday morning.",
            category = IssueCategory.Water,
            status = IssueStatus.Reopened,
            area = "Adyar",
            locationName = "Kasturba Nagar, block C",
            authorName = "Sundar V.",
            createdAt = daysAgo(11),
            likes = 73,
        ),
        Issue(
            ticketId = "IG-2459",
            title = "School-zone crossing has no speed humps",
            description = "Vehicles turn fast into the school lane every morning. Parents are worried.",
            category = IssueCategory.Safety,
            status = IssueStatus.Acknowledged,
            area = "Velachery",
            locationName = "Vijayanagar school lane",
            authorName = "Lakshmi N.",
            createdAt = daysAgo(13),
            likes = 66,
        ),
        Issue(
            ticketId = "IG-2455",
            title = "Fallen tree blocking the footpath",
            description = "Pedestrians are stepping onto the main road to get around it.",
            category = IssueCategory.Other,
            status = IssueStatus.Resolved,
            area = "Velachery",
            locationName = "100 Feet Road, near the temple",
            authorName = "Ravi T.",
            createdAt = daysAgo(16),
            likes = 18,
        ),
    )
}
