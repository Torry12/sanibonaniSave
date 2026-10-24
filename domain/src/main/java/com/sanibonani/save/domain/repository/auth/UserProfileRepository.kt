package com.sanibonani.save.domain.repository.auth

import com.sanibonani.save.domain.model.UserProfile

interface UserProfileRepository {
    suspend fun getProfile(userId: String): Result<UserProfile?>
}
