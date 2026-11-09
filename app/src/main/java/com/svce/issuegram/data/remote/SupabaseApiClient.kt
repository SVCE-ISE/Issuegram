package com.svce.issuegram.data.remote

import com.svce.issuegram.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val status: Int, val serverCode: String?, message: String) : IOException(message) {
    val retryable get() = status == 408 || status == 429 || status >= 500
}

class ReportConflictException(message: String) : IllegalStateException(message)

/** Public key identifies the app; the bearer token identifies its current user. */
class SupabaseApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS).build(),
) : AuthGateway {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    lateinit var auth: AuthManager
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val body = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            val error = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                            val code = error?.get("error_code")?.jsonPrimitive?.contentOrNull
                                ?: error?.get("code")?.jsonPrimitive?.contentOrNull
                            val message = when {
                                code == "anonymous_provider_disabled" -> "Anonymous access is not enabled yet. Please try again later."
                                it.code == 401 -> "Your session could not be renewed. Your saved identity has been kept."
                                it.code == 403 -> "You don't have permission for this action."
                                it.code == 404 || code == "PGRST205" || code == "42703" -> "The service is being updated. Please try again later."
                                it.code == 429 -> "Too many requests. Please try again shortly."
                                it.code >= 500 -> "The service is temporarily unavailable. Please try again."
                                else -> "The server could not save this change (${it.code})."
                            }
                            throw ApiException(it.code, code, message)
                        }
                        if (cont.isActive) cont.resume(body)
                    } catch (e: Exception) { if (cont.isActive) cont.resumeWithException(e) }
                }
            }
        })
    }

    private suspend fun call(path: String, method: String = "GET", payload: String? = null, prefer: String? = null, authenticated: Boolean = true): String {
        suspend fun attempt(force: Boolean): String {
            val builder = Request.Builder().url(baseUrl.trimEnd('/') + path).header("apikey", apiKey)
            if (authenticated) builder.header("Authorization", "Bearer ${auth.session(force).accessToken}")
            prefer?.let { builder.header("Prefer", it) }
            builder.method(method, if (method == "GET" || method == "HEAD") null else (payload ?: "{}").toRequestBody(mediaType))
            return execute(builder.build())
        }
        return try { attempt(false) } catch (e: ApiException) {
            if (e.status != 401 || !authenticated) throw e
            attempt(true)
        }
    }

    override suspend fun createAnonymousSession(): AuthSession = try {
        json.decodeFromString(call("/auth/v1/signup", "POST", "{}", authenticated = false))
    } catch (e: ApiException) {
        if (e.serverCode != "anonymous_provider_disabled") throw e
        json.decodeFromString(call("/functions/v1/anonymous-session", "POST", "{}", authenticated = false))
    }
    override suspend fun refreshSession(refreshToken: String): AuthSession = json.decodeFromString(
        call("/auth/v1/token?grant_type=refresh_token", "POST", buildJsonObject { put("refresh_token", refreshToken) }.toString(), authenticated = false)
    )

    suspend fun fetchOwnProfile(): UserProfileDto? {
        val id = auth.session().user.id
        return json.decodeFromString<List<UserProfileDto>>(call("/rest/v1/profiles?id=eq.$id&limit=1")).firstOrNull()
    }

    suspend fun createProfile(id: String, username: String, displayName: String): UserProfileDto {
        require(id == auth.session().user.id)
        return json.decodeFromString<List<UserProfileDto>>(call("/rest/v1/profiles", "POST",
            json.encodeToString(CreateProfileRequest(id, username, displayName.ifBlank { username })), "return=representation")).single()
    }

    suspend fun updateProfile(displayName: String? = null, avatarUrl: String? = null, clearAvatar: Boolean = false): UserProfileDto {
        val id = auth.session().user.id
        val body = buildJsonObject {
            displayName?.let { put("display_name", it) }
            if (clearAvatar) put("avatar_url", JsonNull) else avatarUrl?.let { put("avatar_url", it) }
        }
        return json.decodeFromString<List<UserProfileDto>>(call("/rest/v1/profiles?id=eq.$id", "PATCH", body.toString(), "return=representation")).single()
    }

    suspend fun fetchIssues(offset: Int = 0, authorId: String? = null): List<CivicIssueDto> {
        val filter = authorId?.let { "&author_id=eq.$it" }.orEmpty()
        return json.decodeFromString<List<CivicIssueDto>>(call("/rest/v1/issues?select=*,issue_milestones(*)&order=created_at.desc,id.desc&limit=50&offset=$offset$filter"))
            .map { it.copy(milestones = it.milestones.sortedBy { step -> step.stepOrder }) }
    }

    suspend fun createIssue(payload: CreateIssueRequest): CivicIssueDto {
        val body = call("/rest/v1/issues?on_conflict=request_id", "POST", json.encodeToString(payload), "resolution=ignore-duplicates,return=representation")
        return json.decodeFromString<List<CivicIssueDto>>(body).firstOrNull()
            ?: json.decodeFromString<List<CivicIssueDto>>(call("/rest/v1/issues?request_id=eq.${payload.requestId}&limit=1")).single()
    }

    suspend fun updateIssueStatus(issueId: Long, resolved: Boolean) {
        val response = call("/rest/v1/issues?id=eq.$issueId", "PATCH", json.encodeToString(UpdateIssueStatusRequest(
            if (resolved) "RESOLVED" else "IN_PROGRESS")), "return=representation")
        check(json.decodeFromString<List<CivicIssueDto>>(response).size == 1) { "This issue could not be updated. Refresh and try again." }
    }

    suspend fun fetchIssue(id: Long): CivicIssueDto? = json.decodeFromString<List<CivicIssueDto>>(
        call("/rest/v1/issues?id=eq.$id&select=*,issue_milestones(*)&limit=1")
    ).firstOrNull()

    private suspend fun ownedIssueFilter(issue: CivicIssueDto): String {
        val userId = auth.session().user.id
        com.svce.issuegram.domain.CivicActions.requireOwner(issue, userId)
        val revision = issue.updatedAt?.let { "&updated_at=eq.${encoded(it)}" }.orEmpty()
        return "id=eq.${issue.id}&author_id=eq.$userId$revision"
    }

    suspend fun editIssue(issue: CivicIssueDto, edit: EditIssueRequest): CivicIssueDto {
        val response = call("/rest/v1/issues?${ownedIssueFilter(issue)}", "PATCH", json.encodeToString(edit), "return=representation")
        return json.decodeFromString<List<CivicIssueDto>>(response).singleOrNull()
            ?: throw ReportConflictException("This report changed or is no longer available. Review the latest version before saving.")
    }

    suspend fun deleteIssue(issue: CivicIssueDto) {
        val response = call("/rest/v1/issues?${ownedIssueFilter(issue)}", "DELETE", prefer = "return=representation")
        val deleted = json.decodeFromString<List<CivicIssueDto>>(response)
        if (deleted.size != 1 && fetchIssue(issue.id) != null)
            throw ReportConflictException("This report changed or could not be deleted. Refresh it and try again.")
    }

    suspend fun flagIssue(issue: CivicIssueDto, reason: String) {
        require(reason in com.svce.issuegram.domain.CivicActions.flagReasons)
        val userId = auth.session().user.id
        require(issue.authorId != userId) { "Use edit or delete for your own report." }
        val body = buildJsonObject { put("issue_id", issue.id); put("reporter_id", userId); put("reason", reason) }
        call("/rest/v1/issue_flags?on_conflict=issue_id,reporter_id", "POST", body.toString(), "resolution=ignore-duplicates,return=minimal")
    }

    suspend fun fetchStaffChannels(): Set<String> {
        val id = auth.session().user.id
        return json.decodeFromString<List<StaffMembershipDto>>(call("/rest/v1/staff_members?user_id=eq.$id&select=channel_id")).map { it.channelId }.toSet()
    }

    suspend fun fetchIsModerator(): Boolean {
        val id = auth.session().user.id
        return json.decodeFromString<List<ModeratorDto>>(call("/rest/v1/moderators?user_id=eq.$id&select=user_id&limit=1")).isNotEmpty()
    }

    suspend fun fetchConfirmedIssueIds(): Set<Long> {
        val id = auth.session().user.id
        return json.decodeFromString<List<IssueConfirmationDto>>(call("/rest/v1/issue_confirmations?user_id=eq.$id&select=issue_id")).map { it.issueId }.toSet()
    }

    suspend fun confirmIssue(issueId: Long, confirmed: Boolean) {
        val id = auth.session().user.id
        if (confirmed) {
            val body = buildJsonObject { put("issue_id", issueId); put("user_id", id) }.toString()
            call("/rest/v1/issue_confirmations?on_conflict=issue_id,user_id", "POST", body, "resolution=ignore-duplicates,return=minimal")
        } else {
            call("/rest/v1/issue_confirmations?issue_id=eq.$issueId&user_id=eq.$id", "DELETE", prefer = "return=minimal")
        }
    }

    suspend fun fetchModerationFlags(): List<IssueFlagDto> = json.decodeFromString(
        call("/rest/v1/issue_flags?select=*&order=created_at.desc,id.desc&limit=100")
    )

    suspend fun reviewModerationFlag(flagId: Long, status: String, notes: String? = null) {
        val body = buildJsonObject {
            put("moderation_status", status)
            notes?.takeIf { it.isNotBlank() }?.let { put("review_notes", it.trim().take(500)) }
        }.toString()
        call("/rest/v1/issue_flags?id=eq.$flagId", "PATCH", body, "return=representation")
    }

    suspend fun fetchDepartmentConversations(channelId: String): List<ConversationDto> = json.decodeFromString(
        call("/rest/v1/conversations?channel_id=eq.${encoded(channelId)}&select=id,user_id,channel_id,created_at&order=created_at.desc&limit=50")
    )

    suspend fun fetchConversationMessages(conversationId: String): List<ChannelMessageDto> {
        val latest = json.decodeFromString<List<ChannelMessageDto>>(call(
            "/rest/v1/channel_messages?conversation_id=eq.${encoded(conversationId)}&order=created_at.desc,id.desc&limit=100"))
        return latest.reversed()
    }

    suspend fun fetchChannels(): List<ChannelDto> = json.decodeFromString<List<ChannelDto>>(call("/rest/v1/channels?select=*&order=sort_order.asc"))
        // A department's shared preview/unread metadata must never masquerade as this user's chat.
        .map { it.copy(lastMessage = "Contact this department", lastTimestampLabel = "", unreadCount = 0) }

    suspend fun conversation(channelId: String): String {
        val userId = auth.session().user.id
        val body = buildJsonObject { put("user_id", userId); put("channel_id", channelId) }.toString()
        val created = json.decodeFromString<List<ConversationDto>>(call("/rest/v1/conversations?on_conflict=user_id,channel_id", "POST", body, "resolution=ignore-duplicates,return=representation"))
        return created.firstOrNull()?.id ?: json.decodeFromString<List<ConversationDto>>(call(
            "/rest/v1/conversations?user_id=eq.$userId&channel_id=eq.${encoded(channelId)}&limit=1")).single().id
    }

    suspend fun fetchChannelMessages(channelId: String): List<ChannelMessageDto> {
        val thread = conversation(channelId)
        val latest = json.decodeFromString<List<ChannelMessageDto>>(call(
            "/rest/v1/channel_messages?conversation_id=eq.$thread&order=created_at.desc,id.desc&limit=100"))
        return latest.reversed()
    }

    suspend fun sendChannelMessage(payload: SendMessageRequest): ChannelMessageDto {
        val body = call("/rest/v1/channel_messages?on_conflict=request_id", "POST", json.encodeToString(payload), "resolution=ignore-duplicates,return=representation")
        return json.decodeFromString<List<ChannelMessageDto>>(body).firstOrNull()
            ?: json.decodeFromString<List<ChannelMessageDto>>(call("/rest/v1/channel_messages?request_id=eq.${payload.requestId}&limit=1")).single()
    }

    suspend fun uploadFile(bucket: String, requestId: String, bytes: ByteArray): String {
        val session = auth.session()
        val path = "${session.user.id}/$requestId.jpg"
        suspend fun upload(force: Boolean) = execute(Request.Builder()
            .url("${baseUrl.trimEnd('/')}/storage/v1/object/$bucket/$path")
            .header("apikey", apiKey).header("Authorization", "Bearer ${auth.session(force).accessToken}")
            .header("x-upsert", "true").post(bytes.toRequestBody("image/jpeg".toMediaType())).build())
        try { upload(false) } catch (e: ApiException) { if (e.status == 401) upload(true) else throw e }
        return "${baseUrl.trimEnd('/')}/storage/v1/object/public/$bucket/$path"
    }

    suspend fun observeRealtimeChanges(): Flow<String> {
        val token = auth.session().accessToken
        return callbackFlow {
            val url = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://").trimEnd('/') +
                "/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"
            val socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(buildJsonObject {
                        put("topic", "realtime:issuegram"); put("event", "phx_join"); put("ref", "join")
                        putJsonObject("payload") {
                            put("access_token", token)
                            putJsonObject("config") { putJsonArray("postgres_changes") {
                                listOf("issues", "issue_milestones", "channel_messages").forEach { table -> add(buildJsonObject {
                                    put("event", "*"); put("schema", "public"); put("table", table)
                                }) }
                            } }
                        }
                    }.toString())
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val frame = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                    val event = frame["event"]?.jsonPrimitive?.contentOrNull
                    if (event == "postgres_changes") trySend(text)
                    if (event == "phx_error" || (event == "phx_reply" && frame["payload"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull == "error"))
                        close(IOException("Live updates disconnected"))
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(t) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close(IOException("Live updates closed")) }
            })
            val heartbeat = launch {
                var ref = 0
                while (true) {
                    delay(20_000)
                    socket.send("{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":\"hb${ref++}\"}")
                    val refreshedToken = auth.session().accessToken
                    socket.send(buildJsonObject {
                        put("topic", "realtime:issuegram"); put("event", "access_token"); put("ref", "token")
                        putJsonObject("payload") { put("access_token", refreshedToken) }
                    }.toString())
                }
            }
            awaitClose { heartbeat.cancel(); socket.cancel() }
        }
    }

    private fun encoded(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
}
