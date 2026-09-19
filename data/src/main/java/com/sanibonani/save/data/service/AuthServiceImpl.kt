package com.sanibonani.save.data.service

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.UserProfile
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.service.AuthService
import com.sanibonani.save.domain.utils.AuthIdentityUtils
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.parseSessionFromUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthServiceImpl @Inject constructor(
    private val connectionProtocol: DataConnectionProtocol,
    private val edgeFunctionGateway: EdgeFunctionGateway,
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>
) : AuthService {

    private val client: SupabaseClient get() = connectionProtocol.getRemoteClient(SupabaseClient::class.java)!!
    private val db: SanibonaniDatabase get() = dbProvider.get()

    private fun canonicalEmail(email: String): String = AuthIdentityUtils.normalizeEmail(email)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val sessionStatus: Flow<SessionStatus> = connectionProtocol.remoteStatus.flatMapLatest {
        client.auth.sessionStatus
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val sessionFlow: Flow<UserSession?> = connectionProtocol.remoteStatus.flatMapLatest {
        client.auth.sessionStatus.map { (it as? SessionStatus.Authenticated)?.session }
    }

    override val currentSession: UserSession? get() = client.auth.currentSessionOrNull()
    override val currentUserId: String? get() = currentSession?.user?.id
    override val currentSessionEmail: String? get() = currentSession?.user?.email
    override val accessToken: String? get() = currentSession?.accessToken
    override val isLoggedIn: Boolean get() = currentSession?.accessToken?.isNotBlank() == true

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val normalizedEmail = canonicalEmail(email)
        client.auth.signInWith(Email) {
            this.email = normalizedEmail
            this.password = password
        }
        updateLoginAuditFields()
    }

    override suspend fun signUp(email: String, password: String, metadata: Map<String, String>): Result<String> = runCatching {
        val normalizedEmail = canonicalEmail(email)
        val response = client.auth.signUpWith(Email) {
            this.email = normalizedEmail
            this.password = password
            if (metadata.isNotEmpty()) {
                data = buildJsonObject {
                    metadata.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                }
            }
        }
        response?.id ?: client.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Signup failed to return user ID.")
    }

    override suspend fun adminSignUp(email: String, password: String, metadata: Map<String, String>, confirm: Boolean): Result<String> = runCatching {
        val normalizedEmail = canonicalEmail(email)
        edgeFunctionGateway.invoke(
            functionName = "mobile-admin-actions",
            payload = buildJsonObject {
                put("action", "admin_sign_up")
                put("email", normalizedEmail)
                put("password", password)
                put("confirm", confirm)
                put("metadata", buildJsonObject {
                    metadata.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                })
            }
        ).getOrThrow()["user_id"]?.jsonPrimitive?.contentOrNull ?: throw IllegalStateException("No user ID returned from admin signup.")
    }

    override suspend fun signInWithMagicLink(email: String): Result<Unit> = runCatching {
        client.auth.signInWith(OTP) {
            this.email = canonicalEmail(email)
            createUser = false
        }
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        client.auth.signOut()
    }

    override suspend fun resendVerificationEmail(email: String): Result<Unit> = runCatching {
        client.auth.resendEmail(OtpType.Email.SIGNUP, canonicalEmail(email))
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        client.auth.resetPasswordForEmail(canonicalEmail(email), "sanibonani://reset-password")
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        client.auth.updateUser { password = newPassword }
        Unit
    }

    override suspend fun handleDeepLink(url: String): Result<Unit> = runCatching {
        val session = client.auth.parseSessionFromUrl(url)
        client.auth.importSession(session)
    }

    override suspend fun getProfile(userId: String): UserProfile? = runCatching {
        client.postgrest["profiles"].select { filter { eq("id", userId) } }.decodeSingleOrNull<UserProfile>()
    }.getOrNull()

    override suspend fun resetLocalCache(): Result<Unit> = runCatching {
        db.clearAllData()
    }

    private suspend fun updateLoginAuditFields() {
        val userId = currentUserId ?: return
        runCatching {
            edgeFunctionGateway.invoke(
                functionName = "mobile-admin-actions",
                payload = buildJsonObject {
                    put("action", "record_login")
                    put("user_id", userId)
                    put("last_login_at", java.time.Instant.now().toString())
                }
            )
        }
    }
}
