package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import java.io.File

data class AdminMemberUiState(
    val group: Group? = null,
    val members: List<Member> = emptyList(),
    val selectedMember: Member? = null,
    val selectedMemberBeneficiaries: List<Beneficiary> = emptyList(),
    val selectedMemberDocuments: List<MemberDocument> = emptyList(),
    val selectedMemberCalculation: PaymentCalculation? = null,
    val memberCalculations: Map<String, PaymentCalculation> = emptyMap(),
    val isEligibleForLoan: Boolean = false,
    val loanIneligibilityReason: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isExporting: Boolean = false,
    val exportFile: File? = null,
    val messageText: String = "",
    val isSendingMessage: Boolean = false,
    val messageSentSuccess: Boolean = false,
    val selectedNotifChannel: NotifChannel = NotifChannel.BOTH
)
