package com.svce.issuegram.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.domain.model.User
import com.svce.issuegram.domain.repository.IssueRepository
import com.svce.issuegram.domain.usecase.auth.ObserveCurrentUserUseCase
import com.svce.issuegram.domain.usecase.auth.UpdateUserProfileUseCase
import com.svce.issuegram.domain.usecase.issue.GetUserIssuesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProfileUiState(
    val user: User? = null,
    val postedIssues: List<Issue> = emptyList(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null
)

class ProfileViewModel(
    observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val getUserIssuesUseCase: GetUserIssuesUseCase,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val issueRepository: IssueRepository,
    private val authRepository: com.svce.issuegram.domain.repository.AuthRepository? = null
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val currentUser: StateFlow<User?> = observeCurrentUserUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val allIssues: StateFlow<List<Issue>> = issueRepository.getFeedIssues().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val userPostedIssues: StateFlow<List<Issue>> = currentUser.flatMapLatest { user ->
        if (user != null) {
            getUserIssuesUseCase(user.id, user.displayName)
        } else {
            getUserIssuesUseCase("user_me", "You")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateDisplayName(newName: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = updateUserProfileUseCase.updateName(newName)
            _isBusy.value = false
            if (result.isSuccess) {
                onComplete()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            updateUserProfileUseCase.updateAvatar(uri.toString())
            _isBusy.value = false
        }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            _isBusy.value = true
            updateUserProfileUseCase.updateAvatar(null)
            _isBusy.value = false
        }
    }

    fun deleteIssue(ticketId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            issueRepository.deleteIssue(ticketId)
            onComplete()
        }
    }

    fun editIssue(ticketId: String, title: String, description: String?, locationName: String?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = issueRepository.editIssue(ticketId, title, description, locationName)
            _isBusy.value = false
            if (result.isSuccess) {
                onComplete()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun setUserAdminStatus(targetUid: String, makeAdmin: Boolean, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = runCatching {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("users").document(targetUid)
                    .set(mapOf("isAdmin" to makeAdmin), com.google.firebase.firestore.SetOptions.merge())
                    .await()
                Unit
            }
            _isBusy.value = false
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
            onComplete(result)
        }
    }

    fun signOut() {
        authRepository?.signOut() ?: com.google.firebase.Firebase.auth.signOut()
    }
}
