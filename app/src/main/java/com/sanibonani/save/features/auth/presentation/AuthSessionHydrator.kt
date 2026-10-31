package com.sanibonani.save.features.auth.presentation

import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.UserRole
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
                    // Tracked here rather than read back from the queue: the queue is the render
                    // target, and a step that never got its terminal call is the one this run left
                    // open.
                    var openStep: String? = null

                    fun beginStep(key: String) {
                        openStep = key
                        loadQueue.startStep(key)
                    }
                    try {
                        beginStep("User Profile")
                        getUserProfile(userId).onSuccess { profile ->
                            profile?.let { userProfileCache.save(it.fullName ?: "", it.email ?: "") }
                            loadQueue.completeStep("User Profile")
                        }.onFailure { loadQueue.failStep("User Profile", it.toUserMessage()) }
                        openStep = null

                        beginStep("Security Permissions")
                        // Fail closed on privilege: an unreadable role is the least privileged role,
                        // never a guessed admin.
                        val role = getCurrentUserRole()
                        loadQueue.completeStep("Security Permissions")
                        openStep = null

                        // A cold cache or an unreachable realtime socket does not make the session
                        // invalid; failing here would strand the caller on the login screen.
                        beginStep("Domain Context")
                        val warmup = safeRunCatching("AuthWarmup") {
                            coroutineScope {
                                val memberWarmup = async { memberGroupContextCacheService.warmUpForUser(userId) }
                                // A member opens no admin surface, so boot does not pay for a
                                // getGroupsByAdmin call the server refuses with [SEC_002]. An
                                // elevation that arrives mid-session is served by the admin screens
                                // warming their own group on select.
                                val adminWarmup =
                                    if (role == UserRole.MEMBER) null
                                    else async { adminGroupContextCacheService.warmUpForUser(userId) }
                                memberWarmup.await()
                                adminWarmup?.await()
                            }
                            realtimeSyncService.start()
                        }
                        if (warmup.isSuccess) {
                            loadQueue.completeStep("Domain Context")
                        } else {
                            loadQueue.failStep("Domain Context", warmup.exceptionOrNull()?.toUserMessage().orEmpty())
                        }
                        openStep = null

                        emit(AsyncResult.Success(AuthenticatedAccount(userId, role)))
                    } finally {
                        // A step cancelled between its start and its terminal call stayed Loading,
                        // and the bootloader renders that as an initialisation that never finishes.
                        openStep?.let { loadQueue.failStep(it, "Hydration interrupted.") }
                    }
                }
            }
        }
}
