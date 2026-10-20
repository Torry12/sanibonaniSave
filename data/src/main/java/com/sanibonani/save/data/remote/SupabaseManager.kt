package com.sanibonani.save.data.remote

import com.sanibonani.save.data.datasource.AuthDataSource
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SupabaseRepository that delegates to the portable AuthDataSource.
 *
 * This class contains no Supabase SDK types: the SDK object is mapped to
 * [AuthSession] / [AuthSessionStatus] in SupabaseAuthDataSourceImpl.
 */
@Singleton
class SupabaseManager @Inject constructor(
    private val authDataSource: AuthDataSource
) : SupabaseRepository {

    override val currentSession: AuthSession? get() = authDataSource.currentSession
    override val currentUserId: String? get() = authDataSource.currentUserId
    override val currentSessionEmail: String? get() = authDataSource.currentSessionEmail
    override val accessToken: String? get() = authDataSource.accessToken
    override val supabaseUrl: String get() = authDataSource.supabaseUrl
    override val isLoggedIn: Boolean get() = authDataSource.isLoggedIn
    override val isMfaVerified: Boolean get() = authDataSource.isMfaVerified

    override suspend fun getMfaFactors(): Result<List<MfaFactor>> = authDataSource.getMfaFactors()
    override suspend fun verifyMfa(factorId: String, code: String, saveSession: Boolean): Result<Unit> = authDataSource.verifyMfa(factorId, code, saveSession)
    override suspend fun enrollMfa(): Result<TotpEnrollment> = authDataSource.enrollMfa()
    override val sessionStatus: Flow<AuthSessionStatus> = authDataSource.sessionStatus
    override val sessionFlow: Flow<AuthSession?> = authDataSource.sessionFlow

    override suspend fun signUp(email: String, password: String, metadata: Map<String, String>): Result<String> = authDataSource.signUp(email, password, metadata)
    override suspend fun adminSignUp(email: String, password: String, metadata: Map<String, String>, confirm: Boolean): Result<String> = authDataSource.adminSignUp(email, password, metadata, confirm)
    override suspend fun signIn(email: String, password: String): Result<Unit> = authDataSource.signIn(email, password)
    override suspend fun signInWithMagicLink(email: String): Result<Unit> = authDataSource.signInWithMagicLink(email)
    override suspend fun signOut(): Result<Unit> = authDataSource.signOut()
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = authDataSource.sendPasswordResetEmail(email)
    override suspend fun updatePassword(newPassword: String): Result<Unit> = authDataSource.updatePassword(newPassword)
    override suspend fun handleDeepLink(url: String): Result<Unit> = authDataSource.handleDeepLink(url)
    override suspend fun getUserRole(): UserRole = authDataSource.getUserRole()
    override suspend fun updateUserRole(userId: String, role: UserRole, groupId: String?): Result<Unit> = authDataSource.updateUserRole(userId, role, groupId)
    override suspend fun resendVerificationEmail(email: String): Result<Unit> = authDataSource.resendVerificationEmail(email)
}
