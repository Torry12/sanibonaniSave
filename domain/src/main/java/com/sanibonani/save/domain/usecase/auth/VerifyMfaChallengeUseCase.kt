package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.service.MfaService
import javax.inject.Inject

/**
 * Verifies a TOTP code against the account's first enrolled factor.
 *
 * High-value approvals surface a single challenge, so the "which factor" rule lives
 * here rather than in each screen that shows the dialog.
 */
class VerifyMfaChallengeUseCase @Inject constructor(
    private val mfaService: MfaService
) {
    suspend operator fun invoke(code: String): Result<Unit> {
        val factor = mfaService.getMfaFactors()
            .getOrElse { return Result.failure(it) }
            .firstOrNull()
            ?: return Result.failure(IllegalStateException(NO_ENROLLED_FACTOR))

        return mfaService.verifyMfa(factor.id, code, true)
    }

    private companion object {
        const val NO_ENROLLED_FACTOR =
            "No authenticator is enrolled on this account. Set up two-step verification before authorising this payment."
    }
}
