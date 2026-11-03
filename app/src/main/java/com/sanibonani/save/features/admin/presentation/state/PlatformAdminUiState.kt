package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*

data class PlatformAdminUiState(
    val isAuthorized: Boolean = false,
    val selectedTab: Int = 0,
    val syncMetrics: Map<String, RealtimeSyncStatus> = emptyMap(),
    val showMfaChallenge: Boolean = false,
    val mfaChallengeError: String? = null,
    val isVerifyingMfa: Boolean = false
)
