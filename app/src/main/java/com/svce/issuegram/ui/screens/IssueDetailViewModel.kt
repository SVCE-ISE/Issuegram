package com.svce.issuegram.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.domain.usecase.issue.GetIssueDetailUseCase
import com.svce.issuegram.domain.usecase.issue.ToggleLikeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IssueDetailUiState(val issue: Issue? = null, val isLiked: Boolean = false, val isLoading: Boolean = false)

class IssueDetailViewModel(private val getIssueDetailUseCase: GetIssueDetailUseCase, private val toggleLikeUseCase: ToggleLikeUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(IssueDetailUiState(isLoading = true))
    val uiState: StateFlow<IssueDetailUiState> = _uiState.asStateFlow()

    fun loadIssue(ticketId: String) {
        viewModelScope.launch {
            getIssueDetailUseCase(ticketId).collect { issue ->
                _uiState.value = IssueDetailUiState(
                    issue = issue,
                    isLoading = false
                )
            }
        }
    }

    fun toggleLike(ticketId: String) {
        viewModelScope.launch {
            val currLiked = _uiState.value.isLiked
            _uiState.value = _uiState.value.copy(isLiked = !currLiked)
            toggleLikeUseCase(ticketId)
        }
    }
}
