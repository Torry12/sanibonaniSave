package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase

data class AdminOverviewUiState(
    val groupContext: AsyncResult<AdminGroupContext> = AsyncResult.Uninitialized,
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val managedGroups: AsyncResult<List<Group>> = AsyncResult.Uninitialized,
    val metrics: ActuarialMetrics = ActuarialMetrics(),
    val businessInsight: GetGroupBusinessInsightsUseCase.GroupBusinessInsight = GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty,
    val healthScore: GroupHealthScore? = null,
    val feeStatus: AdminFeeState = AdminFeeState.DUE,
    val daysOverdue: Int = 0,
    val restoreRequested: Boolean = false,
    val viabilityPlan: ViabilityPlan? = null,
    val isCalculatingViability: Boolean = false,
    val notifications: List<AppNotification> = emptyList(),
    val memberMessages: List<AppNotification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val selectedTab: Int = 0,
    val verificationResult: VerificationResult = VerificationResult.Valid,
    val isSystemOnline: Boolean = true,
    val isAirplaneMode: Boolean = false,
    val messageText: String = "",
    val isSendingMessage: Boolean = false,
    val messageSentSuccess: Boolean = false,
    val selectedNotifChannel: NotifChannel = NotifChannel.BOTH,
    val syncMetrics: Map<String, com.sanibonani.save.data.sync.RealtimeSyncManager.SyncStatus> = emptyMap(),
    val isSupportMode: Boolean = false,
    val isImpersonationBannerDismissed: Boolean = false,
    val currentUserId: String? = null,
    val groupAuditReport: AsyncResult<GroupAuditReport> = AsyncResult.Uninitialized,
    val isLoadingAuditReport: Boolean = false
)

data class AdminGroupContext(
    val group: Group,
    val members: List<Member>,
    val metrics: ActuarialMetrics,
    val notifications: List<AppNotification>,
    val messages: List<AppNotification>
)
