package com.svce.issuegram.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.svce.issuegram.MainActivity
import com.svce.issuegram.R
import com.svce.issuegram.data.model.IssueStatus

object SystemNotificationHelper {

    const val CHANNEL_ID = "civic_status_updates"
    private const val CHANNEL_NAME = "Civic Issue Updates"
    private const val CHANNEL_DESC = "Notifications for civic issue status and milestone transitions"

    fun createNotificationChannel(context: Context) {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = CHANNEL_DESC
            enableVibration(true)
            enableLights(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showStatusUpdateNotification(
        context: Context,
        ticketId: String,
        issueTitle: String,
        status: IssueStatus,
        milestoneTitle: String? = null
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ticketId", ticketId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            ticketId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusName = status.name
        val milestoneDetail = milestoneTitle?.takeIf { it.isNotBlank() } ?: "Milestone transition recorded"
        val contentText = "$ticketId: $milestoneDetail ($statusName)"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_report_alert)
            .setContentTitle("Issuegram: Status Updated to $statusName")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$issueTitle\n$contentText"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            val notifId = ((ticketId.hashCode() * 31 + status.ordinal) and 0x7FFFFFFF).coerceAtLeast(1)
            notificationManager.notify(notifId, builder.build())
        } catch (_: SecurityException) {
            // Permission might be denied on Android 13+, fail gracefully without crashing
        } catch (_: Exception) {
            // Graceful fallback
        }
    }
}
