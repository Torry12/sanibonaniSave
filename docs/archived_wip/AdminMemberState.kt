package com.sanibonani.save.viewmodel.state.admin

import com.sanibonani.save.data.utils.PaymentCalculation
import com.sanibonani.save.domain.model.*

data class AdminMemberState(
    val members: List<Member> = emptyList(),
    val selectedMember: Member? = null,
    val selectedMemberBeneficiaries: List<Beneficiary> = emptyList(),
    val selectedMemberDocuments: List<MemberDocument> = emptyList(),
    val selectedMemberCalculation: PaymentCalculation? = null,
    val isEligibleForLoan: Boolean = false,
    val loanIneligibilityReason: String? = null,
    val memberCalculations: Map<String, PaymentCalculation> = emptyMap(),
    val editingBeneficiary: Beneficiary? = null,
    val isSavingBeneficiary: Boolean = false,
    val burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val isProcessingClaim: Boolean = false
)
