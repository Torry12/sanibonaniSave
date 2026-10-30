package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject

/**
 * Whether a Supabase session is currently held on this device.
 */
class IsLoggedInUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    operator fun invoke(): Boolean = supabaseRepository.isLoggedIn
}
