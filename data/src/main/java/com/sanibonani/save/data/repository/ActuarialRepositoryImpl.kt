package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.data.utils.roundToTwoDecimals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.*

class ActuarialRepositoryImpl @Inject constructor(
    private val groupRepo: GroupRepository,
    private val memberRepo: Provider<MemberRepository>
) : BaseRepository("ActuarialRepository"), ActuarialRepository {

    override suspend fun computeMetrics(groupId: String): Result<ActuarialMetrics> = withContext(Dispatchers.IO) {
        try {
            val group = groupRepo.getGroupById(groupId).getOrElse {
                return@withContext Result.failure(it)
            }

            val members = memberRepo.get().getGroupMembers(groupId).first().getOrElse {
                return@withContext Result.failure(it)
            }

            val metrics = withContext(Dispatchers.Default) {
                calculateMetrics(group, members)
            }
            Result.success(metrics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun calculateDynamicJoiningFee(groupId: String): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            val group = groupRepo.getGroupById(groupId).getOrThrow()
            val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
            
            withContext(Dispatchers.Default) {
                val memberCount = max(1, members.count { it.status != MemberStatus.SUSPENDED })
                val baseFee = group.joiningFee
                
                // Logic: Equity Buy-in
                // New members should contribute to the "Safety Net" existing members built.
                val reservesPerMember = group.balance / memberCount
                
                // Community standard: Charge 40% of the accumulated reserve share as a "buy-in"
                val buyInFactor = 0.40 
                val equityContribution = reservesPerMember * buyInFactor
                
                // Final Fee = Base + Equity Share
                val dynamicFee = baseFee + equityContribution
                
                // Guardrail: Cap the fee at 5x the base fee to keep it accessible for the community
                // and avoid discouraging growth.
                min(dynamicFee, baseFee * 5.0).roundToTwoDecimals()
            }
        }
    }

    override suspend fun calculateViabilityPlan(
        groupId: String,
        goalAmount: Double,
        periodMonths: Int
    ): Result<ViabilityPlan> = withContext(Dispatchers.IO) {
        kotlinx.coroutines.withTimeoutOrNull(10000) {
            runCatching {
                val group = groupRepo.getGroupById(groupId).getOrThrow()
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
                
                withContext(Dispatchers.Default) {
                    val activeMembers = members.count { 
                        it.status == MemberStatus.ACTIVE || it.status == MemberStatus.PROBATION 
                    }
                    val numMembers = max(1, activeMembers)

                    val messages = mutableListOf<String>()
                    
                    // Base calculation: Goal / (Members * Period)
                    // If goal is zero, we use current group settings to validate current path
                    val effectiveGoal = if (goalAmount > 0) goalAmount else group.goalAmount
                    val effectivePeriod = if (periodMonths > 0) periodMonths else max(1, group.periodMonths)

                    val monthlyPerMember = effectiveGoal / (numMembers * effectivePeriod)
                    
                    // Adjustments based on Group Type
                    val suggestedMonthly = when (group.type) {
                        GroupType.BURIAL_SOCIETY -> {
                            // Burial societies need a safety buffer for immediate claims
                            val buffer = monthlyPerMember * 1.25 
                            messages.add("Burial Society: Added 25% safety buffer for claim readiness.")
                            buffer
                        }
                        GroupType.INVESTMENT_CLUB -> {
                            // Investment clubs should account for a target return (e.g. 8% annual)
                            val rate = 0.08 / 12
                            val denominator = (Math.pow(1.0 + rate, effectivePeriod.toDouble()) - 1.0) / rate
                            val adjusted = effectiveGoal / (numMembers * denominator)
                            messages.add("Investment Club: Factored in 8% projected annual return.")
                            adjusted
                        }
                        else -> monthlyPerMember
                    }

                    val initialContribution = when (group.type) {
                        GroupType.BURIAL_SOCIETY -> suggestedMonthly * 2 // 2 months upfront
                        else -> suggestedMonthly
                    }

                    val projectedValue = if (group.type == GroupType.INVESTMENT_CLUB) {
                        val rate = 0.08 / 12
                        numMembers * suggestedMonthly * ((Math.pow(1.0 + rate, effectivePeriod.toDouble()) - 1.0) / rate)
                    } else {
                        numMembers * suggestedMonthly * effectivePeriod
                    }

                    if (numMembers < 5) {
                        messages.add("Warning: Low member count ($numMembers) increases individual burden. Consider recruiting more members to reduce monthly costs.")
                    }

                    ViabilityPlan(
                        initialContribution = initialContribution.roundToTwoDecimals(),
                        suggestedMonthlyContribution = suggestedMonthly.roundToTwoDecimals(),
                        projectedValue = projectedValue.roundToTwoDecimals(),
                        isViable = projectedValue >= effectiveGoal,
                        goalAmount = effectiveGoal,
                        periodMonths = effectivePeriod,
                        messages = messages
                    )
                }
            }
        } ?: Result.failure(Exception("Calculation timed out. Please try again."))
    }

    override fun calculateMetrics(group: Group, members: List<Member>): ActuarialMetrics {
        val activeMembersList = members.filter { it.status == MemberStatus.ACTIVE || it.status == MemberStatus.PROBATION }
        val memberCount = max(1, activeMembersList.size)

        // Pre-calculate to avoid redundant checks in the loop if possible
        val totalExpectedContributionsAnnual = activeMembersList.sumOf { calculateMemberContribution(group, it) } * 12

        val paymentRatePct = if (totalExpectedContributionsAnnual > 0) {
            min(100.0, (group.balance / totalExpectedContributionsAnnual) * 100.0)
        } else {
            100.0
        }

        val estimatedAnnualClaims = when (group.type) {
            GroupType.BURIAL_SOCIETY -> memberCount * 15000.0
            GroupType.STOKVEL -> memberCount * 5000.0
            else -> memberCount * 10000.0
        }

        val mortalityRatePct = 0.82

        return computeMetrics(
            membersCount = memberCount,
            balance = group.balance,
            mortalityRatePct = mortalityRatePct,
            avgClaim = estimatedAnnualClaims / memberCount,
            safetyLoadingPct = 35.0,
            adminCostPerMember = 25.0,
            annualDiscountRatePct = 8.5,
            currentPremium = if (memberCount > 0) totalExpectedContributionsAnnual / 12.0 / memberCount else group.monthlyContribution,
            claimsPaid = estimatedAnnualClaims * 0.1,
            totalContributions = group.balance,
            paymentRatePct = paymentRatePct,
            totalExpectedContributionsAnnual = totalExpectedContributionsAnnual
        )
    }

    /**
     * Core actuarial math function. 
     * Renamed internal parameter to 'membersCount' to avoid shadowing and for clarity.
     */
    fun computeMetrics(
        membersCount: Int,
        balance: Double,
        mortalityRatePct: Double,
        avgClaim: Double,
        safetyLoadingPct: Double,
        adminCostPerMember: Double,
        annualDiscountRatePct: Double,
        currentPremium: Double,
        claimsPaid: Double,
        totalContributions: Double,
        paymentRatePct: Double,
        totalExpectedContributionsAnnual: Double
    ): ActuarialMetrics {
        val q = mortalityRatePct / 100.0
        val expectedAnnualClaims = membersCount * q * avgClaim
        
        val i = annualDiscountRatePct / 100.0
        val v = 1.0 / (1.0 + i)
        val actuarialPresentValue = expectedAnnualClaims * v
        
        val purePremium = expectedAnnualClaims / membersCount
        val safetyLoading = purePremium * (safetyLoadingPct / 100.0)
        val grossPremium = purePremium + safetyLoading + adminCostPerMember
        
        val reserveAdequacyPct = if (expectedAnnualClaims > 0) (balance / expectedAnnualClaims) * 100.0 else 1000.0
        val solvencyMarginPct = ((balance + totalContributions - claimsPaid) / max(1.0, expectedAnnualClaims)) * 100.0
        val lossRatioPct = if (totalContributions > 0) (claimsPaid / totalContributions) * 100.0 else 0.0
        
        val contributionSufficiencyPct = if (grossPremium > 0) (currentPremium / grossPremium) * 100.0 else 0.0
        val breakEvenMembers = ceil((adminCostPerMember * membersCount) / max(0.01, currentPremium - purePremium - safetyLoading)).toInt()
        
        val fundingRatioPct = if (actuarialPresentValue > 0) (balance / actuarialPresentValue) * 100.0 else 1000.0
        
        val score = (
            min(100.0, reserveAdequacyPct * 0.3) +
            min(100.0, contributionSufficiencyPct * 0.4) +
            min(100.0, paymentRatePct * 0.3)
        ).roundToInt().coerceIn(0, 100)
        
        val monthlyNetFlow = (totalExpectedContributionsAnnual / 12.0 * (paymentRatePct / 100.0)) - (expectedAnnualClaims / 12.0) - (membersCount * adminCostPerMember / 12.0)
        val insolvencyMonths = if (monthlyNetFlow >= 0.0) Int.MAX_VALUE
                              else (balance / abs(monthlyNetFlow)).toInt().coerceAtLeast(0)

        return ActuarialMetrics(
            purePremium = purePremium.roundToTwoDecimals(),
            grossPremium = grossPremium.roundToTwoDecimals(),
            reserveAdequacyPct = reserveAdequacyPct.roundToTwoDecimals(),
            solvencyMarginPct = solvencyMarginPct.roundToTwoDecimals(),
            lossRatioPct = lossRatioPct.roundToTwoDecimals(),
            contributionSufficiencyPct = contributionSufficiencyPct.roundToTwoDecimals(),
            breakEvenMembers = breakEvenMembers,
            actuarialPresentValue = actuarialPresentValue.roundToTwoDecimals(),
            fundingRatioPct = fundingRatioPct.roundToTwoDecimals(),
            paymentRatePct = paymentRatePct.roundToTwoDecimals(),
            compositeRiskScore = score,
            insolvencyMonths = insolvencyMonths,
            expectedAnnualClaims = expectedAnnualClaims.roundToTwoDecimals()
        )
    }
    
    override fun calculateMemberContribution(group: Group, member: Member): Double {
        return PaymentCalculator.calculateMonthlyContribution(group, member)
    }
}
