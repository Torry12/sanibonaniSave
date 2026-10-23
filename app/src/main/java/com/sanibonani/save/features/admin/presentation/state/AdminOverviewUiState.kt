package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*

data class AdminOverviewUiState(
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val managedGroups: AsyncResult<List<Group>> = AsyncResult.Uninitialized,
    val metrics: ActuarialMetrics = ActuarialMetrics(),
    val businessInsight: Result<GroupFinancialInsight>? = null,
    val healthScore: GroupHealthScore? = null,
    val feeStatus: AdminFeeState = AdminFeeState.DUE,
    val daysOverdue: Int = 0,
    val viabilityPlan: ViabilityPlan? = null,
    val isCalculatingViability: Boolean = false,
    val notifications: List<AppNotification> = emptyList(),
    val memberMessages: List<AppNotification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,
    val verificationResult: VerificationResult = VerificationResult.Valid,
    val isSystemOnline: Boolean = true,
    val isAirplaneMode: Boolean = false,
    val messageText: String = "",
    val isSendingMessage: Boolean = false,
    val messageSentSuccess: Boolean = false,
    val selectedNotifChannel: NotifChannel = NotifChannel.BOTH,
    val syncMetrics: Map<String, RealtimeSyncStatus> = emptyMap(),
    val isSupportMode: Boolean = false,
    val isImpersonationBannerDismissed: Boolean = false,
    val currentUserId: String? = null,
    val groupAuditReport: AsyncResult<GroupAuditReport> = AsyncResult.Uninitialized,
    val platformRegistrationFee: Money = Money.ZERO,
    val outstandingPlatformFees: List<PlatformFee> = emptyList(),
    val isPayingPlatformFee: Boolean = false,
    val platformBankDetails: PlatformConfig = PlatformConfig()
) {
    fun clearGroupContext() = copy(
        group = null,
        members = emptyList(),
        metrics = ActuarialMetrics(),
        businessInsight = null,
        healthScore = null,
        notifications = emptyList(),
        memberMessages = emptyList(),
        isLoading = true,
        error = null,
        groupAuditReport = AsyncResult.Uninitialized
    )
}

data class AdminGroupContext(
    val group: Group,
    val members: List<Member>,
    val metrics: ActuarialMetrics,
    val notifications: List<AppNotification>,
    val messages: List<AppNotification>
)
