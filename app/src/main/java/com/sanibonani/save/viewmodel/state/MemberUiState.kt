package com.sanibonani.save.viewmodel.state

import com.sanibonani.save.data.remote.Feature
import com.sanibonani.save.data.utils.PaymentCalculation
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import java.io.File

/**
 * Represents the UI state for the Member Portal.
 *
 * This state class follows the single-source-of-truth principle,
 * containing all data needed to render the member screens.
 */
data class MemberUiState(
    // Core member data
    val member: Member? = null,
    val group: Group? = null,
    val userRole: UserRole = UserRole.MEMBER,
    val currentGroupId: String? = null,
    val businessInsight: GetGroupBusinessInsightsUseCase.GroupBusinessInsight = GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty,

    // Financial data
    val contributions: List<Contribution> = emptyList(),
    val calculation: PaymentCalculation? = null,

    // Beneficiaries
    val beneficiaries: List<Beneficiary> = emptyList(),

    // Documents
    val documents: List<MemberDocument> = emptyList(),

    // Loans
    val loans: List<Loan> = emptyList(),
    val loanRepayments: List<LoanRepayment> = emptyList(),
    val isEligibleForLoan: Boolean = false,
    val loanIneligibilityReason: String? = null,

    // Burial Society Claims
    val burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val isSubmittingClaim: Boolean = false,
    val claimSubmitSuccess: Boolean = false,

    // Multi-group support
    val memberships: List<Member> = emptyList(),
    val cacheLastSyncByGroup: Map<String, Long> = emptyMap(),

    // Communication
    val notifications: List<AppNotification> = emptyList(),
    val messages: List<AppNotification> = emptyList(),

    // UI state
    val selectedTab: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,

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
    val biometricEnabled: Boolean = false
) {
    /**
     * Returns true if the member portal has loaded required data.
     */
    val isDataReady: Boolean
        get() = member != null && group != null && !isLoading

    /**
     * Returns the total notification count.
     */
    val notificationCount: Int
        get() = notifications.size

    /**
     * Returns the total message count.
     */
    val messageCount: Int
        get() = messages.size

    /**
     * Returns true if the member is in good standing (ACTIVE status).
     */
    val isInGoodStanding: Boolean
        get() = member?.status == MemberStatus.ACTIVE
}

/**
 * Represents the UI state for member registration flow.
 */
data class RegisterMemberState(
    // Personal info
    val fullName: String = "",
    val idNumber: String = "",
    val phone: String = "",
    val email: String = "",

    // Address info
    val street: String = "",
    val suburb: String = "",
    val city: String = "",
    val province: String = "",

    // Preferences
    val notificationPref: NotificationPref = NotificationPref.BOTH,

    // Form state
    val isSubmitting: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,

    // Registration context
    val targetGroupId: String? = null,
    val joiningFee: Double = 0.0,
    val transactionId: String? = null,

    // Address autocomplete
    val addressSuggestions: List<Feature> = emptyList(),
    val isSearchingAddress: Boolean = false
) {
    /**
     * Returns true if all required personal fields are filled.
     */
    val isPersonalInfoComplete: Boolean
        get() = fullName.isNotBlank() &&
                idNumber.isNotBlank() &&
                phone.isNotBlank()

    /**
     * Returns true if all required address fields are filled.
     */
    val isAddressComplete: Boolean
        get() = street.isNotBlank() &&
                suburb.isNotBlank() &&
                city.isNotBlank() &&
                province.isNotBlank()

    /**
     * Returns true if the form is ready for submission.
     */
    val canSubmit: Boolean
        get() = isPersonalInfoComplete && isAddressComplete && !isSubmitting

    /**
     * Clears address suggestions when user is done selecting.
     */
    fun withClearedSuggestions() = copy(addressSuggestions = emptyList())
}

/**
 * One-time events that should be consumed by the UI.
 *
 * Unlike state, events are transient and should only be processed once.
 */
sealed class MemberEvent {
    /** Navigation to landing page after successful registration */
    data object NavigateToLanding : MemberEvent()

    /** Navigation to payment screen */
    data class NavigateToPayment(val groupId: String, val amount: Double) : MemberEvent()

    /** Show a toast/snackbar message */
    data class ShowMessage(val message: String) : MemberEvent()

    /** Ask the UI to open/share a generated file (CSV/PDF) via FileProvider. */
    data class OpenFile(
        val file: File,
        val mimeType: String,
        val chooserTitle: String
    ) : MemberEvent()

    /** Ask the UI to download a remote file via URL and headers. */
    data class DownloadFile(
        val url: String,
        val fileName: String,
        val mimeType: String,
        val headers: Map<String, String>
    ) : MemberEvent()
}

