package com.svce.issuegram.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.domain.usecase.auth.ObserveCurrentUserUseCase
import com.svce.issuegram.domain.usecase.issue.CreateIssueUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CameraReportViewModel(
    private val createIssueUseCase: CreateIssueUseCase,
    observeCurrentUserUseCase: ObserveCurrentUserUseCase
) : ViewModel() {

    private val currentUser = observeCurrentUserUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    fun submitIssue(
        title: String,
        description: String?,
        categoryName: String,
        locationName: String,
        photoUri: String?,
        latitude: Double,
        longitude: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isPosting.value) return
        val category = when {
            categoryName.contains("pothole", ignoreCase = true) -> IssueCategory.Pothole
            categoryName.contains("streetlight", ignoreCase = true) ||
                categoryName.contains("light", ignoreCase = true) -> IssueCategory.Streetlight
            categoryName.contains("garbage", ignoreCase = true) -> IssueCategory.Garbage
            categoryName.contains("water", ignoreCase = true) -> IssueCategory.Water
            categoryName.contains("drain", ignoreCase = true) -> IssueCategory.Drainage
            categoryName.contains("road", ignoreCase = true) -> IssueCategory.Road
            categoryName.contains("safety", ignoreCase = true) -> IssueCategory.Safety
            else -> IssueCategory.Other
        }

        val user = currentUser.value
        val authorName = user?.displayName?.takeIf { it.isNotBlank() } ?: "Civic Reporter"
        val authorUsername = user?.displayName?.takeIf { it.isNotBlank() } ?: authorName

        viewModelScope.launch {
            _isPosting.value = true
            try {
                val result = createIssueUseCase(
                    title = title,
                    description = description,
                    category = category,
                    locationName = locationName,
                    photoUri = photoUri,
                    latitude = latitude,
                    longitude = longitude,
                    authorName = authorName,
                    authorUsername = authorUsername
                )
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    onError(result.exceptionOrNull()?.message ?: "Failed to post issue")
                }
            } finally {
                _isPosting.value = false
            }
        }
    }
}
