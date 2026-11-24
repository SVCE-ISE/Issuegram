package com.svce.issuegram.data.datasource.remote

import com.svce.issuegram.domain.model.Notification

interface NotificationRemoteDataSource {
    suspend fun fetchNotifications(): List<Notification>
    suspend fun markAllAsRead()
}
