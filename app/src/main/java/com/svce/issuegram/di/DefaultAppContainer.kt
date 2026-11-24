package com.svce.issuegram.di

import android.content.Context
import com.svce.issuegram.IssuegramApplication
import com.svce.issuegram.data.datasource.local.IssueLocalDataSource
import com.svce.issuegram.data.datasource.remote.FirebaseAuthRemoteDataSource
import com.svce.issuegram.data.datasource.remote.FirestoreIssueRemoteDataSource
import com.svce.issuegram.data.datasource.remote.FirestoreNotificationRemoteDataSource
import com.svce.issuegram.data.repository.AuthRepositoryImpl
import com.svce.issuegram.data.repository.IssueRepositoryImpl
import com.svce.issuegram.data.repository.NotificationRepositoryImpl
import com.svce.issuegram.domain.repository.AuthRepository
import com.svce.issuegram.domain.repository.IssueRepository
import com.svce.issuegram.domain.repository.NotificationRepository
import com.svce.issuegram.domain.usecase.auth.ObserveCurrentUserUseCase
import com.svce.issuegram.domain.usecase.auth.UpdateUserProfileUseCase
import com.svce.issuegram.domain.usecase.issue.CreateIssueUseCase
import com.svce.issuegram.domain.usecase.issue.GetFeedIssuesUseCase
import com.svce.issuegram.domain.usecase.issue.GetIssueDetailUseCase
import com.svce.issuegram.domain.usecase.issue.GetUserIssuesUseCase
import com.svce.issuegram.domain.usecase.issue.ToggleLikeUseCase
import com.svce.issuegram.domain.usecase.notification.GetNotificationsUseCase
import com.svce.issuegram.domain.usecase.notification.MarkNotificationsAsReadUseCase

class DefaultAppContainer(private val context: Context = IssuegramApplication.instance) : AppContainer {

    private val issueRemoteDataSource by lazy {
        FirestoreIssueRemoteDataSource(context)
    }
    private val issueLocalDataSource by lazy { IssueLocalDataSource() }
    private val authRemoteDataSource by lazy { FirebaseAuthRemoteDataSource() }
    private val notificationRemoteDataSource by lazy {
        FirestoreNotificationRemoteDataSource(context)
    }

    override val issueRepository: IssueRepository by lazy {
        IssueRepositoryImpl(issueRemoteDataSource, issueLocalDataSource)
    }

    override val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(authRemoteDataSource)
    }

    override val notificationRepository: NotificationRepository by lazy {
        NotificationRepositoryImpl(notificationRemoteDataSource)
    }

    override val getFeedIssuesUseCase: GetFeedIssuesUseCase by lazy {
        GetFeedIssuesUseCase(issueRepository)
    }

    override val getIssueDetailUseCase: GetIssueDetailUseCase by lazy {
        GetIssueDetailUseCase(issueRepository)
    }

    override val getUserIssuesUseCase: GetUserIssuesUseCase by lazy {
        GetUserIssuesUseCase(issueRepository)
    }

    override val createIssueUseCase: CreateIssueUseCase by lazy {
        CreateIssueUseCase(issueRepository)
    }

    override val toggleLikeUseCase: ToggleLikeUseCase by lazy {
        ToggleLikeUseCase(issueRepository)
    }

    override val observeCurrentUserUseCase: ObserveCurrentUserUseCase by lazy {
        ObserveCurrentUserUseCase(authRepository)
    }

    override val updateUserProfileUseCase: UpdateUserProfileUseCase by lazy {
        UpdateUserProfileUseCase(authRepository)
    }

    override val getNotificationsUseCase: GetNotificationsUseCase by lazy {
        GetNotificationsUseCase(notificationRepository)
    }

    override val markNotificationsAsReadUseCase: MarkNotificationsAsReadUseCase by lazy {
        MarkNotificationsAsReadUseCase(notificationRepository)
    }
}
