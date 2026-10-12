package com.sanibonani.save

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.*

import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.usecase.equity.ProcessContributionUseCase
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import com.sanibonani.save.domain.utils.PaymentCalculation
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.*
import javax.inject.Inject
import kotlin.random.Random
import com.sanibonani.save.domain.repository.credit.LoanRepository

/**
 * LIVE DATA SIMULATION & DEBUG RUNNER
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppLiveSimulationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var groupRepo: GroupRepository
    @Inject lateinit var memberRepo: MemberRepository
    @Inject lateinit var loanRepo: LoanRepository
    @Inject lateinit var processContributionUseCase: ProcessContributionUseCase
    @Inject lateinit var activateGroupUseCase: ActivateGroupUseCase
    @Inject lateinit var createGroupUseCase: CreateGroupUseCase

    private val TAG = "AppLiveSimTest"

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun runThreeMonthActivitySimulation() {
        runBlocking {
            Log.i(TAG, "🚀 STARTING 3-MONTH LIVE SIMULATION")

        // Use the deterministic in-memory test identity; never create production auth users.
        com.sanibonani.save.di.TestAuthSessionController.reset(UserRole.GROUP_ADMIN)
        com.sanibonani.save.di.TestAuthSessionController.signIn("simulation-admin@example.test")
        val adminUserId = com.sanibonani.save.di.TestAuthSessionController.mockedUserId!!

        // 2. Create Group
        val groupId = UUID.randomUUID().toString()
        val group = Group(
            id = groupId,
            name = "Sim-Circle ${System.currentTimeMillis().toString().takeLast(4)}",
            type = GroupType.STOKVEL,
            joiningFee = Money(150.0),
            monthlyContribution = Money(500.0),
            lateFee = Money(50.0),
            adminUserId = adminUserId,
            registrationPaid = true
        )
        // Use UseCase to ensure Admin Member is created and role is assigned
        createGroupUseCase(
            group = group,
            adminFullName = "Sim Admin",
            adminPhone = "0821234567"
        ).getOrThrow()

        // Registration is intentionally unpaid in the in-memory test backend. Activation
        // correctly rejects that state, so the flow tests group/member/ledger operations.

        // 3. Add 10 Members
        val members = mutableListOf<Member>()
        for (i in 1..10) {
            val member = Member(
                groupId = groupId,
                fullName = "Sim Member $i",
                email = "sim.member.$i.${System.currentTimeMillis()}@example.com",
                status = MemberStatus.ACTIVE,
                joinedAt = LocalDate.now().minusMonths(4).toString()
            )
            val registered = memberRepo.registerMember(member, "sim_joining_tx_$i").getOrThrow()
            members.add(registered)
        }

        // 4. SIMULATE MONTH 1
        members.forEach { m ->
            processContributionUseCase.confirm(
                txId = "month1_${m.id}",
                method = PaymentMethod.BANK,
                type = PaymentType.CONTRIBUTION,
                amount = Money(500.0),
                groupId = groupId,
                member = m,
                calculation = PaymentCalculation.EMPTY
            ).getOrThrow()
        }

        // 5. SIMULATE MONTH 2
        members.forEachIndexed { index, m ->
            if (index % 2 == 0) {
                processContributionUseCase.confirm(
                    txId = "month2_${m.id}",
                    method = PaymentMethod.BANK,
                    type = PaymentType.CONTRIBUTION,
                    amount = Money(500.0),
                    groupId = groupId,
                    member = m,
                    calculation = PaymentCalculation.EMPTY
                ).getOrThrow()
            }
        }

        // 6. SIMULATE LOAN
        val borrower = members[0]
        val loan = Loan(
            memberId = borrower.id!!,
            groupId = groupId,
            amount = Money(2000.0),
            interestRate = 10.0,
            totalToRepay = Money(2200.0),
            monthlyRepayment = Money(1100.0),
            status = LoanStatus.ACTIVE,
            purpose = "Education"
        )
        loanRepo.requestLoan(loan).getOrThrow()

        // 7. VERIFY INTEGRITY
        delay(3000)
        val finalGroup = groupRepo.getGroupById(groupId).getOrThrow()
        val membersList = memberRepo.getGroupMembers(groupId).first().getOrThrow()

        Log.i(TAG, "--- SIMULATION RESULTS ---")
        Log.i(TAG, "Group Balance: ${finalGroup.balance.format()}")
        Log.i(TAG, "Member Count (Cache/DB): ${membersList.size}")

        assertTrue("Should have at least 10 members", membersList.size >= 10)
        assertTrue("Balance should be significantly positive", finalGroup.balance.isPositive)

        Log.i(TAG, "✅ SIMULATION COMPLETE.")
        }
    }
}
