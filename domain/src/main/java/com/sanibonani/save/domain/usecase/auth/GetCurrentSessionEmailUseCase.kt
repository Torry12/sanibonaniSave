package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject

/**
 * The email of the signed-in account, or null when there is no session.
 */
class GetCurrentSessionEmailUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    operator fun invoke(): String? = supabaseRepository.currentSessionEmail
}
