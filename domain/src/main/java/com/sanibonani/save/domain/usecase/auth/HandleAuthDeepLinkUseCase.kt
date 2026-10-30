package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject

/**
 * Hands a Supabase auth callback URL to the client so it can exchange the token it carries.
 */
class HandleAuthDeepLinkUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    suspend operator fun invoke(url: String): Result<Unit> =
        supabaseRepository.handleDeepLink(url)
}
