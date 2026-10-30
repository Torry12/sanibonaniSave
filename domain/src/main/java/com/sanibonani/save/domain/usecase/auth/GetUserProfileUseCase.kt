package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.model.UserProfile
import com.sanibonani.save.domain.repository.auth.UserProfileRepository
import javax.inject.Inject

/**
 * The stored profile row for one account, or null when the account has none yet.
 */
class GetUserProfileUseCase @Inject constructor(
    private val userProfileRepository: UserProfileRepository
) {
    suspend operator fun invoke(userId: String): Result<UserProfile?> =
        userProfileRepository.getProfile(userId)
}
