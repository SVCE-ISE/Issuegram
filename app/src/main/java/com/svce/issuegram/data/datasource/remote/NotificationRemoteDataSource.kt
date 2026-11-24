package com.svce.issuegram.data.datasource.remote

import com.svce.issuegram.domain.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRemoteDataSource {
    suspend fun fetchNotifications(): List<Notification>
    fun observeNotifications(): Flow<List<Notification>>
    suspend fun markAllAsRead()
}
