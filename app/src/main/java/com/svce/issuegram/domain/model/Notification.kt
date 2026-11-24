package com.svce.issuegram.domain.model

import com.svce.issuegram.data.model.IssueStatus
import java.time.Instant

enum class AlertKind { Status, Reply }

data class Notification(
    val id: String,
    val ticketId: String,
    val issueTitle: String,
    val kind: AlertKind,
    val status: IssueStatus? = null,
    val message: String,
    val createdAt: Instant,
    val isUnread: Boolean,
)
