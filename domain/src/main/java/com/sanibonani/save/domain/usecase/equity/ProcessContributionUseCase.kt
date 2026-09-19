package com.sanibonani.save.domain.usecase.equity

import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.PaymentGatewayRepository
import com.sanibonani.save.domain.usecase.treasury.ActivateGroupUseCase
import com.sanibonani.save.domain.utils.OperationKeys
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Orchestrates all fund inflows (contributions, fees, joining fees).
 * Handles atomic bookkeeping and side effects like group activation.
 */
class ProcessContributionUseCase @Inject constructor(
    private val contributionRepository: ContributionRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val loanRepository: LoanRepository,
    private val platformRepository: PlatformRepository,
    private val providerRepository: FinancialProviderRepository,
    private val fraudDetectionService: com.sanibonani.save.domain.service.FraudDetectionService,
    private val activateGroupUseCase: ActivateGroupUseCase
) {
    companion object {
        const val NEW_GROUP_SENTINEL = "new_group"
    }

    /**
     * Confirms and records a successful contribution in the system.
     */
    suspend fun confirm(
        txId: String,
        method: PaymentMethod,
        type: PaymentType,
        amount: Money,
        groupId: String,
        member: Member? = null,
        group: Group? = null,
        calculation: PaymentCalculation? = null,
        destination: PaymentDestination = PaymentDestination.GROUP
    ): Result<Unit> = runCatching {
        require(amount.isPositive) { "Contribution amount must be greater than zero." }

        when (type) {
            PaymentType.PLATFORM_FEE -> processPlatformFee(txId, amount, groupId, group, member, method, destination)
            PaymentType.JOINING_FEE -> processJoiningFee(txId, amount, groupId, member, group, method, destination)
            PaymentType.CONTRIBUTION -> processStandardContribution(txId, amount, groupId, member, group, calculation, method, destination)
            else -> throw IllegalArgumentException("Type '${type.name}' is not supported in contribution flow.")
        }
    }

    private suspend fun processPlatformFee(
        txId: String,
        amount: Money,
        groupId: String,
        group: Group?,
        member: Member?,
        method: PaymentMethod,
        destination: PaymentDestination
    ) {
        if (groupId == NEW_GROUP_SENTINEL) return

        val allocations = buildJsonArray {
            addJsonObject {
                put("type", "member_fee")
                put("amount", amount.amount)
                put("due_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                put("description", "Platform Service Fee")
            }
        }

        contributionRepository.processComplexPayment(
            groupId = groupId,
            memberId = member?.id ?: "",
            totalAmount = amount,
            txId = txId,
            allocations = allocations,
            method = method,
            paymentType = PaymentType.PLATFORM_FEE,
            destination = destination
        ).getOrThrow()

        if (group?.registrationPaid == false) {
            activateGroupUseCase(groupId, txId).getOrThrow()
        } else {
            groupRepository.updateFeeStatus(groupId, AdminFeeState.PAID).getOrThrow()
            platformRepository.unsuspendGroup(groupId).getOrThrow()
        }
    }

    private suspend fun processJoiningFee(
        txId: String,
        amount: Money,
        groupId: String,
        member: Member?,
        group: Group?,
        method: PaymentMethod,
        destination: PaymentDestination
    ) {
        val targetMember = member ?: throw Exception("Member context required")
        val allocations = buildJsonArray {
            addJsonObject {
                put("type", "joining_fee")
                put("amount", amount.amount)
                put("due_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                put("description", "Member Joining Fee")
            }
        }

        contributionRepository.processComplexPayment(
            groupId = groupId,
            memberId = targetMember.id ?: "",
            totalAmount = amount,
            txId = txId,
            allocations = allocations,
            method = method,
            paymentType = PaymentType.JOINING_FEE,
            destination = destination
        ).getOrThrow()

        val targetStatus = if ((group?.probationMonths ?: 0) > 0) MemberStatus.PROBATION else MemberStatus.ACTIVE
        memberRepository.updateMemberStatus(targetMember.id ?: "", targetStatus).getOrThrow()
    }

    private suspend fun processStandardContribution(
        txId: String,
        amount: Money,
        groupId: String,
        member: Member?,
        group: Group?,
        calculation: PaymentCalculation?,
        method: PaymentMethod,
        destination: PaymentDestination
    ) {
        val targetMember = member ?: throw Exception("Member context required")
        val calc = calculation ?: throw Exception("Calculation context required")
        val memberId = targetMember.id ?: throw IllegalStateException("Member ID missing")

        val activeLoans = loanRepository.getMemberLoans(memberId).first().getOrNull()?.filter {
            it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID
        } ?: emptyList()

        var remainingAmount = amount
        val breakdown = calc.breakdown
        val effectiveDueDate = if (calc.nextDueDate == "N/A" || calc.nextDueDate.isBlank()) {
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        } else calc.nextDueDate

        val allocations = buildJsonArray {
            // 0. Recovery Fee
            if (remainingAmount.isPositive && breakdown.recoveryFee.isPositive) {
                val payAmount = remainingAmount.min(breakdown.recoveryFee)
                addJsonObject {
                    put("type", "member_fee")
                    put("amount", payAmount.amount.toPlainString())
                    put("due_date", effectiveDueDate)
                    put("description", "Shortfall Recovery Fee")
                }
                remainingAmount -= payAmount
            }

            // 1. Platform Fees
            if (remainingAmount.isPositive && breakdown.platformFees.isPositive) {
                val payAmount = remainingAmount.min(breakdown.platformFees)
                addJsonObject {
                    put("type", "member_fee")
                    put("amount", payAmount.amount.toPlainString())
                    put("due_date", effectiveDueDate)
                    put("description", "Monthly Platform Fee")
                }
                remainingAmount -= payAmount
            }

            // 2. Late Fees
            if (remainingAmount.isPositive && breakdown.lateFees.isPositive) {
                val payAmount = remainingAmount.min(breakdown.lateFees)
                addJsonObject {
                    put("type", "late_fee")
                    put("amount", payAmount.amount.toPlainString())
                    put("due_date", effectiveDueDate)
                    put("description", "Late Fee Payment")
                }
                remainingAmount -= payAmount
            }

            // 3. Loans
            if (remainingAmount.isPositive && activeLoans.isNotEmpty()) {
                activeLoans.sortedBy { it.createdAt }.forEach { loan ->
                    if (remainingAmount.isZero) return@forEach
                    val outstanding = loan.totalToRepay - loan.totalRepaid
                    if (outstanding.isPositive) {
                        val payAmount = remainingAmount.min(outstanding)
                        addJsonObject {
                            put("type", "loan_repayment")
                            put("amount", payAmount.amount.toPlainString())
                            put("loan_id", loan.id)
                            put("due_date", effectiveDueDate)
                            put("description", "Loan Repayment: ${loan.id?.take(5)}")
                        }
                        remainingAmount -= payAmount
                    }
                }
            }

            // 4. Contribution
            if (remainingAmount.isPositive) {
                addJsonObject {
                    put("type", "contribution")
                    put("amount", remainingAmount.amount.toPlainString())
                    put("due_date", effectiveDueDate)
                    put("description", "Monthly Contribution")
                }
            }
        }

        contributionRepository.processComplexPayment(
            groupId = groupId,
            memberId = memberId,
            totalAmount = amount,
            txId = txId,
            allocations = allocations,
            method = method,
            paymentType = PaymentType.CONTRIBUTION,
            destination = destination
        ).getOrThrow()
    }

    suspend operator fun invoke(
        type: PaymentType,
        amount: Money,
        groupId: String,
        member: Member? = null,
        group: Group? = null,
        calculation: PaymentCalculation? = null,
        method: PaymentMethod = PaymentMethod.BANK
    ): Result<String> = runCatching {
        val txId = "tx_${OperationKeys.stableSuffix("contribution", type.name, groupId, member?.id, amount.toDouble())}"

        fraudDetectionService.evaluateTransaction(
            Transaction(
                id = txId,
                memberId = member?.id ?: "",
                groupId = groupId,
                amount = amount,
                type = TransactionType.CONTRIBUTION,
                status = TransactionStatus.COMPLETED,
                createdAt = java.time.Instant.now().toString()
            )
        ).getOrThrow()

        val destination = if (type in listOf(PaymentType.REGISTRATION, PaymentType.PLATFORM_FEE, PaymentType.JOINING_FEE))
            PaymentDestination.PLATFORM else PaymentDestination.GROUP

        confirm(txId, method, type, amount, groupId, member, group, calculation, destination).getOrThrow()
        txId
    }
}
