package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.AuthSession
import com.sanibonani.save.domain.model.AuthSessionStatus
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.model.MfaFactor
import com.sanibonani.save.domain.model.TotpEnrollment
import kotlinx.coroutines.flow.Flow

/**
 * Portable abstraction for authentication and user session management.
 *
 * Exposes domain types rather than the Supabase SDK's own session objects, so
 * the authentication port stays usable without pulling in the SDK.
 */
interface AuthDataSource {
    val currentUserId: String?
    val currentSessionEmail: String?
    val accessToken: String?
    val supabaseUrl: String
    val isLoggedIn: Boolean
    val currentSession: AuthSession?
    val sessionStatus: Flow<AuthSessionStatus>
    val sessionFlow: Flow<AuthSession?>
    val isMfaVerified: Boolean

    suspend fun signUp(email: String, password: String, metadata: Map<String, String>): Result<String>
    suspend fun adminSignUp(email: String, password: String, metadata: Map<String, String>, confirm: Boolean = true): Result<String>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signInWithMagicLink(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    suspend fun handleDeepLink(url: String): Result<Unit>
    suspend fun getUserRole(): UserRole
    suspend fun updateUserRole(userId: String, role: UserRole, groupId: String? = null): Result<Unit>
    suspend fun getMfaFactors(): Result<List<MfaFactor>>
    suspend fun verifyMfa(factorId: String, code: String, saveSession: Boolean = true): Result<Unit>
    suspend fun enrollMfa(): Result<TotpEnrollment>
    suspend fun resendVerificationEmail(email: String): Result<Unit>
}
