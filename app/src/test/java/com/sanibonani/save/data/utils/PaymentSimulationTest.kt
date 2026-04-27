package com.sanibonani.save.data.utils

import com.sanibonani.save.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class PaymentSimulationTest {

    @Test
    fun simulateThreeMonthsOfActivity() {
        val group = Group(
            id = "group-1",
            monthlyContribution = 200.0,
            lateFee = 50.0,
            lateFeeGraceDays = 5,
            paymentDueDay = 28,
            type = GroupType.BURIAL_SOCIETY,
            beneficiaryIncreasePct = 10.0
        )

        // 1. Initialize 100 members with random starting conditions
        val members = List(100) { i ->
            Member(
                id = "member-$i",
                groupId = "group-1",
                joinedAt = "2023-12-01T00:00:00Z", // All joined at start of simulation
                beneficiaryOver65Count = Random.nextInt(0, 3)
            )
        }

        val contributions = mutableListOf<Contribution>()
        var currentDate = LocalDate.of(2024, 1, 1)

        println("Starting simulation for 100 members over 3 months...")

        // Simulate 3 months (Jan, Feb, Mar)
        var totalCollected = 0.0
        var totalExpectedGroup = 0.0

        for (month in 1..3) {
            val assessmentDate = LocalDate.of(2024, month, 28)
            println("\n--- Month $month (Date: $assessmentDate) ---")

            members.forEach { member ->
                val statusBefore = PaymentCalculator.calculateStatus(group, member, contributions, assessmentDate)
                val monthlyAmount = PaymentCalculator.calculateMonthlyContribution(group, member)
                
                // Track expected revenue for the group
                totalExpectedGroup += monthlyAmount

                // Random behavior
                val behavior = Random.nextDouble()
                val paymentAmount = when {
                    behavior < 0.7 -> statusBefore.totalDueNow // 70% pay in full (including late fees)
                    behavior < 0.85 -> monthlyAmount / 2 // 15% pay partial
                    else -> 0.0 // 15% don't pay this month
                }

                if (paymentAmount > 0) {
                    contributions.add(Contribution(
                        id = "c-${contributions.size}",
                        memberId = member.id!!,
                        groupId = group.id!!,
                        amount = paymentAmount,
                        status = if (paymentAmount >= statusBefore.totalDueNow) ContributionStatus.PAID else ContributionStatus.PARTIAL,
                        paidAt = assessmentDate.toString(),
                        dueDate = assessmentDate.toString()
                    ))
                    totalCollected += paymentAmount
                }
            }

            // End of month analysis
            val currentShortfall = members.sumOf { 
                PaymentCalculator.calculateStatus(group, it, contributions, assessmentDate).shortfall 
            }
            
            val totalPaidByMembers = contributions.sumOf { it.amount }
            
            println("Total Collected So Far: R$totalCollected")
            println("Total Expected Group Revenue (Cumulative): R$totalExpectedGroup")
            println("Current Aggregate Shortfall: R$currentShortfall")
            
            // ACTUARIAL CHECK: Total Expected should ideally = Total Paid + Total Shortfall
            // (Ignoring late fees for this simple check, though they are in totalDueNow)
            val drift = totalExpectedGroup - (totalPaidByMembers + currentShortfall)
            println("SIM_LOG: Month $month - Drift: $drift")
        }

        // Final Assertions
        val finalSample = members.first()
        val finalStatus = PaymentCalculator.calculateStatus(group, finalSample, contributions, currentDate)
        println("\nFinal Sample Member Status: $finalStatus")
        
        // Ensure no negative values
        assertTrue("Shortfall should be >= 0", finalStatus.shortfall >= 0)
        assertTrue("Overpayment should be >= 0", finalStatus.overpayment >= 0)
    }

    @Test
    fun testDoubleCountingBug() {
        val group = Group(
            id = "g1",
            monthlyContribution = 200.0,
            paymentDueDay = 28,
            type = GroupType.STOKVEL
        )
        
        // Member joined Dec 1st.
        // On Jan 28th (Due Day), they should owe for Dec and Jan (Total 400).
        val member = Member(id = "m1", joinedAt = "2023-12-01T00:00:00Z")
        val contributions = emptyList<Contribution>()
        
        val dateJan28 = LocalDate.of(2024, 1, 28)
        val status = PaymentCalculator.calculateStatus(group, member, contributions, dateJan28)
        
        println("Shortfall on Due Day: R${status.shortfall}")
        println("Total Due Now on Due Day: R${status.totalDueNow}")

        // Expected shortfall: 400 (Dec + Jan)
        // Expected totalDueNow: 400 (Dec + Jan)
        
        assertEquals("Shortfall should be 400", 400.0, status.shortfall, 0.0)
        assertEquals("Total Due Now should be 400", 400.0, status.totalDueNow, 0.0)
    }
}
