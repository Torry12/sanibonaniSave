package com.sanibonani.save.di

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockSupabaseRepository @Inject constructor() : SupabaseRepository {
    override val currentUserId: String? get() = TestAuthSessionController.mockedUserId
    override val currentSessionEmail: String? get() = TestAuthSessionController.mockedEmail
    override val accessToken: String? get() = TestAuthSessionController.currentSession?.accessToken
    override val supabaseUrl: String get() = "https://127.0.0.1"
    override val isLoggedIn: Boolean get() = TestAuthSessionController.mockedUserId != null
    override val currentSession: AuthSession? get() = TestAuthSessionController.currentSession
    override val sessionStatus: Flow<AuthSessionStatus> = TestAuthSessionController.sessionStatus
    override val sessionFlow: Flow<AuthSession?> = TestAuthSessionController.sessionFlow

    override suspend fun signUp(email: String, password: String, metadata: Map<String, String>): Result<String> {
        val id = java.util.UUID.randomUUID().toString()
        TestAuthSessionController.setRole(UserRole.MEMBER)
        TestAuthSessionController.signIn(email)
        return Result.success(id)
    }

    override suspend fun adminSignUp(email: String, password: String, metadata: Map<String, String>, confirm: Boolean): Result<String> {
        val id = java.util.UUID.randomUUID().toString()
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

    override suspend fun resendVerificationEmail(email: String): Result<Unit> = Result.success(Unit)

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

    // MFA Mocks
    override val isMfaVerified: Boolean get() = TestAuthSessionController.mockedIsMfaVerified
    override suspend fun getMfaFactors(): Result<List<MfaFactor>> = Result.success(emptyList())
    override suspend fun verifyMfa(factorId: String, code: String, saveSession: Boolean): Result<Unit> =
        Result.success(Unit)
    override suspend fun enrollMfa(): Result<TotpEnrollment> =
        Result.failure(NotImplementedError("MFA not mocked in tests"))
}
