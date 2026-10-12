package com.sanibonani.save.di

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Singleton
import java.util.UUID

object TestAuthSessionController {
    private const val TEST_PLATFORM_ADMIN_PASSWORD = "torry123M"

    private val _sessionStatus =
        MutableStateFlow<AuthSessionStatus>(AuthSessionStatus.NotAuthenticated(false))
    private val _sessionFlow = MutableStateFlow<AuthSession?>(null)

    var mockedUserId: String? = null
    var mockedEmail: String? = null
    var mockedRole: UserRole = UserRole.GROUP_ADMIN
        private set
    var mockedIsMfaVerified: Boolean = false

    val sessionStatus: Flow<AuthSessionStatus> = _sessionStatus.asStateFlow()
    val sessionFlow: Flow<AuthSession?> = _sessionFlow.asStateFlow()
    val currentSession: AuthSession? get() = _sessionFlow.value

    fun reset(role: UserRole = UserRole.GROUP_ADMIN) {
        mockedUserId = null
        mockedEmail = null
        mockedRole = role
        mockedIsMfaVerified = false
        _sessionFlow.value = null
        _sessionStatus.value = AuthSessionStatus.NotAuthenticated(true)
    }

    fun setRole(role: UserRole) {
        mockedRole = role
    }

    fun signIn(email: String) {
        if (PlatformAdminAuthPolicy.isPlatformAdminEmail(email)) {
            mockedRole = UserRole.PLATFORM_ADMIN
        }
        mockedUserId = mockedUserId ?: UUID.randomUUID().toString()
        mockedEmail = email
        val session = buildSession(email)
        _sessionFlow.value = session
        _sessionStatus.value = AuthSessionStatus.Authenticated(session)
    }

    fun emitAuthenticatedSession() {
        val email = mockedEmail ?: "test-user@example.com"
        signIn(email)
    }

    fun signOut() {
        mockedUserId = null
        mockedEmail = null
        _sessionFlow.value = null
        _sessionStatus.value = AuthSessionStatus.NotAuthenticated(true)
    }

    private fun buildSession(email: String): AuthSession = AuthSession(
        userId = mockedUserId ?: UUID.randomUUID().toString(),
        email = email,
        accessToken = "mock-access-token-" + email.substringBefore("@").ifBlank { "user" }
    )

    fun isValidCredentials(email: String, password: String): Boolean {
        return if (PlatformAdminAuthPolicy.isPlatformAdminEmail(email)) {
            password == TEST_PLATFORM_ADMIN_PASSWORD
        } else {
            true
        }
    }

    fun roleForCredentials(email: String, password: String, fallback: UserRole): UserRole {
        val isPlatformAdminLogin = PlatformAdminAuthPolicy.isPlatformAdminEmail(email) &&
            password == TEST_PLATFORM_ADMIN_PASSWORD
        return if (isPlatformAdminLogin) {
            UserRole.PLATFORM_ADMIN
        } else {
            fallback
        }
    }
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [SupabaseRepoModule::class]
)
abstract class TestSupabaseRepoModule {

    @Binds
    @Singleton
    abstract fun bindMockSupabaseRepository(
        impl: MockSupabaseRepository
    ): SupabaseRepository
}
