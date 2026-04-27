package com.sanibonani.save.data.remote

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.utils.AdminClient
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import com.sanibonani.save.domain.utils.UserRoleMapper
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.admin.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromUrl
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SupabaseRepository for authentication and user management.
 * Handles all login, sign-up, and session logic for the app.
 * All error handling is user-friendly and robust.
 */
@Singleton
class SupabaseManager @Inject constructor(
    val client: SupabaseClient,
    @AdminClient val adminClient: SupabaseClient,
    private val db: SanibonaniDatabase
) : SupabaseRepository {
    override val currentSession: UserSession? get() = client.auth.currentSessionOrNull()
    
    override val currentUserId: String? get() = currentSession?.user?.id
    
    override val currentSessionEmail: String? get() = currentSession?.user?.email

    override val accessToken: String? get() = currentSession?.accessToken

    override val supabaseUrl: String get() = client.supabaseUrl
    
    override val isLoggedIn: Boolean get() = currentSession?.accessToken?.isNotBlank() == true

    override suspend fun resetLocalCache() {
        db.clearAllData()
    }

    // Reactive flow for session status changes
    override val sessionStatus: Flow<SessionStatus> = client.auth.sessionStatus
    
    override val sessionFlow: Flow<UserSession?> = client.auth.sessionStatus.map { status ->
        (status as? SessionStatus.Authenticated)?.session
    }

    /**
     * Attempts to sign up a new user. If the email is already registered, throws a user-friendly error.
     * If a server error occurs, throws a user-friendly error. Attempts fallback sign-in if needed.
     */
    override suspend fun signUp(
        email: String, 
        password: String, 
        metadata: Map<String, String>
    ): Result<String> = runCatching {
        var userId: String? = null
        try {
            val response = client.auth.signUpWith(Email) {
                this.email    = email
                this.password = password
                if (metadata.isNotEmpty()) {
                    data = buildJsonObject {
                        metadata.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                    }
                }
            }
            userId = response?.id ?: client.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            val msg = e.message ?: ""
            // Some Supabase auth failures return opaque "Unknown Error URL ... /auth/v1/signup" payloads.
            // Treat these as possible duplicate-account cases and attempt sign-in with provided credentials.
            val looksLikeExistingAccount =
                msg.contains("already registered", ignoreCase = true) ||
                    msg.contains("user already", ignoreCase = true) ||
                    msg.contains("already been registered", ignoreCase = true) ||
                    msg.contains("email exists", ignoreCase = true) ||
                    (msg.contains("unknown error", ignoreCase = true) && msg.contains("/auth/v1/signup", ignoreCase = true)) ||
                    (msg.contains("unprocessable", ignoreCase = true) && msg.contains("/auth/v1/signup", ignoreCase = true))
            val looksLikeServerError =
                msg.contains("500", ignoreCase = true) ||
                    msg.contains("Internal Server Error", ignoreCase = true)

            if (looksLikeExistingAccount || looksLikeServerError) {
                // Try to sign in to see if the user actually exists
                try {
                    client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    userId = client.auth.currentUserOrNull()?.id
                } catch (signInError: Exception) {
                    val signInMsg = signInError.message.orEmpty()
                    if (looksLikeExistingAccount || signInMsg.contains("invalid login credentials", ignoreCase = true)) {
                        throw IllegalStateException("This email is already registered. Please sign in or reset your password.")
                    }
                    // If sign in fails and this looked like a backend issue, keep message user-friendly.
                    if (looksLikeServerError) {
                        throw IllegalStateException("Server error while creating account. Please try again.")
                    }
                    throw e
                }
            } else {
                throw e
            }
        }

        // Fallback logic: try to resolve userId if not set
        if (userId == null) {
            userId = client.auth.currentSessionOrNull()?.user?.id ?: client.auth.currentUserOrNull()?.id
        }

        if (userId == null) {
            try {
                client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                repeat(3) {
                    delay(400)
                    userId = client.auth.currentSessionOrNull()?.user?.id ?: client.auth.currentUserOrNull()?.id
                    if (userId != null) return@repeat
                }
            } catch (signInError: Exception) {
                val msg = signInError.message ?: ""
                if (msg.contains("invalid login credentials", ignoreCase = true)) {
                    throw IllegalStateException("Email already registered with different password.")
                }
                throw signInError
            }
        }

        userId ?: throw IllegalStateException("Failed to obtain user ID.")
    }

    override suspend fun adminSignUp(
        email: String,
        password: String,
        metadata: Map<String, String>,
        confirm: Boolean
    ): Result<String> = runCatching {
        val response = adminClient.auth.admin.createUserWithEmail {
            this.email = email
            this.password = password
            autoConfirm = confirm
            if (metadata.isNotEmpty()) {
                userMetadata = buildJsonObject {
                    metadata.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                }
            }
        }
        response.id
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email    = email
            this.password = password
        }
    }

    override suspend fun signInWithMagicLink(email: String): Result<Unit> = runCatching {
        client.auth.signInWith(OTP) {
            this.email = email
            createUser = false
        }
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        client.auth.signOut()
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        client.auth.resetPasswordForEmail(
            email = email,
            redirectUrl = "sanibonani://reset-password"
        )
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        // Ensure we have a valid session before attempting update
        val session = client.auth.currentSessionOrNull() 
            ?: throw IllegalStateException("Your session has expired or is invalid. Please request a new reset link.")
            
        if (session.accessToken.isBlank()) {
            throw IllegalStateException("Authentication token is missing. Please try the reset link again.")
        }

        client.auth.updateUser {
            password = newPassword
        }
    }

    override suspend fun handleDeepLink(url: String): Result<Unit> = runCatching {
        client.auth.parseSessionFromUrl(url)
    }

    override suspend fun getUserRole(): UserRole {
        val userId = currentSession?.user?.id ?: return UserRole.MEMBER
        val sessionEmail = currentSession?.user?.email?.trim()?.lowercase()

        val user = currentSession?.user
        val metadataRole = user
            ?.userMetadata
            ?.get("role")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let(UserRoleMapper::fromRaw)

        // Some Supabase setups store custom claims in app metadata instead of user metadata.
        val appMetadataRole = user
            ?.appMetadata
            ?.get("role")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let(UserRoleMapper::fromRaw)

        val profileRole = runCatching {
            client.postgrest["profiles"].select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<ProfileResponse>()?.role?.let(UserRoleMapper::fromRaw)
        }.onFailure { e ->
            AppLogger.w(
                tag = "SupabaseManager",
                message = "Role resolution via profile failed for userId=$userId.",
                throwable = e
            )
        }.getOrNull()

        val resolved = when {
            PlatformAdminAuthPolicy.isPlatformAdminEmail(sessionEmail) -> UserRole.PLATFORM_ADMIN
            else -> profileRole ?: metadataRole ?: appMetadataRole ?: UserRole.MEMBER
        }

        AppLogger.d(
            tag = "SupabaseManager",
            message = "Resolved role userId=$userId email=$sessionEmail profileRole=$profileRole metadataRole=$metadataRole appMetadataRole=$appMetadataRole resolved=$resolved"
        )

        return resolved
    }

    @kotlinx.serialization.Serializable
    private data class ProfileResponse(
        val id: String,
        val role: String? = null
    )

    override suspend fun updateUserRole(userId: String, role: UserRole, groupId: String?): Result<Unit> = runCatching {
        val isSelf = currentUserId == userId

        // Prefer self-service metadata updates when the target user is the current user.
        // This avoids a hard dependency on the service_role key for normal in-app flows.
        if (isSelf) {
            val existingMeta = currentSession?.user?.userMetadata ?: buildJsonObject {}
            val updatedMeta = buildJsonObject {
                // Preserve existing metadata
                existingMeta.forEach { (k, v) -> put(k, v) }

                // Update role
                put("role", UserRoleMapper.toStorageValue(role))

                // Add/update group admin claims
                if (role == UserRole.GROUP_ADMIN && groupId != null) {
                    val currentGroups = (existingMeta["admin_for_groups"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: emptyList())
                        .toMutableSet()
                    currentGroups.add(groupId)
                    put("admin_for_groups", JsonArray(currentGroups.map { JsonPrimitive(it) }))
                }
            }

            client.auth.updateUser {
                data = updatedMeta
            }
            return@runCatching
        }

        // Fallback: admin API for updating other users (requires service_role key).
        adminClient.auth.admin.updateUserById(userId) {
            userMetadata = buildJsonObject {
                // Preserve existing metadata
                val existingMeta = this@updateUserById.userMetadata ?: buildJsonObject {}
                existingMeta.forEach { (k, v) -> put(k, v) }

                // Update role
                put("role", UserRoleMapper.toStorageValue(role))

                // Add/update group admin claims
                if (role == UserRole.GROUP_ADMIN && groupId != null) {
                    val currentGroups = (existingMeta["admin_for_groups"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: emptyList())
                        .toMutableSet()
                    currentGroups.add(groupId)
                    put("admin_for_groups", JsonArray(currentGroups.map { JsonPrimitive(it) }))
                }
            }
        }
    }
}
