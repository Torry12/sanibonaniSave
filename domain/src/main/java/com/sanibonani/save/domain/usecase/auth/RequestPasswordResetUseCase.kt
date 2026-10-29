package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject

/**
 * Sends the password-reset mail. The address is canonicalised here so a reset is requested
 * for the account the server actually holds, whatever case the user typed.
 */
class RequestPasswordResetUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> =
        supabaseRepository.sendPasswordResetEmail(email.trim().lowercase())
}
