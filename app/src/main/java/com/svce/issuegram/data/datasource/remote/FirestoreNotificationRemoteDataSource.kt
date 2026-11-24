package com.svce.issuegram.data.datasource.remote

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.svce.issuegram.IssuegramApplication
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.domain.model.AlertKind
import com.svce.issuegram.domain.model.Notification
import kotlinx.coroutines.tasks.await
import java.time.Instant

class FirestoreNotificationRemoteDataSource(
    private val context: Context = IssuegramApplication.instance,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : NotificationRemoteDataSource {

    private val prefs by lazy {
        context.getSharedPreferences("issuegram_read_notifications", Context.MODE_PRIVATE)
    }

    private val readIds: MutableSet<String> by lazy {
        prefs.getStringSet("read_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    override suspend fun fetchNotifications(): List<Notification> {
        return try {
            val currentUserId = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

            // 1. Fetch user-specific notifications from notifications collection if available
            val explicitNotifications = mutableListOf<Notification>()
            if (currentUserId != null) {
                val notifSnapshot = firestore.collection("notifications")
                    .whereEqualTo("userId", currentUserId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                for (doc in notifSnapshot.documents) {
                    val data = doc.data ?: continue
                    val id = doc.id
                    val time = runCatching {
                        Instant.parse(data["createdAt"] as? String)
                    }.getOrNull() ?: Instant.now()

                    val status = (data["status"] as? String)?.let { s ->
                        IssueStatus.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
                    } ?: IssueStatus.Open

                    val kind = (data["kind"] as? String)?.let { k ->
                        AlertKind.entries.firstOrNull { it.name.equals(k, ignoreCase = true) }
                    } ?: AlertKind.Status

                    val isRead = (data["isRead"] as? Boolean == true) || readIds.contains(id)

                    explicitNotifications.add(
                        Notification(
                            id = id,
                            ticketId = data["ticketId"] as? String ?: "",
                            issueTitle = data["issueTitle"] as? String ?: (data["title"] as? String ?: "Notification"),
                            kind = kind,
                            status = status,
                            message = data["message"] as? String ?: "",
                            createdAt = time,
                            isUnread = !isRead
                        )
                    )
                }
            }

            if (explicitNotifications.isNotEmpty()) {
                return explicitNotifications
            }

            // 2. Fallback: Generate notifications from milestones in the user's or public issues
            val issuesSnapshot = firestore.collection("issues")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()

            val milestoneNotifications = mutableListOf<Notification>()
            for (doc in issuesSnapshot.documents) {
                val data = doc.data ?: continue
                val ticketId = data["ticketId"] as? String ?: doc.id
                val issueTitle = data["title"] as? String ?: "Issue"
                val issueStatus = (data["status"] as? String)?.let { s ->
                    IssueStatus.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
                } ?: IssueStatus.Open

                @Suppress("UNCHECKED_CAST")
                val milestones = (data["milestones"] as? List<Map<String, Any?>>) ?: emptyList()

                milestones.forEach { m ->
                    val mId = (m["id"] as? Number)?.toLong() ?: 1L
                    val notifId = "milestone_${ticketId}_$mId"
                    val time = runCatching {
                        Instant.parse(m["timestamp"] as? String)
                    }.getOrNull() ?: Instant.now()

                    val mTitle = m["title"] as? String ?: ""
                    val mActor = m["actor"] as? String ?: ""

                    val mStatus = if (mTitle.contains("resolved", ignoreCase = true)) {
                        IssueStatus.Resolved
                    } else if (m["stepOrder"] == 1) {
                        IssueStatus.Open
                    } else {
                        issueStatus
                    }

                    milestoneNotifications.add(
                        Notification(
                            id = notifId,
                            ticketId = ticketId,
                            issueTitle = issueTitle,
                            kind = AlertKind.Status,
                            status = mStatus,
                            message = if (mActor.isNotBlank()) "$mTitle - $mActor" else mTitle,
                            createdAt = time,
                            isUnread = !readIds.contains(notifId)
                        )
                    )
                }
            }

            milestoneNotifications
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun markAllAsRead() {
        val currentList = fetchNotifications()
        val newReadIds = currentList.map { it.id }.toSet()
        readIds.addAll(newReadIds)
        prefs.edit().putStringSet("read_ids", readIds).apply()

        // Also mark server documents as read if user is logged in
        val currentUserId = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (currentUserId != null) {
            try {
                val unreadDocs = firestore.collection("notifications")
                    .whereEqualTo("userId", currentUserId)
                    .whereEqualTo("isRead", false)
                    .get()
                    .await()
                for (doc in unreadDocs.documents) {
                    doc.reference.update("isRead", true)
                }
            } catch (_: Exception) {
                // Best effort
            }
        }
    }
}
