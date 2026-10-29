package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject

/**
 * The signed-in user id, or null when the session is missing or blank.
 *
 * A decision recorded against nobody cannot be audited, so callers that write an approver or
 * reviewer must refuse instead of defaulting to a placeholder. A blank id counts as missing: the
 * session can be populated a moment after the process starts, and an empty string is not an actor.
 */
class GetAttributableActorIdUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    operator fun invoke(): String? =
        supabaseRepository.currentUserId?.takeIf { it.isNotBlank() }
}
