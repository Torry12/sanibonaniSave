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
    /**
     * The member's own ledger read has answered at least once for the member on screen. Room answers
     * from the cache, so this says a read ran - not that the server agreed with it.
     */
    val memberLedgerAnswered: Boolean = false,
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

    // Document upload state
    val isUploading: Boolean = false,
    val profileImageVersion: Long = 0L,

    // Export state
    val isExporting: Boolean = false,

    // Messaging state
    val messageText: String = "",
    val isSendingMessage: Boolean = false,
    val messageSentSuccess: Boolean = false,

    // Security
    val biometricEnabled: Boolean = false,

    // Verification
    val cacheLastSyncByGroup: Map<String, Long> = emptyMap(),
    val isSystemOnline: Boolean = true,
    val isAirplaneMode: Boolean = false,
    /**
     * Sections of the selected group whose last pull did not land. Non-empty means the portal is
     * showing the cache, not a book the server confirmed.
     */
    val unverifiedSections: Set<String> = emptySet(),

    // Support / Impersonation
    val isImpersonating: Boolean = false,
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
        memberLedgerAnswered = false,
        paymentCalculation = null,
        beneficiaries = emptyList(),
        documents = emptyList(),
        loans = emptyList(),
        burialClaims = emptyList(),
        payouts = emptyList(),
        notifications = emptyList(),
        messages = emptyList(),
        isLoading = true,
        error = null
    )

    /**
     * Support Mode was entered but its hydration refused before any of the targeted member's book
     * arrived. No record is coming, so there is nothing left to keep hidden and the refusal is the
     * only thing the admin can act on.
     */
    val supportEntryWasRefused: Boolean
        get() = isImpersonating && !isExiting && member == null && error != null

    /**
     * Whether the portal must withhold what it shows because the targeted member's record has not
     * been verified yet.
     *
     * A refusal releases it: the screen's loading branch outranks the error branch, so a lock held
     * over a refused entry rendered "Entering Support Mode…" forever with no top bar to exit from.
     */
    val isPrivacyLocked: Boolean
        get() = isImpersonating && !isExiting && !supportEntryWasRefused &&
            (member == null || (member.id != targetMemberId && member.userId != targetMemberId))

    /**
     * Paid to the platform rather than into the group: the types `internal_post_contribution` books
     * to `platform_ledger`. A row the server still owes is not money paid, so only paid rows count.
     */
    val platformFeesPaid: Money
        get() = contributions.filter { it.status == ContributionStatus.PAID && it.isPlatformFee }
            .fold(Money.ZERO) { acc, c -> acc + c.amount }

    /**
     * Money the group actually holds for this member.
     *
     * `members.total_paid` is derived by `fn_sync_single_member_financials`, which counts a paid
     * `registration_contribution` although that fee was booked to the platform - the split that let a
     * member with nothing but a joining fee be shown R500 of savings beside an empty ledger. The fee
     * rows are subtracted from whichever source answers, and an empty contributions list subtracts
     * nothing: a read that never ran must not turn into an asserted R0.
     */
    val totalSavings: Money
        get() {
            val cachedTotalPaid = member?.totalPaid ?: Money.ZERO
            val paidContributionSum = contributions
                .filter { it.status == ContributionStatus.PAID }
                .fold(Money.ZERO) { acc, c -> acc + c.amount }
            val ledgerInflow = memberLedger
                .filter { it.amount.isPositive }
                .fold(Money.ZERO) { acc, e -> acc + e.amount }
            val paidIn = when {
                cachedTotalPaid.isPositive -> cachedTotalPaid
                paidContributionSum.isPositive -> paidContributionSum
                else -> ledgerInflow
            }
            return (paidIn - platformFeesPaid).coerceAtLeast(Money.ZERO)
        }

    /**
     * The member's own transaction history: the [memberLedger] rows plus the platform fees they paid.
     *
     * The ledger read cannot show a registration or member fee, whatever the sync does: that money goes
     * to `platform_ledger`, which has no `member_id` column, and the Room query additionally filters
     * `is_platform` rows out. So the member measured on Cloud on 2026-11-04 - one paid
     * registration_contribution of R500 and no group entries - was shown a card saying they had paid
     * R500 beside a list saying they had never transacted. The fee rows come from the contributions
     * the member did pay, are marked platform rows, and carry no balance because the group never held
     * the money. A fee already in the book is not repeated: seeds did once mis-file registration rows
     * straight into `group_ledger`.
     */
    val memberTransactions: List<LedgerEntry>
        get() {
            val bookedContributionIds = memberLedger.mapNotNull { it.transactionId }.toSet()
            val fees = contributions
                .filter { it.status == ContributionStatus.PAID && it.isPlatformFee }
                .filter { c -> c.id?.let { it !in bookedContributionIds } ?: true }
                .map { c ->
                    LedgerEntry(
                        id = c.id?.let { "platform-fee:$it" },
                        groupId = c.groupId,
                        memberId = c.memberId,
                        transactionId = c.id,
                        amount = c.amount,
                        category = c.type,
                        isPlatform = true,
                        createdAt = c.paidAt ?: c.createdAt
                    )
                }
            // [memberLedger] arrives newest first from both tiers, so an all-ledger list keeps its
            // order without a sort that could tie two rows written in the same second.
            return (memberLedger + fees).sortedByDescending { it.createdAt ?: "" }
        }

    /**
     * Whether the activity list may call an empty [memberTransactions] "no transactions".
     *
     * The list is the only reader of the member's ledger rows, while the header sums contributions -
     * two different books, though [totalSavings] now leaves out the fees that keep the ledger empty -
     * so an empty list is only a fact once the read has answered for the member on screen and the
     * TREASURY pillar that fills this table has landed. Until then the screen knows nothing and has
     * to say so.
     */
    val memberActivityIsUnconfirmed: Boolean
        get() = memberTransactions.isEmpty() &&
            (!memberLedgerAnswered || unverifiedSections.contains(DomainPillar.TREASURY.name.lowercase()))
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
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val targetGroupId: String? = null,
    val joiningFee: Money = Money.ZERO,
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
