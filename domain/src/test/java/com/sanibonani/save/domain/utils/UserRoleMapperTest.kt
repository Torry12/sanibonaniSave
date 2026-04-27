package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserRoleMapperTest {

    @Test
    fun fromRaw_parsesPlatformAdminAliases() {
        assertEquals(UserRole.PLATFORM_ADMIN, UserRoleMapper.fromRaw("platform_admin"))
        assertEquals(UserRole.PLATFORM_ADMIN, UserRoleMapper.fromRaw("platform-admin"))
        assertEquals(UserRole.PLATFORM_ADMIN, UserRoleMapper.fromRaw(" platformadmin "))
    }

    @Test
    fun fromRaw_parsesGroupAdminAliases() {
        assertEquals(UserRole.GROUP_ADMIN, UserRoleMapper.fromRaw("group_admin"))
        assertEquals(UserRole.GROUP_ADMIN, UserRoleMapper.fromRaw("group-admin"))
        assertEquals(UserRole.GROUP_ADMIN, UserRoleMapper.fromRaw("groupadmin"))
    }

    @Test
    fun fromRaw_returnsNullForUnknownValue() {
        assertNull(UserRoleMapper.fromRaw("admin"))
        assertNull(UserRoleMapper.fromRaw(null))
    }

    @Test
    fun toStorageValue_returnsCanonicalValues() {
        assertEquals("platform_admin", UserRoleMapper.toStorageValue(UserRole.PLATFORM_ADMIN))
        assertEquals("group_admin", UserRoleMapper.toStorageValue(UserRole.GROUP_ADMIN))
        assertEquals("member", UserRoleMapper.toStorageValue(UserRole.MEMBER))
    }
}

