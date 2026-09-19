package com.sanibonani.save.data.repository.mappers

import com.sanibonani.save.data.local.LoanEntity
import com.sanibonani.save.data.local.LoanRepaymentEntity
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanRepayment

fun Loan.toEntity() = LoanEntity(
    id = id ?: "",
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    interestRate = interestRate,
    totalToRepay = totalToRepay,
    totalRepaid = totalRepaid,
    monthlyRepayment = monthlyRepayment,
    startDate = startDate,
    endDate = endDate,
    nextPaymentDate = nextPaymentDate,
    status = status,
    purpose = purpose,
    contractUrl = contractUrl,
    suretyAmount = suretyAmount,
    createdAt = createdAt,
)

fun LoanEntity.toModel() = Loan(
    id = id,
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    interestRate = interestRate,
    totalToRepay = totalToRepay,
    totalRepaid = totalRepaid,
    monthlyRepayment = monthlyRepayment,
    startDate = startDate,
    endDate = endDate,
    nextPaymentDate = nextPaymentDate,
    status = status,
    purpose = purpose,
    contractUrl = contractUrl,
    suretyAmount = suretyAmount,
    createdAt = createdAt,
)

fun LoanRepayment.toEntity() = LoanRepaymentEntity(
    id = id ?: "",
    loanId = loanId,
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    paidAt = paidAt,
    paymentMethod = paymentMethod,
    transactionId = transactionId,
    createdAt = createdAt,
)

fun LoanRepaymentEntity.toModel() = LoanRepayment(
    id = id,
    loanId = loanId,
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    paidAt = paidAt,
    paymentMethod = paymentMethod,
    transactionId = transactionId,
    createdAt = createdAt,
)
