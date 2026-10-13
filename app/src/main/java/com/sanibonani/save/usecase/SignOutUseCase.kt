package com.sanibonani.save.usecase

import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val adminGroupContextCacheService: AdminGroupContextCacheService,
    private val memberGroupContextCacheService: MemberGroupContextCacheService,
    private val userProfileCacheService: UserProfileCacheService
) {
    suspend operator fun invoke() {
        adminGroupContextCacheService.clearForSignOut()
        memberGroupContextCacheService.clearForSignOut()
        userProfileCacheService.clear()
        supabaseRepo.signOut()
    }
}
