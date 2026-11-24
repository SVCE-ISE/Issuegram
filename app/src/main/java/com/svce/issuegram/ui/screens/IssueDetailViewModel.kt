package com.svce.issuegram.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.domain.model.User
import com.svce.issuegram.domain.repository.IssueRepository
import com.svce.issuegram.domain.usecase.auth.ObserveCurrentUserUseCase
import com.svce.issuegram.domain.usecase.issue.GetIssueDetailUseCase
import com.svce.issuegram.domain.usecase.issue.ToggleLikeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IssueDetailUiState(val issue: Issue? = null, val isLiked: Boolean = false, val isLoading: Boolean = false)

class IssueDetailViewModel(
    private val getIssueDetailUseCase: GetIssueDetailUseCase,
    private val toggleLikeUseCase: ToggleLikeUseCase,
    private val issueRepository: IssueRepository? = null,
    observeCurrentUserUseCase: ObserveCurrentUserUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(IssueDetailUiState(isLoading = true))
    val uiState: StateFlow<IssueDetailUiState> = _uiState.asStateFlow()

    val currentUser: StateFlow<User?> = observeCurrentUserUseCase?.invoke()?.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    ) ?: MutableStateFlow(null).asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadIssue(ticketId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            getIssueDetailUseCase(ticketId).collect { issue ->
                _uiState.value = IssueDetailUiState(
                    issue = issue,
                    isLiked = issue?.isLikedByMe == true,
                    isLoading = false
                )
            }
        }
    }

    fun toggleLike(ticketId: String) {
        viewModelScope.launch {
            toggleLikeUseCase(ticketId)
        }
    }

    fun updateStatus(ticketId: String, status: com.svce.issuegram.data.model.IssueStatus) {
        viewModelScope.launch {
            issueRepository?.updateStatus(ticketId, status)
        }
    }
}
