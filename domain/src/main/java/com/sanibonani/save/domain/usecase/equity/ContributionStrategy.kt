package com.sanibonani.save.domain.usecase.equity
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.utils.PaymentCalculation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.sanibonani.save.domain.usecase.financialResult
import com.sanibonani.save.domain.usecase.firstValueOrNull

/**
 * Strategy interface for handling different types of fund inflows.
 */
interface ContributionStrategy {
    suspend fun process(
        txId: String,
        amount: Money,
        groupId: String,
        member: Member?,
        group: Group?,
        calculation: PaymentCalculation?,
        method: PaymentMethod,
        destination: PaymentDestination
    ): Result<Unit>
}

/**
 * Handles Platform Service Fees and Group Registration Fees.
 */
class PlatformFeeStrategy(
    private val contributionRepository: ContributionRepository,
    private val groupRepository: GroupRepository,
    private val platformRepository: PlatformRepository,
    private val activateGroupUseCase: ActivateGroupUseCase
) : ContributionStrategy {
    override suspend fun process(
        txId: String,
        amount: Money,
        groupId: String,
        member: Member?,
        group: Group?,
        calculation: PaymentCalculation?,
        method: PaymentMethod,
        destination: PaymentDestination
    ): Result<Unit> = financialResult {
        if (groupId == "new_group") return@financialResult

        val actualType = if (group?.registrationPaid == false) PaymentType.REGISTRATION else PaymentType.PLATFORM_FEE
        val description = if (actualType == PaymentType.REGISTRATION) "Group Registration Fee" else "Platform Service Fee"

        val allocations = listOf(
            PaymentAllocation(
                type = "member_fee",
                amount = amount,
                dueDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                description = description
            )
        )

        contributionRepository.processComplexPayment(
            groupId = groupId,
            memberId = member?.id ?: "",
            totalAmount = amount,
            txId = txId,
            allocations = allocations,
            method = method,
            paymentType = actualType,
            destination = destination
        ).getOrThrow()

        // Handle Side Effects
        if (actualType == PaymentType.REGISTRATION) {
            activateGroupUseCase(groupId, txId).getOrThrow()
        } else {
            groupRepository.updateFeeStatus(groupId, AdminFeeState.PAID).getOrThrow()
            platformRepository.unsuspendGroup(groupId).getOrThrow()
        }
    }
}

/**
 * Handles member joining fees.
 */
class JoiningFeeStrategy(
    private val contributionRepository: ContributionRepository,
    private val memberRepository: MemberRepository
) : ContributionStrategy {
    override suspend fun process(
        txId: String,
        amount: Money,
        groupId: String,
        member: Member?,
        group: Group?,
        calculation: PaymentCalculation?,
        method: PaymentMethod,
        destination: PaymentDestination
    ): Result<Unit> = financialResult {
        val targetMember = member ?: throw Exception("Member context required for joining fee")
        val allocations = listOf(
            PaymentAllocation(
                type = "joining_fee",
                amount = amount,
                dueDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                description = "Member Joining Fee"
            )
        )
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

        // Transition member status from PENDING_PAYMENT
        val currentGroup = group ?: throw Exception("Group context required for status transition")
        val targetStatus = if (currentGroup.probationMonths > 0) MemberStatus.PROBATION else MemberStatus.ACTIVE
        memberRepository.updateMemberStatus(targetMember.id ?: "", targetStatus).getOrThrow()
    }
}

/**
 * Handles recurring member contributions and loan repayments.
 */
class StandardContributionStrategy(
    private val contributionRepository: ContributionRepository,
    private val loanRepository: LoanRepository,
    private val allocationService: com.sanibonani.save.domain.service.PaymentAllocationService
) : ContributionStrategy {
    override suspend fun process(
        txId: String,
        amount: Money,
        groupId: String,
        member: Member?,
        group: Group?,
        calculation: PaymentCalculation?,
        method: PaymentMethod,
        destination: PaymentDestination
    ): Result<Unit> = financialResult {
        val memberId = member?.id ?: throw Exception("Member context required for contribution")
        val calc = calculation ?: throw Exception("Calculation context required for contribution")

        // Fetch active loans to check for potential repayments
        val activeLoans = loanRepository.getMemberLoans(memberId).firstValueOrNull()?.filter {
            it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID
        } ?: emptyList()

        val allocations = allocationService.calculateAllocations(amount, calc, activeLoans)

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
}
