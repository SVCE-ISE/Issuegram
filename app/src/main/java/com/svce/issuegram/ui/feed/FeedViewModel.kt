package com.svce.issuegram.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.domain.model.User
import com.svce.issuegram.domain.usecase.auth.ObserveCurrentUserUseCase
import com.svce.issuegram.domain.usecase.issue.GetFeedIssuesUseCase
import com.svce.issuegram.domain.usecase.issue.ToggleLikeUseCase
import com.svce.issuegram.domain.repository.IssueRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedUiState(
    val issues: List<Issue> = emptyList(),
    val currentUser: User? = null,
    val hasUnreadNotifications: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)

class FeedViewModel(
    getFeedIssuesUseCase: GetFeedIssuesUseCase,
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    getNotificationsUseCase: com.svce.issuegram.domain.usecase.notification.GetNotificationsUseCase,
    private val toggleLikeUseCase: ToggleLikeUseCase,
    private val issueRepository: IssueRepository? = null,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<FeedUiState> = combine(
        getFeedIssuesUseCase(),
        observeCurrentUserUseCase(),
        getNotificationsUseCase(),
        _isRefreshing,
    ) { issues, user, notifications, refreshing ->
        FeedUiState(
            issues = issues,
            currentUser = user,
            hasUnreadNotifications = notifications.any { it.isUnread },
            isLoading = false,
            isRefreshing = refreshing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FeedUiState(isLoading = true)
    )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                issueRepository?.refreshIssues()
            } catch (_: Exception) {
            } finally {
                delay(600)
                _isRefreshing.value = false
            }
        }
    }

    fun toggleLike(ticketId: String) {
        viewModelScope.launch {
            toggleLikeUseCase(ticketId)
        }
    }
}
