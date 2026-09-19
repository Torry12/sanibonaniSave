package com.sanibonani.save.data.repository.system

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.repository.mappers.toEntity
import com.sanibonani.save.data.repository.mappers.toModel
import com.sanibonani.save.domain.model.UserProfile
import com.sanibonani.save.domain.repository.system.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepositoryImpl @Inject constructor(
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>
) : BaseRepository("UserProfileRepository"), UserProfileRepository {

    private val db: SanibonaniDatabase get() = dbProvider.get()
    private val dao get() = db.userProfileDao()

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = runCatching {
        dao.upsertProfile(profile.toEntity())
    }

    override suspend fun getProfile(userId: String): Result<UserProfile?> = runCatching {
        dao.getProfile(userId)?.toModel()
    }

    override fun observeProfile(userId: String): Flow<Result<UserProfile?>> =
        dao.observeProfile(userId).map { Result.success(it?.toModel()) }

    override suspend fun deleteProfile(userId: String): Result<Unit> = runCatching {
        dao.deleteProfile(userId)
    }
}
