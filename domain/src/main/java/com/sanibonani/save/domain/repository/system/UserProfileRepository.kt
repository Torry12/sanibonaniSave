package com.sanibonani.save.domain.repository.system

import com.sanibonani.save.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
    suspend fun getProfile(userId: String): Result<UserProfile?>
    fun observeProfile(userId: String): Flow<Result<UserProfile?>>
    suspend fun deleteProfile(userId: String): Result<Unit>
}
