package com.sanibonani.save

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ComplexFinancialScenariosTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var supabaseRepo: SupabaseRepository
    @Inject lateinit var groupRepo: GroupRepository
    @Inject lateinit var memberRepo: MemberRepository
    @Inject lateinit var loanRepo: LoanRepository
    @Inject lateinit var platformRepo: PlatformRepository

    companion object {
        private const val TAG = "ComplexFinSim"
    }

    @Test
    fun testLoanWorkflowAndFinancialImpact() = runBlocking {
        hiltRule.inject()

        Log.i(TAG, "🚀 STARTING COMPLEX SCENARIO: Loan Workflow & Financial Impact")

        // 1. Setup Identities
        val adminEmail = "torrymsimango@gmail.com"
        val adminPass = "torry123M"
        supabaseRepo.signIn(adminEmail, adminPass).getOrThrow()
        val adminId = supabaseRepo.currentUserId!!
        Log.i(TAG, "✅ Step 1: Admin signed in ($adminId)")

        // 2. Locate a Seed Group
        val groups = groupRepo.getGroupsByAdmin(adminId).getOrThrow()
        val group = groups.first { it.registrationPaid && it.isPublic }
        val groupId = group.id!!
        Log.i(TAG, "✅ Step 2: Targeting Group: ${group.name} (Balance: ${group.balance})")

        // 3. Register a New Member for the Loan
        val memberId = UUID.randomUUID().toString()
        val member = Member(
            id = memberId,
            groupId = groupId,
            fullName = "Complex Test Member",
            status = MemberStatus.ACTIVE
        )
        memberRepo.registerMember(member, "test_joining_tx").getOrThrow()
        Log.i(TAG, "✅ Step 3: Member registered: $memberId")

        // 4. Request a Loan
        val loanAmount = 2000.0
        val loan = Loan(
            memberId = memberId,
            groupId = groupId,
            amount = Money(loanAmount),
            interestRate = 10.0,
            totalToRepay = Money(2200.0),
            monthlyRepayment = Money(220.0),
            status = LoanStatus.PENDING,
            purpose = "High-fidelity simulation testing"
        )
        val loanId = loanRepo.requestLoan(loan).getOrThrow()
        Log.i(TAG, "✅ Step 4: Loan requested: $loanId (Amount: R$loanAmount)")

        // 5. Admin Approves and Disburses (Decreases Group Balance)
        val initialBalance = groupRepo.getGroup(groupId).getOrThrow().balance
        loanRepo.disburseLoan(loanId, PaymentMethod.BANK).getOrThrow()

        val afterDisburseBalance = groupRepo.getGroup(groupId).getOrThrow().balance
        Log.i(TAG, "✅ Step 5: Loan disbursed. Balance: $initialBalance -> $afterDisburseBalance")

        assertEquals(
            "Group balance should decrease by loan amount",
            initialBalance.toDouble() - loanAmount,
            afterDisburseBalance.toDouble(),
            0.01
        )

        // 6. Member Records a Partial Repayment (Increases Group Balance)
        val repaymentAmount = 500.0
        val repayment = LoanRepayment(
            loanId = loanId,
            memberId = memberId,
            groupId = groupId,
            amount = Money(repaymentAmount),
            paymentMethod = PaymentMethod.CASH
        )
        loanRepo.recordRepayment(repayment).getOrThrow()

        val finalBalance = groupRepo.getGroup(groupId).getOrThrow().balance
        Log.i(TAG, "✅ Step 6: Repayment recorded. Final Balance: $finalBalance")

        assertEquals(
            "Group balance should increase by repayment amount",
            afterDisburseBalance.toDouble() + repaymentAmount,
            finalBalance.toDouble(),
            0.01
        )

        // 7. Verify via Platform Analytics (Financial Reporting)
        val analytics = platformRepo.getPlatformAnalytics().getOrThrow()
        Log.i(TAG, "✅ Step 7: Platform Reporting verified. Total Platform Balance: ${analytics.totalBalance}")

        assertTrue("Analytics should reflect data in system", analytics.totalGroups > 0)
        assertTrue("Analytics should show positive platform balance", analytics.totalBalance.isPositive)

        Log.i(TAG, "🏁 SUCCESS: High-fidelity complex scenario completed.")
    }
}
