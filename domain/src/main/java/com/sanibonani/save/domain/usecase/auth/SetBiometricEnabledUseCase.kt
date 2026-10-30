package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.CredentialsRepository
import javax.inject.Inject

/**
 * Persists the device-local biometric unlock preference.
 */
class SetBiometricEnabledUseCase @Inject constructor(
    private val credentialsRepository: CredentialsRepository
) {
    operator fun invoke(enabled: Boolean) = credentialsRepository.setBiometricEnabled(enabled)
}
