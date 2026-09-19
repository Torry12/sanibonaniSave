package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.UserProfile
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow

interface AuthService {
    val sessionStatus: Flow<SessionStatus>
    val sessionFlow: Flow<UserSession?>
    val currentSession: UserSession?
    val currentUserId: String?
    val currentSessionEmail: String?
    val accessToken: String?
    val isLoggedIn: Boolean

    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signUp(email: String, password: String, metadata: Map<String, String>): Result<String>
    suspend fun adminSignUp(email: String, password: String, metadata: Map<String, String>, confirm: Boolean): Result<String>
    suspend fun signInWithMagicLink(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun resendVerificationEmail(email: String): Result<Unit>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    suspend fun handleDeepLink(url: String): Result<Unit>
    suspend fun getProfile(userId: String): UserProfile?
    suspend fun resetLocalCache(): Result<Unit>
}
