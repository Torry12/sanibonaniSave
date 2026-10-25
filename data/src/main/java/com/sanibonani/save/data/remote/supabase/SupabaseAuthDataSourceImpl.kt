package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.AuthDataSource
import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.domain.model.AuthSession
import com.sanibonani.save.domain.model.AuthSessionStatus
import com.sanibonani.save.domain.model.MfaFactor
import com.sanibonani.save.domain.model.TotpEnrollment
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.utils.AuthIdentityUtils
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import com.sanibonani.save.domain.utils.UserRoleMapper
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.parseSessionFromUrl
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class SupabaseAuthDataSourceImpl @Inject constructor(
    protocol: DataConnectionProtocol,
    private val edgeFunctionGateway: EdgeFunctionGateway
) : BaseSupabaseDataSource(protocol), AuthDataSource {

    private fun canonicalEmail(email: String): String = AuthIdentityUtils.normalizeEmail(email)

    /**
     * Raw SDK session. Used for the derived fields below; the domain-facing
     * [currentSession] / [sessionFlow] / [sessionStatus] expose mapped
     * [AuthSession] / [AuthSessionStatus] values so the Supabase SDK never
     * crosses the module boundary.
     */
    private val rawSession: UserSession? get() = client.auth.currentSessionOrNull()

    override val currentSession: AuthSession? get() = rawSession?.toAuthSession()
    override val currentUserId: String? get() = rawSession?.user?.id
    override val currentSessionEmail: String? get() = rawSession?.user?.email
    override val accessToken: String? get() = rawSession?.accessToken
    override val supabaseUrl: String get() = client.supabaseUrl
    override val isLoggedIn: Boolean get() = rawSession?.accessToken?.isNotBlank() == true
    override val isMfaVerified: Boolean
        get() = rawSession?.user?.appMetadata?.get("aal")?.jsonPrimitive?.contentOrNull == "aal2"

    override val sessionStatus: Flow<AuthSessionStatus> = client.auth.sessionStatus.map { it.toAuthSessionStatus() }
    override val sessionFlow: Flow<AuthSession?> = client.auth.sessionStatus.map { status ->
        // Only Authenticated carries a session in the status object. Initialising and
        // refreshing are transient states that still hold a live session, so the SDK's
        // current session is reused there; emitting null instead would log the user out
        // for a moment and wipe the navigation back stack.
        val session = if (status is SessionStatus.Authenticated) status.session else rawSession
        session?.takeIf { it.accessToken.isNotBlank() }?.toAuthSession()
    }

    private fun UserSession.toAuthSession(): AuthSession = AuthSession(
        userId = user?.id ?: "",
        email = user?.email,
        accessToken = accessToken
    )

    private fun SessionStatus.toAuthSessionStatus(): AuthSessionStatus = when (this) {
        is SessionStatus.Authenticated -> AuthSessionStatus.Authenticated(session.toAuthSession())
        else -> AuthSessionStatus.NotAuthenticated()
    }

    override suspend fun signUp(email: String, password: String, metadata: Map<String, String>): Result<String> = safeRunCatching {
        val normalizedEmail = canonicalEmail(email)
        try {
            val response = client.auth.signUpWith(Email) {
                this.email = normalizedEmail
                this.password = password
                if (metadata.isNotEmpty()) {
                    data = buildJsonObject {
                        metadata.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                    }
                }
            }
            response?.id ?: client.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Signup succeeded but user ID is missing.")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val msg = e.message.orEmpty()
            if (msg.contains("already registered", ignoreCase = true) || msg.contains("400")) {
                client.auth.signInWith(Email) {
                    this.email = normalizedEmail
                    this.password = password
                }
                client.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Sign-in succeeded but user ID is missing.")
            } else throw e
        }
    }

    override suspend fun adminSignUp(email: String, password: String, metadata: Map<String, String>, confirm: Boolean): Result<String> = safeRunCatching {
        edgeFunctionGateway.invoke(
            functionName = "mobile-admin-actions",
            payload = buildJsonObject {
                put("action", "admin_sign_up")
                put("email", canonicalEmail(email))
                put("password", password)
                put("confirm", confirm)
                put("metadata", buildJsonObject {
                    metadata.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                })
            }
        ).getOrThrow()["user_id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Admin account creation succeeded but no user ID was returned.")
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = safeRunCatching {
        client.auth.signInWith(Email) {
            this.email = canonicalEmail(email)
            this.password = password
        }
    }

    override suspend fun signInWithMagicLink(email: String): Result<Unit> = safeRunCatching {
        client.auth.signInWith(OTP) {
            this.email = canonicalEmail(email)
            createUser = false
        }
    }

    override suspend fun signOut(): Result<Unit> = safeRunCatching {
        client.auth.signOut()
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = safeRunCatching {
        client.auth.resetPasswordForEmail(
            email = canonicalEmail(email),
            redirectUrl = "sanibonani://reset-password"
        )
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> = safeRunCatching {
        client.auth.updateUser { password = newPassword }
    }

    override suspend fun handleDeepLink(url: String): Result<Unit> = safeRunCatching {
        val session = client.auth.parseSessionFromUrl(url)
        client.auth.importSession(session)
    }

    override suspend fun getUserRole(): UserRole {
        val user = rawSession?.user ?: return UserRole.MEMBER
        val userId = user.id
        val sessionEmail = user.email?.trim()?.lowercase()

        if (PlatformAdminAuthPolicy.isPlatformAdminEmail(sessionEmail)) return UserRole.PLATFORM_ADMIN

        val metadataRole = user.userMetadata?.get("role")?.jsonPrimitive?.contentOrNull?.let(UserRoleMapper::fromRaw)

        val profileRole = safeRunCatching {
            client.postgrest["profiles"].select { filter { eq("id", userId) } }.decodeSingleOrNull<ProfileResponse>()?.role?.let(UserRoleMapper::fromRaw)
        }.getOrNull()

        return profileRole ?: metadataRole ?: UserRole.MEMBER
    }

    @Serializable
    private data class ProfileResponse(val id: String, val role: String? = null)

    override suspend fun updateUserRole(userId: String, role: UserRole, groupId: String?): Result<Unit> = safeRunCatching {
        if (currentUserId == userId) {
            val storageRole = UserRoleMapper.toStorageValue(role)
            val existingMeta = rawSession?.user?.userMetadata ?: buildJsonObject {}
            val updatedMeta = buildJsonObject {
                existingMeta.forEach { (k, v) -> put(k, v) }
                put("role", storageRole)
                if (role == UserRole.GROUP_ADMIN && groupId != null) {
                    val currentGroups = (existingMeta["admin_for_groups"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()).toMutableSet()
                    currentGroups.add(groupId)
                    put("admin_for_groups", JsonArray(currentGroups.map { JsonPrimitive(it) }))
                }
            }
            // [getUserRole] trusts profiles.role above the user metadata, so updating the token
            // claims alone leaves the account on its old role (and a new group admin stranded).
            client.postgrest["profiles"].update(mapOf("role" to storageRole)) {
                filter { eq("id", userId) }
            }
            client.auth.updateUser { data = updatedMeta }
        } else {
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

    override suspend fun getMfaFactors(): Result<List<MfaFactor>> = safeRunCatching {
        client.auth.currentUserOrNull()?.factors?.map {
            MfaFactor(id = it.id, friendlyName = it.friendlyName ?: "Unnamed", type = it.factorType.toString(), status = "active")
        } ?: emptyList()
    }

    override suspend fun verifyMfa(factorId: String, code: String, saveSession: Boolean): Result<Unit> = safeRunCatching {
        val challenge = client.auth.mfa.createChallenge(factorId)
        client.auth.mfa.verifyChallenge(factorId, challenge.id, code, saveSession)
    }

    override suspend fun enrollMfa(): Result<TotpEnrollment> = safeRunCatching {
        val response = client.auth.mfa.enroll(FactorType.TOTP)
        TotpEnrollment(id = response.id, qrCode = response.data.qrCode, secret = response.data.secret, uri = response.data.uri)
    }

    override suspend fun resendVerificationEmail(email: String): Result<Unit> = safeRunCatching {
        client.auth.resendEmail(type = io.github.jan.supabase.auth.OtpType.Email.SIGNUP, email = canonicalEmail(email))
    }
}
