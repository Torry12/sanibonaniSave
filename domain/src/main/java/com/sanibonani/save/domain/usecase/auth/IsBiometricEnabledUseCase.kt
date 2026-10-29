package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.auth.CredentialsRepository
import javax.inject.Inject

/**
 * Reads whether the user asked for biometric unlock on this device.
 */
class IsBiometricEnabledUseCase @Inject constructor(
    private val credentialsRepository: CredentialsRepository
) {
    operator fun invoke(): Boolean = credentialsRepository.isBiometricEnabled()
}
