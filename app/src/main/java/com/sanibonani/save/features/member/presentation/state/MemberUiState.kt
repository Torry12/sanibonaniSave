package com.sanibonani.save.features.member.presentation.state

import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.model.SyncStatus
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.domain.service.LocationAddress
import java.io.File

/**
 * Represents the UI state for the Member Portal.
 */
data class MemberUiState(
    // Core Context (Refactored)
    val member: Member? = null,
    val group: Group? = null,
    val userRole: UserRole = UserRole.MEMBER,
    val selectedGroupId: String? = null,

    // Financial data
    val contributions: List<Contribution> = emptyList(),
    val memberLedger: List<LedgerEntry> = emptyList(),
    val paymentCalculation: PaymentCalculation? = null,
    val monthlyContributionAmount: Money = Money.ZERO,

    // Beneficiaries
    val beneficiaries: List<Beneficiary> = emptyList(),

    // Documents
    val documents: List<MemberDocument> = emptyList(),

    // Loans
    val loans: List<Loan> = emptyList(),

    // Burial Society Claims
    val burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val payouts: List<PayoutRequest> = emptyList(),
    val isSubmittingClaim: Boolean = false,

    // Multi-group support
    val memberships: List<Member> = emptyList(),

    // Communication
    val notifications: List<AppNotification> = emptyList(),
    val messages: List<AppNotification> = emptyList(),

    // UI state
    val selectedTab: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),

    // Document upload state
    val uploadProgress: Double? = null,
    val isUploading: Boolean = false,
    val profileImageVersion: Long = 0L,

    // Export state
    val isExporting: Boolean = false,
    val exportFile: File? = null,

    // Messaging state
    val messageText: String = "",
    val isSendingMessage: Boolean = false,
    val messageSentSuccess: Boolean = false,

    // Security
    val biometricEnabled: Boolean = false,

    // Verification
    val verificationResult: VerificationResult = VerificationResult.Valid,
    val cacheLastSyncByGroup: Map<String, Long> = emptyMap(),
    val isSystemOnline: Boolean = true,
    val isAirplaneMode: Boolean = false,

    // Support / Impersonation
    val isImpersonating: Boolean = false,
    val impersonatorId: String? = null,
    val targetMemberId: String? = null,
    val isImpersonationBannerDismissed: Boolean = false,
    val isExiting: Boolean = false,

    // Insights
    val businessInsight: Result<GroupFinancialInsight>? = null,

    // Audit Report
    val memberAuditReport: AsyncResult<MemberAuditReport> = AsyncResult.Uninitialized,

    // Global Sync
    val syncStatus: SyncStatus = SyncStatus.Idle
) {
    fun clearGroupContext() = copy(
        member = null,
        group = null,
        contributions = emptyList(),
        memberLedger = emptyList(),
        paymentCalculation = null,
        beneficiaries = emptyList(),
        documents = emptyList(),
        loans = emptyList(),
        notifications = emptyList(),
        messages = emptyList(),
        isLoading = true,
        error = null
    )

    val totalSavings: Money
        get() {
            if (member?.totalPaid?.isPositive == true) return member.totalPaid
            val contribSum = contributions.filter { it.status == ContributionStatus.PAID }.fold(Money.ZERO) { acc, c -> acc + c.amount }
            if (contribSum.isPositive) return contribSum
            return memberLedger.filter { it.amount.isPositive }.fold(Money.ZERO) { acc, e -> acc + e.amount }
        }
}

data class MemberContextPackage(
    val member: Member,
    val group: Group,
    val contributions: List<Contribution>,
    val beneficiaries: List<Beneficiary>,
    val loans: List<Loan>,
    val calculation: PaymentCalculation,
    val notifications: List<AppNotification>,
    val messages: List<AppNotification>
)

/**
 * Represents the UI state for member registration flow.
 */
data class RegisterMemberState(
    val fullName: String = "",
    val idNumber: String = "",
    val phone: String = "",
    val email: String = "",
    val street: String = "",
    val suburb: String = "",
    val city: String = "",
    val province: String = "",
    val notificationPref: NotificationPref = NotificationPref.BOTH,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val targetGroupId: String? = null,
    val joiningFee: Money = Money.ZERO,
    val transactionId: String? = null,
    val addressSuggestions: List<LocationAddress> = emptyList(),
    val isSearchingAddress: Boolean = false
) {
    val isPersonalInfoComplete: Boolean
        get() = fullName.isNotBlank() && idNumber.isNotBlank() && phone.isNotBlank()

    val isAddressComplete: Boolean
        get() = street.isNotBlank() && suburb.isNotBlank() && city.isNotBlank() && province.isNotBlank()

    val canSubmit: Boolean
        get() = isPersonalInfoComplete && isAddressComplete && !isSubmitting && !isLoading && fieldErrors.isEmpty()

    fun withClearedSuggestions() = copy(addressSuggestions = emptyList())
}

sealed class MemberEvent : UIEvent() {
    data object NavigateToLanding : MemberEvent()
    data class NavigateToPayment(val groupId: String, val amount: Money) : MemberEvent()
    data class ShowMemberMessage(val memberMessage: String) : MemberEvent()
    data class OpenMemberFile(val memberFile: File, val memberMimeType: String, val memberChooserTitle: String) : MemberEvent()
    data class DownloadMemberFile(val memberUrl: String, val memberFileName: String, val memberMimeType: String, val memberHeaders: Map<String, String>) : MemberEvent()
}
