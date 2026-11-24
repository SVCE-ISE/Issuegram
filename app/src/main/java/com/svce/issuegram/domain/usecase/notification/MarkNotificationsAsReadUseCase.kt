package com.svce.issuegram.domain.usecase.notification

import com.svce.issuegram.domain.repository.NotificationRepository

class MarkNotificationsAsReadUseCase(private val notificationRepository: NotificationRepository) {
    suspend operator fun invoke(): Result<Unit> = notificationRepository.markAllAsRead()
}
