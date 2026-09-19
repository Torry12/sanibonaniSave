package com.sanibonani.save.data.repository.mappers

import com.sanibonani.save.data.local.*
import com.sanibonani.save.domain.model.*

fun Contribution.toEntity() = ContributionEntity(
    id = id ?: "",
    memberId = memberId,
    groupId = groupId,
    policyId = policyId,
    amount = amount,
    createdAt = createdAt,
    dueDate = dueDate,
    paidAt = paidAt,
    status = status,
    type = type,
    paymentMethod = paymentMethod,
    lateFeesApplied = lateFeesApplied,
    lateFeeAmount = lateFeeAmount,
    principalAmount = principalAmount,
    transactionId = transactionId,
)

fun ContributionEntity.toModel() = Contribution(
    id = id,
    memberId = memberId,
    groupId = groupId,
    policyId = policyId,
    amount = amount,
    createdAt = createdAt,
    dueDate = dueDate,
    paidAt = paidAt,
    status = status,
    type = type,
    paymentMethod = paymentMethod,
    transactionId = transactionId,
    lateFeesApplied = lateFeesApplied,
    lateFeeAmount = lateFeeAmount,
    principalAmount = principalAmount,
)

fun Payment.toEntity() = PaymentEntity(
    id = id ?: "",
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    paymentType = paymentType,
    paymentMethod = paymentMethod,
    transactionId = transactionId,
    status = status,
    processedAt = processedAt,
    createdAt = createdAt,
)

fun PaymentEntity.toModel() = Payment(
    id = id,
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    paymentType = paymentType,
    paymentMethod = paymentMethod,
    transactionId = transactionId,
    status = status,
    processedAt = processedAt,
    createdAt = createdAt,
)

fun PayoutRequest.toEntity() = PayoutEntity(
    id = id ?: "",
    groupId = groupId,
    memberId = memberId,
    amount = amount,
    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,
    status = status,
    type = type,
    processedBy = processedBy,
    processedAt = processedAt,
    payoutReference = payoutReference,
    createdAt = createdAt,
)

fun PayoutEntity.toModel() = PayoutRequest(
    id = id,
    groupId = groupId,
    memberId = memberId,
    amount = amount,
    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,
    status = status,
    type = type,
    processedBy = processedBy,
    processedAt = processedAt,
    payoutReference = payoutReference,
    createdAt = createdAt,
)

fun LedgerEntryEntity.toModel() = LedgerEntry(
    id = id,
    groupId = groupId,
    memberId = memberId,
    transactionId = transactionId,
    amount = amount,
    balanceAfter = balanceAfter,
    description = description,
    category = category,
    isPlatform = isPlatform,
    createdAt = createdAt
)

fun LedgerEntry.toLedgerEntity() = LedgerEntryEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    memberId = memberId,
    transactionId = transactionId,
    amount = amount,
    balanceAfter = balanceAfter,
    description = description,
    category = category,
    isPlatform = isPlatform,
    createdAt = createdAt
)

fun BankTransactionEntity.toModel() = BankTransaction(
    id = id,
    groupId = groupId,
    bankReference = bankReference,
    amount = amount,
    transactionDate = transactionDate,
    description = description,
    status = status,
    reconciledWith = reconciledWith,
    createdAt = createdAt
)

fun BankTransaction.toEntity() = BankTransactionEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    bankReference = bankReference,
    amount = amount,
    transactionDate = transactionDate,
    description = description,
    status = status,
    reconciledWith = reconciledWith,
    createdAt = createdAt
)

fun SettlementRecordEntity.toModel() = SettlementRecord(
    id = id,
    transactionId = transactionId,
    providerReference = providerReference,
    status = status,
    settledAt = settledAt,
    feeDeducted = feeDeducted
)

fun SettlementRecord.toEntity() = SettlementRecordEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    transactionId = transactionId,
    providerReference = providerReference,
    status = status,
    settledAt = settledAt,
    feeDeducted = feeDeducted
)

fun ReconciliationRecordEntity.toModel() = ReconciliationRecord(
    id = id,
    groupId = groupId,
    periodStart = periodStart,
    periodEnd = periodEnd,
    status = status,
    ledgerBalance = ledgerBalance,
    actualBalance = actualBalance,
    discrepancy = discrepancy,
    notes = notes,
    performedBy = performedBy,
    createdAt = createdAt
)

fun ReconciliationRecord.toEntity() = ReconciliationRecordEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    periodStart = periodStart,
    periodEnd = periodEnd,
    status = status,
    ledgerBalance = ledgerBalance,
    actualBalance = actualBalance,
    discrepancy = discrepancy,
    notes = notes,
    performedBy = performedBy,
    createdAt = createdAt
)
