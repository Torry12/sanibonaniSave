package com.sanibonani.save.data.service

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.service.UserRoleService
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import com.sanibonani.save.domain.utils.UserRoleMapper
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class UserRoleServiceImpl @Inject constructor(
    private val connectionProtocol: DataConnectionProtocol
) : UserRoleService {

    private val client: SupabaseClient get() = connectionProtocol.getRemoteClient(SupabaseClient::class.java)!!

    override suspend fun getUserRole(): UserRole {
        val user = client.auth.currentSessionOrNull()?.user ?: return UserRole.MEMBER
        val sessionEmail = user.email?.trim()?.lowercase()

        val isAuthorizedPlatformAdmin = PlatformAdminAuthPolicy.isPlatformAdminEmail(sessionEmail)

        // Metadata & Database Role Resolution
        val metadataRole = user.userMetadata?.get("role")?.jsonPrimitive?.contentOrNull?.let(UserRoleMapper::fromRaw)

        val profileRole = runCatching {
            withTimeoutOrNull(5.seconds) {
                client.postgrest["profiles"].select {
                    filter { eq("id", user.id) }
                }.decodeSingleOrNull<ProfileResponse>()?.role?.let(UserRoleMapper::fromRaw)
            }
        }.getOrNull()

        val resolved = profileRole ?: metadataRole ?: UserRole.MEMBER

        // 🛡️ SECURITY ENFORCEMENT: Under no conditions can any user who is not platform admin ever log in as platform admin!
        if (resolved == UserRole.PLATFORM_ADMIN && !isAuthorizedPlatformAdmin) {
            AppLogger.w("UserRoleService", "SECURITY ALERT: Unauthorized attempt to assume platform admin role by $sessionEmail. Reverting to MEMBER.")
            return UserRole.MEMBER
        }

        if (isAuthorizedPlatformAdmin) {
            return UserRole.PLATFORM_ADMIN
        }

        // Group Admin Verification
        if (resolved == UserRole.GROUP_ADMIN && !hasOwnedGroup(user.id)) {
            return UserRole.MEMBER
        }

        return resolved
    }

    override suspend fun updateUserRole(userId: String, role: UserRole, groupId: String?): Result<Unit> = runCatching {
        client.postgrest["profiles"].update(mapOf("role" to UserRoleMapper.toStorageValue(role))) {
            filter { eq("id", userId) }
        }
    }

    override suspend fun hasOwnedGroup(userId: String): Boolean = runCatching {
        withTimeoutOrNull(5.seconds) {
            client.postgrest["groups"].select(columns = Columns.list("id")) {
                filter { eq("admin_user_id", userId) }
                limit(1)
            }.decodeList<GroupRow>().isNotEmpty()
        }
    }.getOrNull() ?: false

    @Serializable
    private data class ProfileResponse(val role: String? = null)
    @Serializable
    private data class GroupRow(val id: String)
}
