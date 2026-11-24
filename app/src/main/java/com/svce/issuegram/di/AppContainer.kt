package com.svce.issuegram.di

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

interface AppContainer {
    val issueRepository: IssueRepository
    val authRepository: AuthRepository
    val notificationRepository: NotificationRepository

    val getFeedIssuesUseCase: GetFeedIssuesUseCase
    val getIssueDetailUseCase: GetIssueDetailUseCase
    val getUserIssuesUseCase: GetUserIssuesUseCase
    val createIssueUseCase: CreateIssueUseCase
    val toggleLikeUseCase: ToggleLikeUseCase
    val observeCurrentUserUseCase: ObserveCurrentUserUseCase
    val updateUserProfileUseCase: UpdateUserProfileUseCase
    val getNotificationsUseCase: GetNotificationsUseCase
    val markNotificationsAsReadUseCase: MarkNotificationsAsReadUseCase
}
