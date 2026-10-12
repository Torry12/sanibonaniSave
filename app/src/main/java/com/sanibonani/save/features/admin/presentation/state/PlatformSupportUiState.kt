package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*

data class PlatformSupportUiState(
    val impersonationGroupId: String? = null,
    val impersonationMembers: List<Member> = emptyList(),
    val isLoadingImpersonationMembers: Boolean = false,
    val searchQuery: String = "",
    val memberBehaviorInsights: List<MemberBehaviorInsight> = emptyList(),
    val broadcastMessage: String = "",
    val isBroadcasting: Boolean = false,
    val broadcastSuccess: Boolean = false
)
