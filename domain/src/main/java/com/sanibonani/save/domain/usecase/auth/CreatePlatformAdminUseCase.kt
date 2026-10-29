package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject

/**
 * Creates the account behind a platform administrator login.
 *
 * The role claim is what row-level security reads to decide what the account can see, so it is
 * part of this operation rather than something the screen gets to improvise. The value is the
 * literal the server compares against; [com.sanibonani.save.domain.model.UserRole] is the client's
 * reading of it.
 */
class CreatePlatformAdminUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<String> =
        supabaseRepository.adminSignUp(
            email = email,
            password = password,
            metadata = mapOf(ROLE_KEY to ROLE_PLATFORM_ADMIN, "full_name" to DEFAULT_FULL_NAME),
            confirm = true
        )

    private companion object {
        const val ROLE_KEY = "role"
        const val ROLE_PLATFORM_ADMIN = "platform_admin"
        const val DEFAULT_FULL_NAME = "Platform Administrator"
    }
}
