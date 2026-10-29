package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.utils.safeRunCatching
import javax.inject.Inject

/**
 * Resolves the signed-in account's role, failing closed to [UserRole.MEMBER].
 *
 * Elevated dashboards gate on this value, so a role that cannot be read must never
 * default to something more privileged than the caller can prove.
 */
class GetCurrentUserRoleUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    suspend operator fun invoke(): UserRole =
        safeRunCatching { supabaseRepository.getUserRole() }.getOrDefault(UserRole.MEMBER)
}
