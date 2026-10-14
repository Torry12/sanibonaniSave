package com.sanibonani.save.data.repository.auth

import com.sanibonani.save.data.repository.BaseRepository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.UserProfile
import com.sanibonani.save.domain.repository.auth.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class UserProfileRepositoryImpl @Inject constructor(
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>
) : BaseRepository("UserProfileRepository"), UserProfileRepository {

    private val db: SanibonaniDatabase get() = dbProvider.get()
    private val dao get() = db.userProfileDao()

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = safeRunCatching {
        dao.upsertProfile(profile.toEntity())
    }

    override suspend fun getProfile(userId: String): Result<UserProfile?> = safeRunCatching {
        dao.getProfile(userId)?.toModel()
    }

    override fun observeProfile(userId: String): Flow<Result<UserProfile?>> =
        dao.observeProfile(userId).map { Result.success(it?.toModel()) }

    override suspend fun deleteProfile(userId: String): Result<Unit> = safeRunCatching {
        dao.deleteProfile(userId)
    }
}

