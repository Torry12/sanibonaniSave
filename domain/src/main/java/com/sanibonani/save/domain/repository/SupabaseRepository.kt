package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.UserRole
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.Flow

interface SupabaseRepository {
    val currentUserId: String?
    val currentSessionEmail: String?
    val accessToken: String?
    val supabaseUrl: String
    val isLoggedIn: Boolean
    val currentSession: UserSession?
    val sessionStatus: Flow<SessionStatus>
    val sessionFlow: Flow<UserSession?>
    
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
    suspend fun resetLocalCache()
}
