package com.sanibonani.save.features.auth.presentation

import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.service.RealtimeSyncService
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.auth.GetUserProfileUseCase
import com.sanibonani.save.domain.usecase.auth.ObserveAuthSessionUseCase
import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.auth.presentation.AuthViewModel.AuthenticatedAccount
import com.sanibonani.save.features.common.presentation.LoadStateQueue
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Resolves an authenticated session into the account the UI routes on: profile cached,
 * role resolved fail-closed, and domain caches warmed before Success is emitted.
 */
internal class AuthSessionHydrator(
    private val observeAuthSession: ObserveAuthSessionUseCase,
    private val getUserProfile: GetUserProfileUseCase,
    private val getCurrentUserRole: GetCurrentUserRoleUseCase,
    private val adminGroupContextCacheService: AdminGroupContextCacheService,
    private val memberGroupContextCacheService: MemberGroupContextCacheService,
    private val userProfileCache: UserProfileCacheService,
    private val realtimeSyncService: RealtimeSyncService,
    private val loadQueue: LoadStateQueue,
) {

    fun accountStatus(): Flow<AsyncResult<AuthenticatedAccount>> =
        observeAuthSession().flatMapLatest { session ->
            if (session == null) {
                loadQueue.reset()
                flowOf<AsyncResult<AuthenticatedAccount>>(AsyncResult.Uninitialized)
            } else {
                flow<AsyncResult<AuthenticatedAccount>> {
                    emit(AsyncResult.Loading)
                    val userId = session.userId

                    loadQueue.startStep("User Profile")
                    getUserProfile(userId).onSuccess { profile ->
                        profile?.let { userProfileCache.save(it.fullName ?: "", it.email ?: "") }
                        loadQueue.completeStep("User Profile")
                    }.onFailure { loadQueue.failStep("User Profile", it.toUserMessage()) }

                    loadQueue.startStep("Security Permissions")
                    // Fail closed on privilege: an unreadable role is the least privileged role,
                    // never a guessed admin.
                    val role = getCurrentUserRole()
                    loadQueue.completeStep("Security Permissions")

                    // A cold cache or an unreachable realtime socket does not make the session
                    // invalid; failing here would strand the caller on the login screen.
                    loadQueue.startStep("Domain Context")
                    val warmup = safeRunCatching("AuthWarmup") {
                        coroutineScope {
                            val memberWarmup = async { memberGroupContextCacheService.warmUpForUser(userId) }
                            val adminWarmup = async { adminGroupContextCacheService.warmUpForUser(userId) }
                            awaitAll(memberWarmup, adminWarmup)
                        }
                        realtimeSyncService.start()
                    }
                    if (warmup.isSuccess) {
                        loadQueue.completeStep("Domain Context")
                    } else {
                        loadQueue.failStep("Domain Context", warmup.exceptionOrNull()?.toUserMessage().orEmpty())
                    }

                    emit(AsyncResult.Success(AuthenticatedAccount(userId, role)))
                }
            }
        }
}
