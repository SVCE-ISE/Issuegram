package com.svce.issuegram.data

import com.svce.issuegram.data.model.IssueStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class AlertKind { Status, Reply }

data class AppNotification(
    val id: String,
    val ticketId: String,
    val issueTitle: String,
    val kind: AlertKind,
    val status: IssueStatus? = null,
    val message: String,
    val at: Instant,
    val unread: Boolean,
)

/** Dummy alert feed until notifications come from FCM. */
object MockAlerts {

    private fun hoursAgo(hours: Long): Instant = Instant.now().minus(hours, ChronoUnit.HOURS)
    private fun daysAgo(days: Long): Instant = Instant.now().minus(days, ChronoUnit.DAYS)

    val all: List<AppNotification> = listOf(
        AppNotification(
            id = "n1",
            ticketId = "IG-2481",
            issueTitle = "Overflowing drain near the bus stop",
            kind = AlertKind.Status,
            status = IssueStatus.InProgress,
            message = "Work has started. The drain will be cleared this week.",
            at = hoursAgo(3),
            unread = true,
        ),
        AppNotification(
            id = "n2",
            ticketId = "IG-2468",
            issueTitle = "Broken road surface after pipeline work",
            kind = AlertKind.Status,
            status = IssueStatus.Resolved,
            message = "Marked fixed. Confirm it looks right from your side.",
            at = hoursAgo(7),
            unread = true,
        ),
        AppNotification(
            id = "n3",
            ticketId = "IG-2463",
            issueTitle = "Sewage mixing with the drinking water line",
            kind = AlertKind.Reply,
            message = "Admin: The valve line is scheduled for Thursday morning.",
            at = hoursAgo(26),
            unread = false,
        ),
        AppNotification(
            id = "n4",
            ticketId = "IG-2476",
            issueTitle = "Deep pothole swallowing two-wheelers",
            kind = AlertKind.Status,
            status = IssueStatus.Acknowledged,
            message = "Your report was acknowledged by the ward office.",
            at = hoursAgo(30),
            unread = false,
        ),
        AppNotification(
            id = "n5",
            ticketId = "IG-2463",
            issueTitle = "Sewage mixing with the drinking water line",
            kind = AlertKind.Status,
            status = IssueStatus.Reopened,
            message = "Reopened after your report. The team has been notified.",
            at = daysAgo(3),
            unread = false,
        ),
    )
}
