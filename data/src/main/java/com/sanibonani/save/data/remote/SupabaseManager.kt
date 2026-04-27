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

    override suspend fun signUp(
        email: String, 
        password: String, 
        metadata: Map<String, String>
    ): Result<String> = runCatching {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        
        try {
            val response = client.auth.signUpWith(Email) {
                this.email = trimmedEmail
                this.password = trimmedPassword
                if (metadata.isNotEmpty()) {
                    data = buildJsonObject {
                        metadata.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                    }
                }
            }
            response?.id ?: client.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Signup succeeded but user ID is missing.")
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val isUserExists = msg.contains("already registered", ignoreCase = true) || 
                             msg.contains("email exists", ignoreCase = true) ||
                             msg.contains("user already", ignoreCase = true) ||
                             msg.contains("400", ignoreCase = true) // 400 is common for "user already exists"

            if (isUserExists) {
                // If user exists, attempt to sign in to confirm ownership and get the ID
                try {
                    client.auth.signInWith(Email) {
                        this.email = trimmedEmail
                        this.password = trimmedPassword
                    }
                    client.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Sign-in succeeded but user ID is missing.")
                } catch (signInError: Exception) {
                    val siMsg = signInError.message.orEmpty()
                    if (siMsg.contains("invalid login credentials", ignoreCase = true)) {
                        throw IllegalStateException("This email is already registered with a different password.")
                    }
                    // If it's a server error or network error, provide a friendly message
                    val friendly = when {
                        siMsg.contains("500") -> "Server error. Please try again."
                        siMsg.contains("network", ignoreCase = true) -> "Network error. Please check your connection."
                        else -> siMsg.ifBlank { "Account registration failed. This email might already be in use." }
                    }
                    throw Exception(friendly)
                }
            } else {
                // Not an "already exists" error, just rethrow with user-friendly message if possible
                val userFriendlyMsg = when {
                    msg.contains("500") || msg.contains("Internal Server Error") -> "Server error while creating account. Please try again."
                    msg.contains("network", ignoreCase = true) -> "Network error. Please check your connection."
                    else -> msg
                }
                throw Exception(userFriendlyMsg)
            }
        }
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
        updateLoginAuditFields()
    }

    override suspend fun signInWithMagicLink(email: String): Result<Unit> = runCatching {
        client.auth.signInWith(OTP) {
            this.email = email
            createUser = false
        }
        updateLoginAuditFields()
    }

    /**
     * Updates the last_login_at and resets login_attempts for the current user.
     * This uses the admin client because the auth.users table is not directly writable by users.
     */
    private suspend fun updateLoginAuditFields() {
        val userId = currentUserId ?: return
        try {
            adminClient.auth.admin.updateUserById(userId) {
                userMetadata = buildJsonObject {
                    // We can't directly update 'last_login_at' column in auth.users via admin.updateUserById
                    // usually, as it only updates metadata.
                    // However, we can store it in user_metadata or call an RPC if we want it in the column.
                    // For now, let's update it in user_metadata and assume a trigger or RPC handles the audit column.
                    
                    val existingMeta = currentSession?.user?.userMetadata ?: buildJsonObject {}
                    existingMeta.forEach { (k, v) -> put(k, v) }
                    put("last_login_at", JsonPrimitive(kotlinx.datetime.Clock.System.now().toString()))
                    put("login_attempts", JsonPrimitive(0))
                }
            }
        } catch (e: Exception) {
            AppLogger.e("SupabaseManager", "Failed to update login audit fields", e)
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
        val user = currentSession?.user ?: return UserRole.MEMBER
        val userId = user.id
        val sessionEmail = user.email?.trim()?.lowercase()

        // 1. Priority check: Hardcoded platform admin email policy
        if (PlatformAdminAuthPolicy.isPlatformAdminEmail(sessionEmail)) {
            AppLogger.d("SupabaseManager", "Role resolved to PLATFORM_ADMIN via email policy: $sessionEmail")
            return UserRole.PLATFORM_ADMIN
        }

        // 2. Check metadata role (set during signup/migration)
        val metadataRole = user
            .userMetadata
            ?.get("role")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let(UserRoleMapper::fromRaw)

        if (metadataRole == UserRole.PLATFORM_ADMIN) {
            return UserRole.PLATFORM_ADMIN
        }

        // 3. Check profile table (authoritative DB-side role)
        val profileRole = runCatching {
            client.postgrest["profiles"].select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<ProfileResponse>()?.role?.let(UserRoleMapper::fromRaw)
        }.onFailure { e ->
            AppLogger.w(
                tag = "SupabaseManager",
                message = "Role resolution via profile failed for userId=$userId. Falling back to metadata.",
                throwable = e
            )
        }.getOrNull()

        // 4. Check app metadata (alternative claim storage)
        val appMetadataRole = user
            ?.appMetadata
            ?.get("role")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let(UserRoleMapper::fromRaw)

        val resolved = profileRole ?: metadataRole ?: appMetadataRole ?: UserRole.MEMBER

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
