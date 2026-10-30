package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject

/**
 * Emails a single-use sign-in link, for users who would rather not type a password.
 */
class RequestMagicLinkUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> =
        supabaseRepository.signInWithMagicLink(email)
}
