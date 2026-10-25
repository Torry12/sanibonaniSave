package com.sanibonani.save.domain.usecase.member
import com.sanibonani.save.domain.model.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
class ValidateBurialClaimEligibilityUseCase @Inject constructor() {
    sealed class EligibilityResult {
        object Eligible : EligibilityResult()
        data class Ineligible(val reason: String) : EligibilityResult()
    }
    operator fun invoke(
        member: Member,
        group: Group,
        causeOfDeath: String,
        dateOfDeath: String,
        overdueAmount: Money = Money.ZERO
    ): EligibilityResult {
        if (group.type != GroupType.BURIAL_SOCIETY) return EligibilityResult.Ineligible("Burial Societies only.")
        if (member.status == MemberStatus.SUSPENDED) return EligibilityResult.Ineligible("Suspended.")
        if (member.status == MemberStatus.PENDING_PAYMENT) return EligibilityResult.Ineligible("Pending payment.")
        if (overdueAmount.isPositive) return EligibilityResult.Ineligible("Arrears: ${overdueAmount.format()}")

        // 1. Min Members Requirement
        if (group.minMembersForClaims > 0 && group.currentMembers < group.minMembersForClaims) {
            return EligibilityResult.Ineligible("Group must have at least ${group.minMembersForClaims} active members to process claims (Current: ${group.currentMembers}).")
        }

        // 2. Min Reserve Percentage Requirement
        val claimAmount = group.burialClaimAmount
        if (group.minReservePercentage > 0.0 && group.balance.isPositive) {
            val requiredReserve = claimAmount * (group.minReservePercentage / 100.0)
            if (group.balance < requiredReserve) {
                return EligibilityResult.Ineligible("Group reserves (${group.balance.format()}) are below required ${group.minReservePercentage}% minimum threshold (${requiredReserve.format()}).")
            }
        }
        val joinedDate = try { LocalDate.parse(member.joinedAt?.substringBefore("T")) } catch (_: Exception) { return EligibilityResult.Ineligible("Invalid start date.") }
        val deathDate = try { LocalDate.parse(dateOfDeath) } catch (_: Exception) { return EligibilityResult.Ineligible("Invalid death date.") }
        val monthsTenure = ChronoUnit.MONTHS.between(joinedDate, deathDate)
        val isSuicide = SUICIDE_PATTERNS.any { causeOfDeath.contains(it, ignoreCase = true) }
        if (isSuicide && monthsTenure < 12) return EligibilityResult.Ineligible("Suicide 12-month wait.")
        val isAccidental = ACCIDENTAL_PATTERNS.any { causeOfDeath.contains(it, ignoreCase = true) }
        if (!isAccidental && monthsTenure < 6) return EligibilityResult.Ineligible("Natural death 6-month wait.")
        return EligibilityResult.Eligible
    }
    companion object {
        private val ACCIDENTAL_PATTERNS = listOf("accidental", "accident", "drowning", "fire")
        private val SUICIDE_PATTERNS = listOf("suicide", "self-inflicted")
    }
}
