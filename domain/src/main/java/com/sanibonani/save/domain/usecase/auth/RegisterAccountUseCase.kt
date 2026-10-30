package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.utils.UserRoleMapper
import javax.inject.Inject

/**
 * Creates an account carrying the role and full name the sign-up form collected.
 *
 * The role travels as the storage alias the server and the role mapper both read, so a
 * registered admin is not left with a member account until someone edits the row by hand.
 */
class RegisterAccountUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        fullName: String,
        role: UserRole
    ): Result<String> = supabaseRepository.signUp(
        email = email,
        password = password,
        metadata = mapOf(
            ROLE_KEY to UserRoleMapper.toStorageValue(role),
            FULL_NAME_KEY to fullName
        )
    )

    private companion object {
        const val ROLE_KEY = "role"
        const val FULL_NAME_KEY = "full_name"
    }
}
