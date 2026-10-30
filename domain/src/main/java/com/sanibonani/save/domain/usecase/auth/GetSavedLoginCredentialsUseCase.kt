package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.CredentialsRepository
import javax.inject.Inject

/**
 * What the login screen can prefill from the credential store.
 *
 * One read keeps the fields consistent with each other: a stale password shown next to a
 * "remember me" that is off is what filling them separately produces. The password is returned
 * as stored — deciding when it may be shown is the screen's call, not this read's.
 */
data class SavedLoginCredentials(
    val email: String,
    val password: String,
    val rememberMe: Boolean,
    val hasSavedCredentials: Boolean,
    val biometricEnabled: Boolean,
)

class GetSavedLoginCredentialsUseCase @Inject constructor(
    private val credentialsRepository: CredentialsRepository
) {
    operator fun invoke(): SavedLoginCredentials = SavedLoginCredentials(
        email = credentialsRepository.getSavedEmail(),
        password = credentialsRepository.getSavedPassword(),
        rememberMe = credentialsRepository.getRememberMe(),
        hasSavedCredentials = credentialsRepository.hasSavedCredentials(),
        biometricEnabled = credentialsRepository.isBiometricEnabled(),
    )
}
