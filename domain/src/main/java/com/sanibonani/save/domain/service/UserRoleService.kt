package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.UserRole

interface UserRoleService {
    suspend fun getUserRole(): UserRole
    suspend fun updateUserRole(userId: String, role: UserRole, groupId: String? = null): Result<Unit>
    suspend fun hasOwnedGroup(userId: String): Boolean
}
