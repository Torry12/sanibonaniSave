package com.sanibonani.save.data.remote

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import com.sanibonani.save.domain.utils.UserRoleMapper
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromUrl
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import java.io.IOException
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
    private val db: SanibonaniDatabase,
    private val edgeFunctionGateway: EdgeFunctionGateway
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
                    msg.contains("network", ignoreCase = true) || e is IOException -> "Network error. Please check your connection."
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
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()

        edgeFunctionGateway.invoke(
            functionName = "mobile-admin-actions",
            payload = buildJsonObject {
                put("action", "admin_sign_up")
                put("email", trimmedEmail)
                put("password", trimmedPassword)
                put("confirm", confirm)
                put("metadata", buildJsonObject {
                    metadata.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                })
            }
        ).getOrThrow()["user_id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Admin account creation succeeded but no user ID was returned.")
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        AppLogger.d("SupabaseManager", "🔄 Attempting sign-in for: $email")
        client.auth.signInWith(Email) {
            this.email    = email
            this.password = password
        }
        AppLogger.d("SupabaseManager", "✅ Sign-in successful for: $email")
        updateLoginAuditFields()
    }

    override suspend fun signInWithMagicLink(email: String): Result<Unit> = runCatching {
        client.auth.signInWith(OTP) {
            this.email = email
            createUser = false
        }
        updateLoginAuditFields()
    }

    private suspend fun updateLoginAuditFields() {
        val userId = currentUserId ?: return
        try {
            edgeFunctionGateway.invoke(
                functionName = "mobile-admin-actions",
                payload = buildJsonObject {
                    put("action", "record_login")
                    put("user_id", userId)
                    put("last_login_at", kotlinx.datetime.Clock.System.now().toString())
                }
            ).getOrThrow()
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
        AppLogger.d("SupabaseManager", "🔄 Handling deep link: $url")
        val session = client.auth.parseSessionFromUrl(url)
        client.auth.importSession(session)
        AppLogger.d("SupabaseManager", "✅ Deep link session imported successfully")
    }

    override suspend fun getUserRole(): UserRole {
        val user = currentSession?.user ?: return UserRole.MEMBER
        val userId = user.id
        val sessionEmail = user.email?.trim()?.lowercase()
        val isCanonicalPlatformAdminEmail = PlatformAdminAuthPolicy.isPlatformAdminEmail(sessionEmail)

        if (PlatformAdminAuthPolicy.ASSUME_ALL_AUTH_USERS_ARE_PLATFORM_ADMIN) {
            if (!isCanonicalPlatformAdminEmail) {
                AppLogger.d(
                    tag = "SupabaseManager",
                    message = "Feature flag enabled but user is not canonical platform admin email; no elevation. userId=$userId email=$sessionEmail"
                )
            } else {
                AppLogger.d(
                    tag = "SupabaseManager",
                    message = "Role resolved to PLATFORM_ADMIN by feature flag for canonical platform admin email: userId=$userId email=$sessionEmail"
                )
                return UserRole.PLATFORM_ADMIN
            }
        }

        // 1. Priority check: hardcoded platform admin email policy
        if (isCanonicalPlatformAdminEmail) {
            AppLogger.d(
                tag = "SupabaseManager",
                message = "Role resolved to PLATFORM_ADMIN via email policy: $sessionEmail"
            )
            return UserRole.PLATFORM_ADMIN
        }

        // 2. Check metadata role (set during signup/migration)
        val metadataRole = user
            .userMetadata
            ?.get("role")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let(UserRoleMapper::fromRaw)

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
            .appMetadata
            ?.get("role")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let(UserRoleMapper::fromRaw)

        val resolved = profileRole ?: metadataRole ?: appMetadataRole ?: UserRole.MEMBER

        val hasOwnedGroup = if (resolved == UserRole.GROUP_ADMIN) {
            runCatching {
                client.postgrest["groups"].select(columns = Columns.list("id")) {
                    filter { eq("admin_user_id", userId) }
                    limit(1)
                }.decodeList<GroupOwnershipRow>().isNotEmpty()
            }.onFailure { e ->
                AppLogger.w(
                    tag = "SupabaseManager",
                    message = "Failed to verify group-admin ownership for userId=$userId. Keeping resolved role until connectivity improves.",
                    throwable = e
                )
            }.getOrDefault(true)
        } else {
            false
        }

        // Guardrail: only the configured canonical email can ever be treated as PLATFORM_ADMIN.
        if (resolved == UserRole.PLATFORM_ADMIN && !isCanonicalPlatformAdminEmail) {
            val fallback = profileRole
                .takeIf { it != UserRole.PLATFORM_ADMIN }
                ?: metadataRole.takeIf { it != UserRole.PLATFORM_ADMIN }
                ?: appMetadataRole.takeIf { it != UserRole.PLATFORM_ADMIN }
                ?: UserRole.MEMBER

            AppLogger.w(
                tag = "SupabaseManager",
                message = "Rejected PLATFORM_ADMIN resolution for non-canonical email. userId=$userId email=$sessionEmail fallback=$fallback"
            )
            return fallback
        }

        if (resolved == UserRole.GROUP_ADMIN && !hasOwnedGroup) {
            AppLogger.w(
                tag = "SupabaseManager",
                message = "Downgrading stale GROUP_ADMIN role to MEMBER because no owned groups were found. userId=$userId email=$sessionEmail"
            )
            return UserRole.MEMBER
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

    @kotlinx.serialization.Serializable
    private data class GroupOwnershipRow(
        val id: String
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

        edgeFunctionGateway.invoke(
            functionName = "mobile-admin-actions",
            payload = buildJsonObject {
                put("action", "update_user_role")
                put("user_id", userId)
                put("role", UserRoleMapper.toStorageValue(role))
                groupId?.let { put("group_id", it) }
            }
        ).getOrThrow()
    }
}
