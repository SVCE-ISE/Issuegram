package com.svce.issuegram.domain.repository

import com.svce.issuegram.domain.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getNotifications(): Flow<List<Notification>>
    suspend fun markAllAsRead(): Result<Unit>
}
