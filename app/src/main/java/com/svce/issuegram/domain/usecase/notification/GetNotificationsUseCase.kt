package com.svce.issuegram.domain.usecase.notification

import com.svce.issuegram.domain.model.Notification
import com.svce.issuegram.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow

class GetNotificationsUseCase(private val notificationRepository: NotificationRepository) {
    operator fun invoke(): Flow<List<Notification>> = notificationRepository.getNotifications()
}
