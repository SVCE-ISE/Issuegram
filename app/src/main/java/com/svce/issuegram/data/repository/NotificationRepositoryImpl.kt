package com.svce.issuegram.data.repository

import com.svce.issuegram.data.datasource.remote.NotificationRemoteDataSource
import com.svce.issuegram.domain.model.Notification
import com.svce.issuegram.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationRepositoryImpl(
    private val remoteDataSource: NotificationRemoteDataSource,
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : NotificationRepository {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notificationsFlow: Flow<List<Notification>> = _notifications.asStateFlow()

    init {
        repositoryScope.launch {
            _notifications.value = remoteDataSource.fetchNotifications()
        }
    }

    override fun getNotifications(): Flow<List<Notification>> = notificationsFlow

    override suspend fun markAllAsRead(): Result<Unit> = runCatching {
        remoteDataSource.markAllAsRead()
        _notifications.value = _notifications.value.map { it.copy(isUnread = false) }
    }
}
