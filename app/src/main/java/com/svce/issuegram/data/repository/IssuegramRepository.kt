package com.svce.issuegram.data.repository

import android.content.Context
import android.net.Uri
import com.svce.issuegram.BuildConfig
import com.svce.issuegram.data.local.*
import com.svce.issuegram.data.model.*
import com.svce.issuegram.data.remote.*
import com.svce.issuegram.data.sync.UploadWorker
import com.svce.issuegram.domain.ReportRules
import com.svce.issuegram.domain.CivicActions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID

class IssuegramRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val sessionStore = SessionStore(context)
    private val local = LocalStore(context)
    private val media = MediaStore(context)
    private val api = SupabaseApiClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)
    private val auth = AuthManager(sessionStore, api).also { api.auth = it }
    private val json = api.json
    private val refreshMutex = Mutex()
    private val syncMutex = Mutex()
    private val onboardingMutex = Mutex()
    @Volatile private var activeChannel: String? = null
    private val messageLock = Any()
    private val remoteMessages = mutableMapOf<String, List<ChannelMessageDto>>()

    private val _currentUser = MutableStateFlow<UserProfileDto?>(null)
    val currentUser = _currentUser.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _issues = MutableStateFlow<List<CivicIssueDto>>(emptyList())
    val issues = _issues.asStateFlow()
    private val _ownIssues = MutableStateFlow<List<CivicIssueDto>>(emptyList())
    val ownIssues = _ownIssues.asStateFlow()
    @Volatile private var selectedIssueId: Long? = null
    private val _selectedIssue = MutableStateFlow<CivicIssueDto?>(null)
    val selectedIssue = _selectedIssue.asStateFlow()
    private val _channels = MutableStateFlow<List<ChannelDto>>(emptyList())
    val channels = _channels.asStateFlow()
    private val _staffChannels = MutableStateFlow<Set<String>>(emptySet())
    val staffChannels = _staffChannels.asStateFlow()
    private val _isModerator = MutableStateFlow(false)
    val isModerator = _isModerator.asStateFlow()
    private val _confirmedIssueIds = MutableStateFlow<Set<Long>>(emptySet())
    val confirmedIssueIds = _confirmedIssueIds.asStateFlow()
    private val _moderationFlags = MutableStateFlow<List<IssueFlagDto>>(emptyList())
    val moderationFlags = _moderationFlags.asStateFlow()
    private val _departmentConversations = MutableStateFlow<Map<String, List<ConversationDto>>>(emptyMap())
    val departmentConversations = _departmentConversations.asStateFlow()
    private val _messagesByConversation = MutableStateFlow<Map<String, List<ChannelMessageDto>>>(emptyMap())
    val messagesByConversation = _messagesByConversation.asStateFlow()
    @Volatile private var activeStaffConversationId: String? = null
    private val _messages = MutableStateFlow<Map<String, List<ChannelMessageDto>>>(emptyMap())
    val messagesByChannel = _messages.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()
    private val _pending = MutableStateFlow<List<OutboxEntry>>(emptyList())
    val pending = _pending.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        scope.launch {
            try {
                val savedSession = sessionStore.read()
                val profile = sessionStore.currentUserFlow.first()
                if (savedSession != null && profile?.id == savedSession.user.id) {
                    _currentUser.value = profile
                    loadCache(profile.id)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _error.value = "Your saved identity could not be opened. Please keep app data and try again." }
            finally { _ready.value = true }
            if (_currentUser.value != null) {
                UploadWorker.schedule(context)
                refreshAll()
            }
        }
    }

    private suspend fun <T> operation(block: suspend () -> T): T = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        _error.value = userMessage(e)
        throw e
    }

    fun dismissError() { _error.value = null }

    suspend fun completeOnboarding(name: String, generatedHandle: String, avatarUri: Uri?) = withContext(Dispatchers.IO) {
        onboardingMutex.withLock {
            operation {
                require(name.trim().length <= 36) { "Use a name of up to 36 characters." }
                val session = auth.begin()
                var profile = api.fetchOwnProfile()
                if (profile == null) {
                    var handle = generatedHandle.takeIf { it.matches(Regex("citizen_[a-f0-9]{12}")) } ?: ReportRules.randomUsername()
                    for (attempt in 0..4) {
                        try { profile = api.createProfile(session.user.id, handle, name.trim()); break }
                        catch (e: ApiException) {
                            if (e.serverCode != "23505") throw e
                            // A lost response may have committed our profile already; only look up our UUID.
                            profile = api.fetchOwnProfile()
                            if (profile != null) break
                            handle = ReportRules.randomUsername()
                        }
                    }
                }
                val created = checkNotNull(profile) { "Could not reserve a username. Please try again." }
                sessionStore.saveUser(created)
                _currentUser.value = created
                loadCache(created.id)
                if (avatarUri != null) queueAvatar(avatarUri)
                refreshAll()
            }
        }
    }

    private fun loadCache(userId: String) {
        fun <T> read(key: String, decode: (String) -> T): T? = local.cached("$userId:$key")?.let { runCatching { decode(it) }.getOrNull() }
        _issues.value = read("issues") { json.decodeFromString<List<CivicIssueDto>>(it) }.orEmpty()
        _ownIssues.value = read("own") { json.decodeFromString<List<CivicIssueDto>>(it) }.orEmpty()
        _channels.value = read("channels") { json.decodeFromString<List<ChannelDto>>(it) }.orEmpty()
        publishPending(userId)
    }

    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext
        refreshMutex.withLock {
            _isLoading.value = true
            try {
                val firstPage = api.fetchIssues()
                _issues.value = firstPage
                _hasMore.value = firstPage.size == 50
                local.cache("${user.id}:issues", json.encodeToString(firstPage))
                val mine = mutableListOf<CivicIssueDto>()
                do { val page = api.fetchIssues(mine.size, user.id); mine += page } while (page.size == 50)
                _ownIssues.value = mine
                local.cache("${user.id}:own", json.encodeToString(mine))
                _channels.value = api.fetchChannels()
                local.cache("${user.id}:channels", json.encodeToString(_channels.value))
                // Fail closed when staff configuration is absent. No citizen gets resolution controls.
                _staffChannels.value = try { api.fetchStaffChannels() } catch (e: CancellationException) { throw e } catch (_: ApiException) { emptySet() }
                _isModerator.value = try { api.fetchIsModerator() } catch (e: CancellationException) { throw e } catch (_: ApiException) { false }
                _confirmedIssueIds.value = try { api.fetchConfirmedIssueIds() } catch (e: CancellationException) { throw e } catch (_: ApiException) { emptySet() }
                if (_isModerator.value) {
                    _moderationFlags.value = try { api.fetchModerationFlags() } catch (e: CancellationException) { throw e } catch (_: ApiException) { emptyList() }
                } else {
                    _moderationFlags.value = emptyList()
                }
                activeChannel?.let {
                    loadMessagesForChannel(it)
                    if (it in _staffChannels.value) loadDepartmentConversations(it)
                }
                activeStaffConversationId?.let { loadStaffConversationMessages(it) }
                selectedIssueId?.let { id ->
                    val refreshed = api.fetchIssue(id)
                    if (selectedIssueId == id) applyReportResult(id, refreshed, user.id)
                }
                _error.value = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = userMessage(e) }
            finally { _isLoading.value = false }
        }
    }

    suspend fun loadMore() = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext
        refreshMutex.withLock {
            if (!_hasMore.value) return@withLock
            operation {
                val page = api.fetchIssues(_issues.value.size)
                _issues.value = (_issues.value + page).distinctBy { it.id }
                _hasMore.value = page.size == 50
                local.cache("${user.id}:issues", json.encodeToString(_issues.value))
            }
        }
    }

    suspend fun queueIssue(caption: String, category: String, locationName: String, subLocation: String,
                           latitude: Double, longitude: Double, capturedFile: File?, pickedUri: Uri?) = withContext(Dispatchers.IO) {
        operation {
            val user = requireNotNull(_currentUser.value) { "Your identity is still loading." }
            ReportRules.validate(caption, category, locationName, latitude, longitude)
            val id = UUID.randomUUID().toString()
            val photo = when {
                pickedUri != null -> media.prepare(pickedUri, id)
                capturedFile != null -> media.prepare(capturedFile, id)
                else -> error("Take or select a real photo before posting.")
            }
            try {
                val request = CreateIssueRequest(id, user.id, user.username, user.avatarUrl,
                    caption.trim().lineSequence().first().take(52), caption.trim(), category, locationName.trim(), subLocation,
                    latitude, longitude, false, emptyList())
                local.enqueue(OutboxEntry(id, user.id, "issue", json.encodeToString(request), photo.path))
            } catch (e: Exception) { photo.delete(); throw e }
            publishPending(user.id)
            UploadWorker.schedule(context)
            notices.emit("Report saved. It will upload when connected.")
        }
    }

    suspend fun queueAvatar(uri: Uri) = withContext(Dispatchers.IO) {
        operation {
            val user = requireNotNull(_currentUser.value)
            val id = UUID.randomUUID().toString()
            val photo = media.prepare(uri, id)
            try { local.enqueue(OutboxEntry(id, user.id, "avatar", "{}", photo.path)) }
            catch (e: Exception) { photo.delete(); throw e }
            publishPending(user.id)
            UploadWorker.schedule(context)
        }
    }

    suspend fun updateName(name: String) = withContext(Dispatchers.IO) {
        operation {
            require(name.trim().length <= 36) { "Use a name of up to 36 characters." }
            val updated = api.updateProfile(displayName = name.trim().ifBlank { requireNotNull(_currentUser.value).username })
            sessionStore.saveUser(updated); _currentUser.value = updated
        }
    }

    suspend fun removeAvatar() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            operation {
                val userId = requireNotNull(_currentUser.value).id
                val updated = api.updateProfile(clearAvatar = true)
                sessionStore.saveUser(updated); _currentUser.value = updated
                local.entries(userId).filter { it.kind == "avatar" }.forEach { entry ->
                    local.completed(entry.id); entry.mediaPath?.let { File(it).delete() }
                }
                publishPending(userId)
                notices.emit("Profile photo removed.")
            }
        }
    }

    suspend fun openIssue(id: Long?) = withContext(Dispatchers.IO) {
        selectedIssueId = id
        _selectedIssue.value = id?.let { target -> (_ownIssues.value + _issues.value).firstOrNull { it.id == target } }
        if (id != null) refreshMutex.withLock {
            if (selectedIssueId == id) operation {
                val fresh = api.fetchIssue(id)
                if (selectedIssueId == id) applyReportResult(id, fresh, requireNotNull(_currentUser.value).id)
            }
        }
    }

    private fun cacheIssueLists(userId: String) {
        local.cache("$userId:issues", json.encodeToString(_issues.value))
        local.cache("$userId:own", json.encodeToString(_ownIssues.value))
    }

    private fun applyReportResult(id: Long, report: CivicIssueDto?, userId: String) {
        fun update(reports: List<CivicIssueDto>) = if (report == null) reports.filterNot { it.id == id }
            else reports.map { if (it.id == id) report else it }
        _issues.value = update(_issues.value); _ownIssues.value = update(_ownIssues.value)
        if (selectedIssueId == id) _selectedIssue.value = report
        cacheIssueLists(userId)
    }

    suspend fun editIssue(issue: CivicIssueDto, edit: EditIssueRequest) = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            operation {
                val user = requireNotNull(_currentUser.value)
                CivicActions.requireOwner(issue, user.id)
                val validated = CivicActions.edit(edit.caption, edit.category, edit.locationName, edit.subLocation, edit.latitude, edit.longitude)
                val updated = try { api.editIssue(issue, validated).copy(milestones = issue.milestones) }
                catch (conflict: ReportConflictException) {
                    applyReportResult(issue.id, api.fetchIssue(issue.id), user.id)
                    throw conflict
                }
                applyReportResult(issue.id, updated, user.id)
                notices.emit("Report updated.")
            }
        }
    }

    suspend fun deleteIssue(issue: CivicIssueDto) = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            operation {
                val user = requireNotNull(_currentUser.value)
                CivicActions.requireOwner(issue, user.id)
                try { api.deleteIssue(issue) }
                catch (conflict: ReportConflictException) {
                    applyReportResult(issue.id, api.fetchIssue(issue.id), user.id)
                    throw conflict
                }
                applyReportResult(issue.id, null, user.id)
                if (selectedIssueId == issue.id) selectedIssueId = null
                notices.emit("Report deleted.")
            }
        }
    }

    suspend fun flagIssue(issue: CivicIssueDto, reason: String) = withContext(Dispatchers.IO) {
        operation { api.flagIssue(issue, reason); notices.emit("Report sent for review. Thank you.") }
    }

    suspend fun toggleConfirmIssue(issue: CivicIssueDto) = withContext(Dispatchers.IO) {
        operation {
            val currentlyConfirmed = issue.id in _confirmedIssueIds.value
            val nextConfirmed = !currentlyConfirmed
            api.confirmIssue(issue.id, nextConfirmed)
            _confirmedIssueIds.value = if (nextConfirmed) _confirmedIssueIds.value + issue.id else _confirmedIssueIds.value - issue.id
            val fresh = api.fetchIssue(issue.id)
            val userId = requireNotNull(_currentUser.value).id
            if (fresh != null) applyReportResult(issue.id, fresh, userId)
            notices.emit(if (nextConfirmed) "Issue confirmed." else "Confirmation removed.")
        }
    }

    suspend fun reviewFlag(flagId: Long, status: String, notes: String? = null) = withContext(Dispatchers.IO) {
        operation {
            check(_isModerator.value) { "Only authorized moderators can review flags." }
            api.reviewModerationFlag(flagId, status, notes)
            _moderationFlags.value = api.fetchModerationFlags()
            notices.emit("Moderation review saved.")
        }
    }

    suspend fun toggleIssueResolution(issue: CivicIssueDto) = withContext(Dispatchers.IO) {
        operation {
            val authorized = _channels.value.any { it.category == issue.category && it.id in _staffChannels.value }
            check(authorized) { "Only assigned staff can change this issue's status." }
            api.updateIssueStatus(issue.id, !issue.isResolved)
            refreshAll()
        }
    }

    suspend fun openChannel(channelId: String?) = withContext(Dispatchers.IO) {
        activeChannel = channelId
        if (channelId == null) { activeStaffConversationId = null; return@withContext }
        val userId = _currentUser.value?.id ?: return@withContext
        local.cached("$userId:messages:$channelId")?.let { raw ->
            synchronized(messageLock) {
                remoteMessages.putIfAbsent(channelId, runCatching { json.decodeFromString<List<ChannelMessageDto>>(raw) }.getOrDefault(emptyList()))
            }
        }
        publishPending(userId)
        try {
            loadMessagesForChannel(channelId)
            if (channelId in _staffChannels.value) loadDepartmentConversations(channelId)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { _error.value = userMessage(e) }
    }

    private suspend fun loadDepartmentConversations(channelId: String) {
        val threads = api.fetchDepartmentConversations(channelId)
        _departmentConversations.value = _departmentConversations.value + (channelId to threads)
    }

    suspend fun openStaffConversation(conversationId: String?) = withContext(Dispatchers.IO) {
        activeStaffConversationId = conversationId
        if (conversationId == null) return@withContext
        try { loadStaffConversationMessages(conversationId) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { _error.value = userMessage(e) }
    }

    private suspend fun loadStaffConversationMessages(conversationId: String) {
        val msgs = api.fetchConversationMessages(conversationId)
        _messagesByConversation.value = _messagesByConversation.value + (conversationId to msgs)
    }

    suspend fun sendStaffReply(channelId: String, conversationId: String, text: String) = withContext(Dispatchers.IO) {
        operation {
            val user = requireNotNull(_currentUser.value)
            check(channelId in _staffChannels.value) { "Only assigned department staff can reply to department threads." }
            require(text.trim().length in 1..2000) { "Messages must contain 1–2000 characters." }
            val reqId = UUID.randomUUID().toString()
            val sent = api.sendChannelMessage(
                SendMessageRequest(reqId, conversationId, channelId, user.id, user.username, text.trim(), Instant.now().toString(), isFromAdmin = true)
            )
            val updated = (_messagesByConversation.value[conversationId].orEmpty() + sent).distinctBy { it.id }
            _messagesByConversation.value = _messagesByConversation.value + (conversationId to updated)
        }
    }

    private suspend fun loadMessagesForChannel(channelId: String) {
        val user = _currentUser.value ?: return
        val messages = api.fetchChannelMessages(channelId)
        synchronized(messageLock) { remoteMessages[channelId] = messages }
        local.cache("${user.id}:messages:$channelId", json.encodeToString(messages))
        publishPending(user.id)
    }

    suspend fun sendMessage(channelId: String, text: String) = withContext(Dispatchers.IO) {
        operation {
            val user = requireNotNull(_currentUser.value)
            require(text.trim().length in 1..2000) { "Messages must contain 1–2000 characters." }
            val id = UUID.randomUUID().toString()
            val request = QueuedMessage(channelId, text.trim(), user.username, Instant.now().toString())
            local.enqueue(OutboxEntry(id, user.id, "message", json.encodeToString(request)))
            publishPending(user.id)
            UploadWorker.schedule(context)
        }
    }

    private fun publishPending(userId: String) = synchronized(messageLock) {
        val entries = local.entries(userId)
        _pending.value = entries
        val merged = remoteMessages.toMutableMap()
        entries.filter { it.kind == "message" }.groupBy { json.decodeFromString<QueuedMessage>(it.payload).channelId }.forEach { (channel, pending) ->
            val deliveredIds = merged[channel].orEmpty().mapNotNull { it.requestId }.toSet()
            merged[channel] = merged[channel].orEmpty() + pending.filter { it.id !in deliveredIds }.map { entry ->
                val msg = json.decodeFromString<QueuedMessage>(entry.payload)
                ChannelMessageDto(id = -entry.id.hashCode().toLong().let { kotlin.math.abs(it).coerceAtLeast(1) }, channelId = channel,
                    senderId = userId, senderUsername = msg.username, message = msg.message, timestampLabel = msg.createdAt,
                    requestId = entry.id, delivery = if (entry.failed) DeliveryState.FAILED else DeliveryState.QUEUED)
            }
        }
        _messages.value = merged
    }

    /** Returns false only for transient failures; completed entries never upload twice. */
    suspend fun syncOutbox(): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val session = sessionStore.read() ?: return@withLock true
            for (entry in local.entries(session.user.id).filterNot { it.failed }) {
                try {
                    when (entry.kind) {
                        "issue" -> {
                            val photo = File(requireNotNull(entry.mediaPath))
                            check(photo.isFile) { "The saved photo is missing. Please create a new report." }
                            val url = api.uploadFile("issue-media", entry.id, photo.readBytes())
                            val request = json.decodeFromString<CreateIssueRequest>(entry.payload)
                            api.createIssue(request.copy(mediaUrls = listOf(url)))
                        }
                        "message" -> {
                            val msg = json.decodeFromString<QueuedMessage>(entry.payload)
                            val thread = api.conversation(msg.channelId)
                            val sent = api.sendChannelMessage(SendMessageRequest(entry.id, thread, msg.channelId, entry.userId, msg.username, msg.message, msg.createdAt))
                            synchronized(messageLock) {
                                val updated = (remoteMessages[msg.channelId].orEmpty() + sent).distinctBy { it.id }
                                remoteMessages[msg.channelId] = updated
                                local.cache("${entry.userId}:messages:${msg.channelId}", json.encodeToString(updated))
                            }
                        }
                        "avatar" -> {
                            val photo = File(requireNotNull(entry.mediaPath))
                            check(photo.isFile) { "The saved profile photo is missing. Please select it again." }
                            val url = api.uploadFile("avatars", entry.id, photo.readBytes())
                            val user = api.updateProfile(avatarUrl = url)
                            sessionStore.saveUser(user); _currentUser.value = user
                        }
                        else -> error("Unsupported pending upload.")
                    }
                    local.completed(entry.id)
                    entry.mediaPath?.let { File(it).delete() }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (e is IOException && (e !is ApiException || e.retryable)) {
                        publishPending(session.user.id)
                        return@withLock false
                    }
                    local.failed(entry.id, userMessage(e))
                }
            }
            publishPending(session.user.id)
            if (_currentUser.value != null) refreshAll()
            true
        }
    }

    suspend fun retryUploads() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val id = _currentUser.value?.id ?: return@withLock
            local.retry(id); publishPending(id); UploadWorker.schedule(context)
        }
    }

    suspend fun discardFailedUploads() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val id = _currentUser.value?.id ?: return@withLock
            local.entries(id).filter { it.failed }.forEach { entry ->
                local.completed(entry.id)
                entry.mediaPath?.let { File(it).delete() }
            }
            publishPending(id)
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    suspend fun observeWhileVisible() = coroutineScope {
        launch {
            while (isActive) { if (_currentUser.value != null) refreshAll(); delay(30_000) }
        }
        var backoff = 2_000L
        while (isActive) {
            if (_currentUser.value == null) { delay(1000); continue }
            try {
                api.observeRealtimeChanges().debounce(500).collect { refreshAll(); backoff = 2_000 }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The foreground poll remains a fallback during reconnect. */ }
            delay(backoff); backoff = (backoff * 2).coerceAtMost(30_000)
        }
    }

    companion object {
        // The singleton stores only applicationContext, never an Activity or a screen.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile private var instance: IssuegramRepository? = null
        fun get(context: Context): IssuegramRepository = instance ?: synchronized(this) {
            instance ?: IssuegramRepository(context.applicationContext).also { instance = it }
        }
        fun userMessage(error: Exception): String = when (error) {
            is ApiException -> error.message ?: "The service could not complete this action."
            is IOException -> "Couldn't connect. Your saved reports and messages are kept for retry."
            is IllegalArgumentException, is IllegalStateException -> error.message ?: "Please try again."
            else -> "This action couldn't be completed. Please try again."
        }
    }
}

@Serializable
private data class QueuedMessage(val channelId: String, val message: String, val username: String, val createdAt: String)
