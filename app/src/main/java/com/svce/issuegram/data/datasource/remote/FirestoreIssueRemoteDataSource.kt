package com.svce.issuegram.data.datasource.remote

import android.content.Context
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueMilestone
import com.svce.issuegram.data.model.IssueStatus
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreIssueRemoteDataSource(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : IssueRemoteDataSource {

    private val issuesCollection = firestore.collection("issues")

    override fun observeIssues(): Flow<List<Issue>> = callbackFlow {
        val registration = issuesCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val issues = snapshot.documents.mapNotNull { doc ->
                        mapDocumentToIssue(doc.id, doc.data ?: emptyMap())
                    }
                    trySend(issues)
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun fetchIssues(): List<Issue> {
        val snapshot = issuesCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            mapDocumentToIssue(doc.id, doc.data ?: emptyMap())
        }
    }

    override suspend fun fetchIssueById(ticketId: String): Issue? = try {
        val cleanTicketId = ticketId.removePrefix("#")
        // Try fetching by doc ID or ticketId field
        val doc = issuesCollection.document(cleanTicketId).get().await()
        if (doc.exists()) {
            mapDocumentToIssue(doc.id, doc.data ?: emptyMap())
        } else {
            val query = issuesCollection.whereEqualTo("ticketId", cleanTicketId).limit(1).get().await()
            query.documents.firstOrNull()?.let {
                mapDocumentToIssue(it.id, it.data ?: emptyMap())
            }
        }
    } catch (e: Exception) {
        null
    }

    override suspend fun postIssue(issue: Issue): Issue {
        val currentUserId = Firebase.auth.currentUser?.uid ?: "anonymous_user"
        val authorDisplayName = issue.authorName.takeIf { it.isNotBlank() && !it.contains("@") }
            ?: Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: "Civic Reporter"

        val ticketId = if (issue.ticketId.isBlank()) {
            "IG-${(1000..9999).random()}"
        } else {
            issue.ticketId.removePrefix("#")
        }

        var remotePhotoUrl = issue.photoUri

        // Upload photo to Firebase Storage if it's a local file/content URI
        if (!issue.photoUri.isNullOrBlank() &&
            !issue.photoUri.startsWith("http://") &&
            !issue.photoUri.startsWith("https://") &&
            !issue.photoUri.startsWith("drawable://")
        ) {
            try {
                val bytes = readPhotoBytes(issue.photoUri)
                if (bytes != null && bytes.isNotEmpty()) {
                    val storageRef = storage.reference.child("issue_photos/${UUID.randomUUID()}.jpg")
                    val uploadTask = storageRef.putBytes(bytes).await()
                    remotePhotoUrl = storageRef.downloadUrl.await().toString()
                }
            } catch (e: Exception) {
                // If storage upload fails (e.g. Firebase storage bucket not provisioned yet),
                // we retain the local photo URI so it renders immediately on the posting device!
                android.util.Log.w("FirestoreIssueRemoteDS", "Firebase storage upload failed: ${e.message}")
            }
        }

        val nowIso = Instant.now().toString()
        val defaultMilestones = listOf<Map<String, Any>>(
            mapOf<String, Any>(
                "id" to 1L,
                "stepOrder" to 1,
                "title" to "Issue Reported",
                "actor" to authorDisplayName,
                "timestamp" to nowIso,
                "isCompleted" to true
            ),
            mapOf<String, Any>(
                "id" to 2L,
                "stepOrder" to 2,
                "title" to "Acknowledged by Authority",
                "actor" to "Civic Control",
                "timestamp" to nowIso,
                "isCompleted" to false
            ),
            mapOf<String, Any>(
                "id" to 3L,
                "stepOrder" to 3,
                "title" to "Assigned to Department",
                "actor" to "Public Works",
                "timestamp" to nowIso,
                "isCompleted" to false
            ),
            mapOf<String, Any>(
                "id" to 4L,
                "stepOrder" to 4,
                "title" to "Work In Progress",
                "actor" to "Field Operations",
                "timestamp" to nowIso,
                "isCompleted" to false
            ),
            mapOf<String, Any>(
                "id" to 5L,
                "stepOrder" to 5,
                "title" to "Issue Resolved",
                "actor" to "Civic Authority",
                "timestamp" to nowIso,
                "isCompleted" to false
            )
        )

        val docData = hashMapOf<String, Any?>(
            "ticketId" to ticketId,
            "reporterId" to currentUserId,
            "authorName" to authorDisplayName,
            "authorUsername" to (issue.authorUsername ?: authorDisplayName),
            "title" to issue.title.trim(),
            "description" to (issue.description?.trim() ?: ""),
            "category" to issue.category.name,
            "status" to issue.status.name,
            "area" to issue.area,
            "locationName" to issue.locationName,
            "latitude" to issue.latitude,
            "longitude" to issue.longitude,
            "photoUri" to remotePhotoUrl,
            "likes" to issue.likes,
            "confirmedUserIds" to emptyList<String>(),
            "milestones" to defaultMilestones,
            "createdAt" to issue.createdAt.toString(),
            "updatedAt" to nowIso
        )

        val docRef = issuesCollection.document(ticketId)
        docRef.set(docData, SetOptions.merge()).await()

        return issue.copy(
            ticketId = ticketId,
            photoUri = remotePhotoUrl,
            isMine = true,
            authorName = authorDisplayName,
            reporterId = currentUserId
        )
    }

    override suspend fun toggleLike(ticketId: String) {
        val cleanTicketId = ticketId.removePrefix("#")
        val currentUserId = Firebase.auth.currentUser?.uid ?: return
        val docRef = issuesCollection.document(cleanTicketId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            if (snapshot.exists()) {
                @Suppress("UNCHECKED_CAST")
                val confirmedUsers = (snapshot.get("confirmedUserIds") as? List<String>) ?: emptyList()
                val hasLiked = confirmedUsers.contains(currentUserId)
                if (hasLiked) {
                    transaction.update(docRef, "likes", FieldValue.increment(-1))
                    transaction.update(docRef, "confirmedUserIds", FieldValue.arrayRemove(currentUserId))
                } else {
                    transaction.update(docRef, "likes", FieldValue.increment(1))
                    transaction.update(docRef, "confirmedUserIds", FieldValue.arrayUnion(currentUserId))
                }
            }
        }.await()
    }

    override suspend fun deleteIssue(ticketId: String): Result<Unit> = try {
        val cleanTicketId = ticketId.removePrefix("#")
        issuesCollection.document(cleanTicketId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue> = try {
        val cleanTicketId = ticketId.removePrefix("#")
        val docRef = issuesCollection.document(cleanTicketId)

        val updates = mutableMapOf<String, Any>(
            "title" to title.trim(),
            "updatedAt" to Instant.now().toString()
        )
        if (description != null) {
            updates["description"] = description.trim()
        }
        if (!locationName.isNullOrBlank()) {
            updates["locationName"] = locationName.trim()
        }

        docRef.update(updates).await()
        val updatedDoc = docRef.get().await()
        val issue = mapDocumentToIssue(updatedDoc.id, updatedDoc.data ?: emptyMap())
        if (issue != null) {
            Result.success(issue)
        } else {
            Result.failure(IllegalStateException("Failed to parse updated issue"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateStatus(
        ticketId: String,
        status: com.svce.issuegram.data.model.IssueStatus,
        milestones: List<IssueMilestone>
    ): Result<Unit> = try {
        val cleanTicketId = ticketId.removePrefix("#")
        val docRef = issuesCollection.document(cleanTicketId)
        val updateMap = mutableMapOf<String, Any>(
            "status" to status.name,
            "updatedAt" to Instant.now().toString()
        )
        if (milestones.isNotEmpty()) {
            updateMap["milestones"] = milestones.map { m ->
                mapOf(
                    "id" to m.id,
                    "stepOrder" to m.stepOrder,
                    "title" to m.title,
                    "actor" to m.actor,
                    "timestamp" to m.timestamp.toString(),
                    "isCompleted" to m.isCompleted
                )
            }
        }
        docRef.update(updateMap).await()

        // Also create a live notification document in Firestore for instant alerts across all devices
        try {
            val snapshot = docRef.get().await()
            val issueData = snapshot.data ?: emptyMap<String, Any?>()
            val reporterId = issueData["reporterId"] as? String ?: ""
            if (reporterId.isNotBlank()) {
                val issueTitle = issueData["title"] as? String ?: "Civic Issue"
                val activeMilestone = milestones.lastOrNull { it.isCompleted }
                val milestoneDetail = activeMilestone?.title ?: "Status changed to ${status.name}"

                val notifDoc = mapOf(
                    "ticketId" to ticketId,
                    "userId" to reporterId,
                    "title" to issueTitle,
                    "issueTitle" to issueTitle,
                    "status" to status.name,
                    "kind" to "Status",
                    "message" to "Status updated: $milestoneDetail",
                    "createdAt" to Instant.now().toString(),
                    "isRead" to false
                )
                firestore.collection("notifications").add(notifDoc).await()
            }
        } catch (_: Exception) {
            // Notification creation is best-effort alongside issue update
        }

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun mapDocumentToIssue(docId: String, data: Map<String, Any?>): Issue? = try {
        val currentUserId = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        val reporterId = data["reporterId"] as? String ?: ""
        val ticketId = data["ticketId"] as? String ?: docId

        val category = (data["category"] as? String)?.let { catStr ->
            IssueCategory.entries.firstOrNull { it.name.equals(catStr, ignoreCase = true) }
        } ?: IssueCategory.Other

        val status = (data["status"] as? String)?.let { statusStr ->
            IssueStatus.entries.firstOrNull { it.name.equals(statusStr, ignoreCase = true) }
        } ?: IssueStatus.Open

        val createdAtInstant = runCatching {
            Instant.parse(data["createdAt"] as? String)
        }.getOrNull() ?: Instant.now()

        @Suppress("UNCHECKED_CAST")
        val milestonesList = (data["milestones"] as? List<Map<String, Any?>>)?.mapIndexed { index, m ->
            val time = runCatching {
                Instant.parse(m["timestamp"] as? String)
            }.getOrNull() ?: createdAtInstant
            IssueMilestone(
                id = (m["id"] as? Number)?.toLong() ?: (index + 1).toLong(),
                stepOrder = (m["stepOrder"] as? Number)?.toInt() ?: (index + 1),
                title = m["title"] as? String ?: "",
                actor = m["actor"] as? String ?: "",
                timestamp = time,
                isCompleted = m["isCompleted"] as? Boolean ?: false
            )
        } ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val confirmedUserIds = (data["confirmedUserIds"] as? List<String>) ?: emptyList()

        Issue(
            ticketId = ticketId,
            title = data["title"] as? String ?: "Civic Issue",
            description = data["description"] as? String,
            category = category,
            status = status,
            area = data["area"] as? String ?: "",
            locationName = data["locationName"] as? String ?: "",
            authorName = data["authorName"] as? String ?: "Civic Reporter",
            createdAt = createdAtInstant,
            likes = (data["likes"] as? Number)?.toInt() ?: 0,
            isLikedByMe = currentUserId != null && confirmedUserIds.contains(currentUserId),
            photoUri = data["photoUri"] as? String,
            isMine = (currentUserId != null && reporterId == currentUserId),
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 12.9716,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 77.5946,
            milestones = milestonesList,
            authorUsername = data["authorUsername"] as? String,
            reporterId = reporterId
        )
    } catch (e: Exception) {
        null
    }

    private suspend fun readPhotoBytes(uriStr: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = if (uriStr.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { it.readBytes() }
            } else {
                val path = if (uriStr.startsWith("file://")) uriStr.removePrefix("file://") else uriStr
                File(path).readBytes()
            } ?: return@runCatching null

            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 1280) {
                sampleSize *= 2
            }

            val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                ?: return@runCatching rawBytes
            try {
                java.io.ByteArrayOutputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                    out.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }
}
