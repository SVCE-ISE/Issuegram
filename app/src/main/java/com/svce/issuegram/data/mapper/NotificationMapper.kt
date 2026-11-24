package com.svce.issuegram.data.mapper

import com.svce.issuegram.data.AlertKind as DataAlertKind
import com.svce.issuegram.data.AppNotification
import com.svce.issuegram.domain.model.AlertKind
import com.svce.issuegram.domain.model.Notification

object NotificationMapper {
    fun toDomain(notification: AppNotification): Notification = Notification(
        id = notification.id,
        ticketId = notification.ticketId,
        issueTitle = notification.issueTitle,
        kind = when (notification.kind) {
            DataAlertKind.Status -> AlertKind.Status
            DataAlertKind.Reply -> AlertKind.Reply
        },
        status = notification.status,
        message = notification.message,
        createdAt = notification.at,
        isUnread = notification.unread
    )

    fun toData(notification: Notification): AppNotification = AppNotification(
        id = notification.id,
        ticketId = notification.ticketId,
        issueTitle = notification.issueTitle,
        kind = when (notification.kind) {
            AlertKind.Status -> DataAlertKind.Status
            AlertKind.Reply -> DataAlertKind.Reply
        },
        status = notification.status,
        message = notification.message,
        at = notification.createdAt,
        unread = notification.isUnread
    )
}
