package com.sanibonani.save.domain.usecase.equity
import com.sanibonani.save.domain.model.ContributionOutcome
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PaymentDestination
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.model.Transaction
import com.sanibonani.save.domain.model.TransactionStatus
import com.sanibonani.save.domain.model.TransactionType
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.service.FraudDetectionService
import com.sanibonani.save.domain.service.PaymentAllocationService
import com.sanibonani.save.domain.utils.PaymentCalculation
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.utils.toUserMessage
import java.util.UUID

/**
 * Orchestrates all fund inflows (contributions, fees, joining fees).
 * Handles atomic bookkeeping, fraud checks, and business side-effects.
 */
class ProcessContributionUseCase @Inject constructor(
    private val contributionRepository: ContributionRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val loanRepository: LoanRepository,
    private val platformRepository: PlatformRepository,
    private val fraudDetectionService: FraudDetectionService,
    private val allocationService: PaymentAllocationService,
    private val activateGroupUseCase: ActivateGroupUseCase
) {
    private val strategies: Map<PaymentType, ContributionStrategy> by lazy {
        mapOf(
            PaymentType.PLATFORM_FEE to PlatformFeeStrategy(contributionRepository, groupRepository, platformRepository, activateGroupUseCase),
            PaymentType.REGISTRATION to PlatformFeeStrategy(contributionRepository, groupRepository, platformRepository, activateGroupUseCase),
            PaymentType.JOINING_FEE to JoiningFeeStrategy(contributionRepository, memberRepository),
            PaymentType.CONTRIBUTION to StandardContributionStrategy(contributionRepository, loanRepository, allocationService)
        )
    }

    /**
     * Confirms and records a successful contribution.
     * This is typically called by a Payment Gateway callback.
     */
    suspend fun confirm(
        txId: String,
        method: PaymentMethod,
        type: PaymentType,
        amount: Money,
        groupId: String,
        member: Member? = null,
        group: Group? = null,
        calculation: PaymentCalculation? = null
    ): Result<Unit> = safeRunCatching {
        require(amount.isPositive) { "Amount must be positive" }

        val destination = resolveDestination(type)
        val strategy = strategies[type] ?: throw IllegalArgumentException("Unsupported payment type: ${type.name}")

        strategy.process(
            txId = txId,
            amount = amount,
            groupId = groupId,
            member = member,
            group = group,
            calculation = calculation,
            method = method,
            destination = destination
        ).getOrThrow()
    }

    /**
     * Primary entry point for initiating or processing a payment.
     */
    suspend operator fun invoke(
        type: PaymentType,
        amount: Money,
        groupId: String,
        member: Member? = null,
        group: Group? = null,
        calculation: PaymentCalculation? = null,
        method: PaymentMethod = PaymentMethod.BANK,
        transactionId: String = "tx_${UUID.randomUUID()}"
    ): ContributionOutcome {
        return try {
            require(transactionId.isNotBlank()) { "Transaction ID must not be blank" }

            // 1. Compliance/Fraud Check
            evaluateFraudRisk(transactionId, groupId, member, amount)?.let { return it }

            // 2. Process
            confirm(transactionId, method, type, amount, groupId, member, group, calculation).fold(
                onSuccess = { ContributionOutcome.Success(transactionId) },
                onFailure = { ContributionOutcome.Failure(it.toUserMessage(), it) }
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // Never report a cancelled contribution as a business failure
        } catch (e: Exception) {
            ContributionOutcome.Failure(e.toUserMessage(), e)
        }
    }

    private fun resolveDestination(type: PaymentType): PaymentDestination {
        return when (type) {
            PaymentType.REGISTRATION, PaymentType.PLATFORM_FEE, PaymentType.JOINING_FEE -> PaymentDestination.PLATFORM
            else -> PaymentDestination.GROUP
        }
    }

    private suspend fun evaluateFraudRisk(txId: String, groupId: String, member: Member?, amount: Money): ContributionOutcome? {
        val result = fraudDetectionService.evaluateTransaction(
            Transaction(
                id = txId,
                memberId = member?.id ?: "system",
                groupId = groupId,
                amount = amount.toCents(),
                type = TransactionType.CONTRIBUTION,
                status = TransactionStatus.COMPLETED,
                createdAt = System.currentTimeMillis()
            )
        )
        return if (result.isFailure) {
            ContributionOutcome.ComplianceBlocked(result.exceptionOrNull()?.message ?: "Fraud check failed")
        } else null
    }
}
