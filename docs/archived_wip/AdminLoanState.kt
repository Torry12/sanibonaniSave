package com.sanibonani.save.viewmodel.state.admin

import com.sanibonani.save.domain.model.Loan

data class AdminLoanState(
    val groupLoans: List<Loan> = emptyList(),
    val isProcessingLoan: Boolean = false
)
