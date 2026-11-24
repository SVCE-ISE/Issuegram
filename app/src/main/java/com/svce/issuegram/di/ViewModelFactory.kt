package com.svce.issuegram.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.svce.issuegram.ui.feed.FeedViewModel
import com.svce.issuegram.ui.profile.ProfileViewModel
import com.svce.issuegram.ui.screens.AlertsViewModel
import com.svce.issuegram.ui.screens.IssueDetailViewModel

class ViewModelFactory(private val appContainer: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(FeedViewModel::class.java) -> {
            FeedViewModel(
                getFeedIssuesUseCase = appContainer.getFeedIssuesUseCase,
                observeCurrentUserUseCase = appContainer.observeCurrentUserUseCase,
                getNotificationsUseCase = appContainer.getNotificationsUseCase,
                toggleLikeUseCase = appContainer.toggleLikeUseCase,
                issueRepository = appContainer.issueRepository
            ) as T
        }
        modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
            ProfileViewModel(
                observeCurrentUserUseCase = appContainer.observeCurrentUserUseCase,
                getUserIssuesUseCase = appContainer.getUserIssuesUseCase,
                updateUserProfileUseCase = appContainer.updateUserProfileUseCase,
                issueRepository = appContainer.issueRepository,
                authRepository = appContainer.authRepository
            ) as T
        }
        modelClass.isAssignableFrom(AlertsViewModel::class.java) -> {
            AlertsViewModel(
                getNotificationsUseCase = appContainer.getNotificationsUseCase,
                markNotificationsAsReadUseCase = appContainer.markNotificationsAsReadUseCase
            ) as T
        }
        modelClass.isAssignableFrom(IssueDetailViewModel::class.java) -> {
            IssueDetailViewModel(
                getIssueDetailUseCase = appContainer.getIssueDetailUseCase,
                toggleLikeUseCase = appContainer.toggleLikeUseCase
            ) as T
        }
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
