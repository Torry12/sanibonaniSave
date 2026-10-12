package com.sanibonani.save.simulation

import com.sanibonani.save.domain.model.*
import java.time.LocalDate
import java.util.UUID

/**
 * DomainSeedFactory: A structured, domain-aligned factory designed from scratch
 * for testing and simulation purposes across the SanibonaniSave architecture.
 */
object DomainSeedFactory {

    fun createTestGroup(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Savings Group",
        type: GroupType = GroupType.STOKVEL,
        adminUserId: String,
        monthlyContribution: Double = 500.0,
        joiningFee: Double = 150.0
    ): Group {
        return Group(
            id = id,
            name = name,
            type = type,
            adminUserId = adminUserId,
            province = "Gauteng",
            city = "Johannesburg",
            township = "Soweto",
            description = "Test seed group for ${type.displayName} testing.",
            joiningFee = Money(joiningFee),
            monthlyContribution = Money(monthlyContribution),
            balance = Money.ZERO,
            registrationPaid = true,
            feeStatus = AdminFeeState.PAID,
            isPublic = true
        )
    }

    fun createTestMember(
        id: String = UUID.randomUUID().toString(),
        groupId: String,
        userId: String? = null,
        fullName: String,
        status: MemberStatus = MemberStatus.ACTIVE,
        joinedDate: LocalDate = LocalDate.now().minusMonths(12)
    ): Member {
        return Member(
            id = id,
            groupId = groupId,
            userId = userId,
            fullName = fullName,
            phone = "082${(1000000..9999999).random()}",
            email = "${fullName.lowercase().replace(" ", ".")}@test.example.com",
            status = status,
            joinedAt = joinedDate.atStartOfDay().toString() + "Z",
            bankName = "First National Bank",
            accountNo = "62${(10000000..99999999).random()}",
            branchCode = "250655",
            beneficiaryOver65Count = 1
        )
    }

    fun createTestContributions(
        memberId: String,
        groupId: String,
        monthlyAmount: Money,
        months: Int = 12
    ): List<Contribution> {
        val baseDate = LocalDate.now().minusMonths(months.toLong())
        val list = mutableListOf<Contribution>()

        // Joining fee
        list.add(
            Contribution(
                id = "c-join-$memberId",
                memberId = memberId,
                groupId = groupId,
                amount = Money(150.0),
                status = ContributionStatus.PAID,
                paidAt = baseDate.atStartOfDay().toString() + "Z",
                dueDate = baseDate.toString(),
                type = "joining_fee"
            )
        )

        // 12 months contributions
        for (h in 1..months) {
            val txDate = baseDate.plusMonths(h.toLong()).withDayOfMonth(28)
            list.add(
                Contribution(
                    id = "c-hist-$memberId-$h",
                    memberId = memberId,
                    groupId = groupId,
                    amount = monthlyAmount,
                    status = ContributionStatus.PAID,
                    paidAt = txDate.atStartOfDay().toString() + "Z",
                    dueDate = txDate.toString(),
                    type = "contribution"
                )
            )
        }
        return list
    }

    fun createTestLoan(
        id: String = UUID.randomUUID().toString(),
        groupId: String,
        memberId: String,
        amount: Double = 2000.0,
        interestRate: Double = 15.0,
        status: LoanStatus = LoanStatus.ACTIVE
    ): Loan {
        val principal = Money(amount)
        val interest = principal * (interestRate / 100.0)
        val totalToRepay = principal + interest
        val startDate = LocalDate.now().minusMonths(3)
        val endDate = startDate.plusMonths(6)
        return Loan(
            id = id,
            groupId = groupId,
            memberId = memberId,
            amount = principal,
            interestRate = interestRate,
            totalToRepay = totalToRepay,
            totalRepaid = Money.ZERO,
            monthlyRepayment = totalToRepay / 6,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            status = status,
            purpose = "Emergency Medical & Repairs"
        )
    }

    fun createTestPayoutRequest(
        id: String = UUID.randomUUID().toString(),
        groupId: String,
        memberId: String,
        amount: Double = 1500.0,
        payoutType: PayoutType = PayoutType.STANDARD,
        status: PayoutStatus = PayoutStatus.PENDING
    ): PayoutRequest {
        return PayoutRequest(
            id = id,
            groupId = groupId,
            memberId = memberId,
            amount = Money(amount),
            bankName = "First National Bank",
            accountNo = "62889900112",
            branchCode = "250655",
            status = status,
            type = payoutType,
            supportingDocumentStatus = DocumentStatus.VERIFIED
        )
    }
}
