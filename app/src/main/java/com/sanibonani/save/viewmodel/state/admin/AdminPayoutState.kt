package com.sanibonani.save.viewmodel.state.admin

import com.sanibonani.save.domain.model.PayoutRequest

data class AdminPayoutState(
    val payouts: List<PayoutRequest> = emptyList(),
    val isRequestingPayout: Boolean = false,
    val payoutRequestSuccess: Boolean = false,
    val payoutAmount: String = "",
    val payoutBankName: String = "",
    val payoutAccountNo: String = "",
    val payoutBranchCode: String = ""
)
