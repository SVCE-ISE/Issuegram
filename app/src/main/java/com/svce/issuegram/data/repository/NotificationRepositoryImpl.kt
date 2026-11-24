package com.svce.issuegram.data.repository

import com.svce.issuegram.IssuegramApplication
import com.svce.issuegram.data.datasource.remote.NotificationRemoteDataSource
import com.svce.issuegram.domain.model.Notification
import com.svce.issuegram.domain.repository.NotificationRepository
import com.svce.issuegram.util.SystemNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationRepositoryImpl(
    private val remoteDataSource: NotificationRemoteDataSource,
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onNewUnreadNotification: (Notification) -> Unit = { notif ->
        runCatching {
            SystemNotificationHelper.showStatusUpdateNotification(
                context = IssuegramApplication.instance,
                ticketId = notif.ticketId,
                issueTitle = notif.issueTitle,
                status = notif.status ?: com.svce.issuegram.data.model.IssueStatus.Open,
                milestoneTitle = notif.message
            )
        }
    }
) : NotificationRepository {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notificationsFlow: Flow<List<Notification>> = _notifications.asStateFlow()
    private val seenNotificationIds = mutableSetOf<String>()
    private var hasLoadedInitial = false

    init {
        repositoryScope.launch {
            remoteDataSource.observeNotifications().collect { updatedList ->
                if (!hasLoadedInitial) {
                    hasLoadedInitial = true
                    seenNotificationIds.addAll(updatedList.map { it.id })
                } else {
                    for (notif in updatedList) {
                        if (notif.isUnread && seenNotificationIds.add(notif.id)) {
                            onNewUnreadNotification(notif)
                        }
                    }
                }
                _notifications.value = updatedList
            }
        }
    }

    override fun getNotifications(): Flow<List<Notification>> = notificationsFlow

    override suspend fun markAllAsRead(): Result<Unit> = runCatching {
        remoteDataSource.markAllAsRead()
        _notifications.value = _notifications.value.map { it.copy(isUnread = false) }
    }
}
