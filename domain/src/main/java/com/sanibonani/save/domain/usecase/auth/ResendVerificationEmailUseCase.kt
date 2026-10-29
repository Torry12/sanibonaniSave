package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject

/**
 * Asks the backend to resend the address-verification mail for an unconfirmed account.
 */
class ResendVerificationEmailUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> =
        supabaseRepository.resendVerificationEmail(email)
}
