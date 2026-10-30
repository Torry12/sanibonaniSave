package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.CredentialsRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject

/**
 * Signs in and applies the "remember me" decision in the same step.
 *
 * The choice belongs with the sign-in it describes: persisting or clearing the saved password
 * after the fact would let a successful login leave a stale credential behind.
 */
class SignInUseCase @Inject constructor(
    private val supabaseRepository: SupabaseRepository,
    private val credentialsRepository: CredentialsRepository
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        rememberMe: Boolean
    ): Result<Unit> = supabaseRepository.signIn(email, password).onSuccess {
        if (rememberMe) {
            credentialsRepository.saveCredentials(email, password, true)
        } else {
            credentialsRepository.clearCredentials()
        }
    }
}
