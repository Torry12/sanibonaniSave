package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.model.UserRole

object UserRoleMapper {
    private val platformAdminAliases = setOf("platform_admin", "platform-admin", "platformadmin")
    private val groupAdminAliases = setOf("group_admin", "group-admin", "groupadmin")

    fun fromRaw(rawRole: String?): UserRole? {
        val normalized = rawRole
            ?.trim()
            ?.lowercase()
            ?.replace('-', '_')
            ?: return null

        return when (normalized) {
            "platform_admin", "platformadmin" -> UserRole.PLATFORM_ADMIN
            "group_admin", "groupadmin" -> UserRole.GROUP_ADMIN
            "member" -> UserRole.MEMBER
            else -> null
        }
    }

    fun toStorageValue(role: UserRole): String {
        return when (role) {
            UserRole.PLATFORM_ADMIN -> "platform_admin"
            UserRole.GROUP_ADMIN -> "group_admin"
            UserRole.MEMBER -> "member"
        }
    }

    fun isPlatformAdminAlias(rawRole: String?): Boolean {
        return rawRole?.trim()?.lowercase() in platformAdminAliases
    }

    fun isGroupAdminAlias(rawRole: String?): Boolean {
        return rawRole?.trim()?.lowercase() in groupAdminAliases
    }
}

