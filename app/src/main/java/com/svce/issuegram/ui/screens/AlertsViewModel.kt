package com.svce.issuegram.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svce.issuegram.domain.model.Notification
import com.svce.issuegram.domain.usecase.notification.GetNotificationsUseCase
import com.svce.issuegram.domain.usecase.notification.MarkNotificationsAsReadUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlertsUiState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
)

class AlertsViewModel(
    getNotificationsUseCase: GetNotificationsUseCase,
    private val markNotificationsAsReadUseCase: MarkNotificationsAsReadUseCase,
) : ViewModel() {

    val uiState: StateFlow<AlertsUiState> = getNotificationsUseCase().map {
        AlertsUiState(notifications = it, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AlertsUiState(isLoading = true)
    )

    fun markAllAsRead() {
        viewModelScope.launch {
            markNotificationsAsReadUseCase()
        }
    }
}
