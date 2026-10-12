package com.sanibonani.save.usecase

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.data.local.SanibonaniDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val adminGroupContextCacheService: AdminGroupContextCacheService,
    private val memberGroupContextCacheService: MemberGroupContextCacheService,
    private val userProfileCacheService: UserProfileCacheService,
    private val realtimeSyncManager: RealtimeSyncManager,
    private val database: SanibonaniDatabase
) {
    suspend operator fun invoke(): Result<Unit> = safeRunCatching {
        realtimeSyncManager.stop()
        adminGroupContextCacheService.clearForSignOut()
        memberGroupContextCacheService.clearForSignOut()
        userProfileCacheService.clear()

        // Securely wipe all locally cached user data to prevent cross-account contamination
        withContext(Dispatchers.IO) {
            safeRunCatching { database.clearAllTables() }
        }

        supabaseRepo.signOut()
    }
}
