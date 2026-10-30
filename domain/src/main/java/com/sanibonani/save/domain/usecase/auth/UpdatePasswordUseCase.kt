package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject

/**
 * Sets a new password for the account a recovery link just authenticated.
 */
class UpdatePasswordUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    suspend operator fun invoke(newPassword: String): Result<Unit> =
        supabaseRepository.updatePassword(newPassword)
}
