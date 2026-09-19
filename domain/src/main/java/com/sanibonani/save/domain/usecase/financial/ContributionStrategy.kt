package com.sanibonani.save.domain.usecase.financial

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.financial.*
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.utils.PaymentCalculation
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    ): Result<Unit> = runCatching {
        if (groupId == "new_group") return@runCatching

        val allocations = buildJsonArray {
            addJsonObject {
                put("type", "member_fee")
                put("amount", amount.amount.toPlainString())
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
}

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
    ): Result<Unit> = runCatching {
        val targetMember = member ?: throw Exception("Member context required")
        val allocations = buildJsonArray {
            addJsonObject {
                put("type", "joining_fee")
                put("amount", amount.amount.toPlainString())
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
}

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
    ): Result<Unit> = runCatching {
        val targetMember = member ?: throw Exception("Member context required")
        val calc = calculation ?: throw Exception("Calculation context required")
        val memberId = targetMember.id ?: throw IllegalStateException("Member ID missing")

        val activeLoans = loanRepository.getMemberLoans(memberId).first().getOrNull()?.filter {
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
