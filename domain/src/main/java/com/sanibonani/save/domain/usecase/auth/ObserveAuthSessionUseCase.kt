package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.model.AuthSession
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The authenticated session as the app sees it, emitting null once the user signs out.
 *
 * Routing keys off this flow rather than a one-shot read, so a restored session and a fresh
 * sign-in reach the UI through the same path.
 */
class ObserveAuthSessionUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    operator fun invoke(): Flow<AuthSession?> = supabaseRepository.sessionFlow
}
