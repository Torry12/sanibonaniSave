package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.repository.NotificationRepository
import javax.inject.Inject

/**
 * Orchestrates the password reset request process.
 */
class ResetPasswordUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(phone: String): Result<Unit> {
        if (phone.isBlank()) return Result.failure(Exception("Phone number is required"))
        
        // This could involve generating a reset token, looking up the user, etc.
        // For now, we delegate to the repository to send a WhatsApp notification.
        return notificationRepository.sendPasswordResetWhatsApp(phone)
    }
}
