package com.sanibonani.save.di

import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Singleton
import java.util.UUID

object TestAuthSessionController {
    private const val TEST_PLATFORM_ADMIN_PASSWORD = "torry123M"

    private val _sessionStatus = MutableStateFlow<SessionStatus>(SessionStatus.NotAuthenticated(false))
    private val _sessionFlow = MutableStateFlow<UserSession?>(null)

    var mockedUserId: String? = null
        private set
    var mockedEmail: String? = null
        private set
    var mockedRole: UserRole = UserRole.GROUP_ADMIN
        private set

    val sessionStatus: Flow<SessionStatus> = _sessionStatus.asStateFlow()
    val sessionFlow: Flow<UserSession?> = _sessionFlow.asStateFlow()

    fun reset(role: UserRole = UserRole.GROUP_ADMIN) {
        mockedUserId = null
        mockedEmail = null
        mockedRole = role
        _sessionFlow.value = null
        _sessionStatus.value = SessionStatus.NotAuthenticated(true)
    }

    fun setRole(role: UserRole) {
        mockedRole = role
    }

    fun signIn(email: String) {
        mockedUserId = mockedUserId ?: UUID.randomUUID().toString()
        mockedEmail = email
        val session = buildSession(email)
        _sessionFlow.value = session
        _sessionStatus.value = SessionStatus.Authenticated(session)
    }

    fun emitAuthenticatedSession() {
        val email = mockedEmail ?: "test-user@example.com"
        signIn(email)
    }

    fun signOut() {
        mockedUserId = null
        mockedEmail = null
        _sessionFlow.value = null
        _sessionStatus.value = SessionStatus.NotAuthenticated(true)
    }

    private fun buildSession(email: String): UserSession {
        val now = Clock.System.now().epochSeconds
        val tokenSuffix = email.substringBefore("@").ifBlank { "user" }
        return UserSession(
            accessToken = "mock-access-token-$tokenSuffix",
            refreshToken = "mock-refresh-token",
            expiresIn = 3600,
            tokenType = "bearer",
            type = "authenticated",
            expiresAt = Instant.fromEpochSeconds(now + 3600)
        )
    }

    fun isValidCredentials(email: String, password: String): Boolean {
        return if (PlatformAdminAuthPolicy.isPlatformAdminEmail(email)) {
            password == TEST_PLATFORM_ADMIN_PASSWORD
        } else {
            // Non-admin test users stay permissive for existing integration tests.
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
object TestSupabaseRepoModule {

    @Provides
    @Singleton
    fun provideMockSupabaseRepository(): SupabaseRepository = object : SupabaseRepository {
        override val currentUserId: String? get() = TestAuthSessionController.mockedUserId
        override val currentSessionEmail: String? get() = TestAuthSessionController.mockedEmail
        override val accessToken: String? get() = "mock-access-token"
        override val supabaseUrl: String get() = "https://127.0.0.1"
        override val isLoggedIn: Boolean get() = TestAuthSessionController.mockedUserId != null
        override val currentSession: UserSession? get() = null
        override val sessionStatus: Flow<SessionStatus> = TestAuthSessionController.sessionStatus
        override val sessionFlow: Flow<UserSession?> = TestAuthSessionController.sessionFlow

        override suspend fun signUp(email: String, password: String, metadata: Map<String, String>): Result<String> {
            val id = UUID.randomUUID().toString()
            TestAuthSessionController.setRole(UserRole.MEMBER)
            TestAuthSessionController.signIn(email)
            return Result.success(id)
        }

        override suspend fun adminSignUp(email: String, password: String, metadata: Map<String, String>, confirm: Boolean): Result<String> {
            val id = UUID.randomUUID().toString()
            return Result.success(id)
        }

        override suspend fun signIn(email: String, password: String): Result<Unit> {
            if (!TestAuthSessionController.isValidCredentials(email, password)) {
                return Result.failure(IllegalStateException("Invalid login credentials"))
            }
            val resolvedRole = TestAuthSessionController.roleForCredentials(
                email = email,
                password = password,
                fallback = TestAuthSessionController.mockedRole
            )
            TestAuthSessionController.setRole(resolvedRole)
            TestAuthSessionController.signIn(email)
            return Result.success(Unit)
        }

        override suspend fun signInWithMagicLink(email: String): Result<Unit> {
            TestAuthSessionController.signIn(email)
            return Result.success(Unit)
        }

        override suspend fun signOut(): Result<Unit> {
            TestAuthSessionController.signOut()
            return Result.success(Unit)
        }

        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun updatePassword(newPassword: String): Result<Unit> = Result.success(Unit)
        override suspend fun handleDeepLink(url: String): Result<Unit> = Result.success(Unit)
        override suspend fun getUserRole(): UserRole = TestAuthSessionController.mockedRole
        override suspend fun updateUserRole(userId: String, role: UserRole, groupId: String?): Result<Unit> {
            TestAuthSessionController.setRole(role)
            return Result.success(Unit)
        }
        override suspend fun resetLocalCache() {}
    }
}
