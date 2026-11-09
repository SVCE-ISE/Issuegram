package com.svce.issuegram.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.data.model.EditIssueRequest
import com.svce.issuegram.data.repository.IssuegramRepository
import com.svce.issuegram.domain.ReportRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class IssuegramViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = IssuegramRepository.get(application)
    val currentUser = repository.currentUser
    val ready = repository.ready
    val issues = repository.issues
    val ownIssues = repository.ownIssues
    val selectedIssue = repository.selectedIssue
    val channels = repository.channels
    val staffChannels = repository.staffChannels
    val isModerator = repository.isModerator
    val confirmedIssueIds = repository.confirmedIssueIds
    val moderationFlags = repository.moderationFlags
    val departmentConversations = repository.departmentConversations
    val messagesByConversation = repository.messagesByConversation
    val messagesByChannel = repository.messagesByChannel
    val isLoading = repository.isLoading
    val hasMore = repository.hasMore
    val pending = repository.pending
    val error = repository.error
    val notices = repository.notices
    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading = _isAuthLoading.asStateFlow()
    private val _isPostingIssue = MutableStateFlow(false)
    val isPostingIssue = _isPostingIssue.asStateFlow()
    private val _isMutating = MutableStateFlow(false)
    val isMutating = _isMutating.asStateFlow()
    private val _isProfileBusy = MutableStateFlow(false)
    val isProfileBusy = _isProfileBusy.asStateFlow()
    private val _isOpeningIssue = MutableStateFlow(false)
    val isOpeningIssue = _isOpeningIssue.asStateFlow()
    private var observation: Job? = null
    private var openChat: Job? = null
    private var openReport: Job? = null
    private val _sendingMessage = MutableStateFlow(false)
    val sendingMessage = _sendingMessage.asStateFlow()
    private var reportRequest = 0

    fun generateRandomUsername() = ReportRules.randomUsername()
    fun dismissError() = repository.dismissError()
    fun setForeground(visible: Boolean) {
        observation?.cancel()
        observation = if (visible) viewModelScope.launch { repository.observeWhileVisible() } else null
    }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Repository publishes actionable errors. */ }
    }
    fun completeOnboarding(name: String, generatedUsername: String, avatarUri: Uri?) {
        if (_isAuthLoading.value) return
        action {
            _isAuthLoading.value = true
            try { repository.completeOnboarding(name, generatedUsername, avatarUri) }
            finally { _isAuthLoading.value = false }
        }
    }
    private fun profileAction(block: suspend () -> Unit) {
        if (_isProfileBusy.value) return
        _isProfileBusy.value = true
        action { try { block() } finally { _isProfileBusy.value = false } }
    }
    fun updateAvatar(uri: Uri) { profileAction { repository.queueAvatar(uri) } }
    fun removeAvatar() { profileAction { repository.removeAvatar() } }
    fun updateName(name: String, onSaved: () -> Unit) { profileAction { repository.updateName(name); onSaved() } }
    fun openIssue(id: Long?) {
        openReport?.cancel()
        val request = ++reportRequest
        _isOpeningIssue.value = id != null
        openReport = action { try { repository.openIssue(id) } finally { if (reportRequest == request) _isOpeningIssue.value = false } }
    }
    private fun mutate(block: suspend () -> Unit) {
        if (_isMutating.value) return
        _isMutating.value = true
        action { try { block() } finally { _isMutating.value = false } }
    }
    fun editIssue(issue: CivicIssueDto, edit: EditIssueRequest, onSaved: () -> Unit) { mutate { repository.editIssue(issue, edit); onSaved() } }
    fun deleteIssue(issue: CivicIssueDto, onDeleted: () -> Unit) { mutate { repository.deleteIssue(issue); onDeleted() } }
    fun flagIssue(issue: CivicIssueDto, reason: String, onSent: () -> Unit) { mutate { repository.flagIssue(issue, reason); onSent() } }
    fun toggleConfirmIssue(issue: CivicIssueDto) { mutate { repository.toggleConfirmIssue(issue) } }
    fun reviewFlag(flagId: Long, status: String, notes: String? = null) { mutate { repository.reviewFlag(flagId, status, notes) } }
    fun refreshAll() { action { repository.refreshAll() } }
    fun loadMore() { action { repository.loadMore() } }
    fun retryUploads() { action { repository.retryUploads() } }
    fun discardFailedUploads() { action { repository.discardFailedUploads() } }
    fun toggleIssueResolution(issue: CivicIssueDto) { action { repository.toggleIssueResolution(issue) } }
    fun openChannel(channelId: String?) { openChat?.cancel(); openChat = action { repository.openChannel(channelId) } }
    fun openStaffConversation(conversationId: String?) { action { repository.openStaffConversation(conversationId) } }
    fun sendStaffReply(channelId: String, conversationId: String, message: String, onSent: () -> Unit) {
        if (_sendingMessage.value) return
        _sendingMessage.value = true
        action { try { repository.sendStaffReply(channelId, conversationId, message); onSent() } finally { _sendingMessage.value = false } }
    }
    fun sendChannelMessage(channelId: String, message: String, onQueued: () -> Unit) {
        if (_sendingMessage.value) return
        _sendingMessage.value = true
        action { try { repository.sendMessage(channelId, message); onQueued() } finally { _sendingMessage.value = false } }
    }
    fun postNewIssue(caption: String, category: String, locationName: String, subLocation: String, latitude: Double,
                     longitude: Double, capturedFile: File?, pickedUri: Uri?, onComplete: () -> Unit) {
        if (_isPostingIssue.value) return
        action {
            _isPostingIssue.value = true
            try {
                repository.queueIssue(caption, category, locationName, subLocation, latitude, longitude, capturedFile, pickedUri)
                com.svce.issuegram.data.local.ReportDraftStore(getApplication()).clear()
                if (capturedFile?.parentFile?.name == "drafts") capturedFile.delete()
                onComplete()
            } finally { _isPostingIssue.value = false }
        }
    }
}
