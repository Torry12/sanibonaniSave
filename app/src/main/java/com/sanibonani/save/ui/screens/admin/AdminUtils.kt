package com.sanibonani.save.ui.screens.admin

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member

data class AdminMemberPortalAccess(
    val enabled: Boolean,
    val groupId: String?,
    val memberId: String?,
    val title: String,
    val description: String,
    val buttonLabel: String
)

internal fun resolveAdminMemberPortalAccess(
    group: Group?,
    managedGroups: List<Group>,
    selectedMember: Member?,
    supportActive: Boolean
): AdminMemberPortalAccess {
    val groupId = group?.id
        ?: managedGroups.firstOrNull { !it.id.isNullOrBlank() }?.id

    if (groupId.isNullOrBlank()) {
        return AdminMemberPortalAccess(
            enabled = false,
            groupId = null,
            memberId = null,
            title = if (supportActive) "Member portal unavailable" else "Switch to Member View",
            description = "Group data not yet synchronized.",
            buttonLabel = "Switch to Member Portal"
        )
    }

    if (!supportActive) {
        return AdminMemberPortalAccess(
            enabled = true,
            groupId = groupId,
            memberId = null,
            title = "Switch to Member View",
            description = "Access your own contributions and profile as a group member.",
            buttonLabel = "Switch to Member Portal"
        )
    }

    // Support mode logic
    if (selectedMember != null) {
        val memberName = selectedMember.fullName.ifBlank { "selected member" }
        return AdminMemberPortalAccess(
            enabled = true,
            groupId = groupId,
            memberId = selectedMember.id,
            title = "Impersonate $memberName",
            description = "View the portal exactly as this member sees it.",
            buttonLabel = "Impersonate Member"
        )
    }

    return AdminMemberPortalAccess(
        enabled = false,
        groupId = groupId,
        memberId = null,
        title = "Member portal unavailable",
        description = "In support mode, please select a member from the list first.",
        buttonLabel = "Switch to Member Portal"
    )
}
