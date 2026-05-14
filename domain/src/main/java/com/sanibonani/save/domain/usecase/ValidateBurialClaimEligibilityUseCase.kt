package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
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
        dateOfDeath: String
    ): EligibilityResult {
        // 1. Group type check
        if (group.type != GroupType.BURIAL_SOCIETY) {
            return EligibilityResult.Ineligible("Claims are only allowed for Burial Society groups.")
        }

        // 2. Member status check
        if (member.status == MemberStatus.SUSPENDED) {
            return EligibilityResult.Ineligible("Membership is suspended. Claims are not permitted.")
        }
        if (member.status == MemberStatus.PENDING_PAYMENT) {
            return EligibilityResult.Ineligible("Membership has outstanding payments. Claims are not permitted.")
        }

        // 3. Waiting period check
        val joinedDate = try {
            LocalDate.parse(member.joinedAt?.substringBefore("T"))
        } catch (_: Exception) {
            return EligibilityResult.Ineligible("Invalid membership start date.")
        }
        
        val deathDate = try {
            LocalDate.parse(dateOfDeath)
        } catch (_: Exception) {
            return EligibilityResult.Ineligible("Invalid date of death.")
        }

        val monthsSinceJoining = ChronoUnit.MONTHS.between(joinedDate, deathDate)
        val isAccidental = ACCIDENTAL_PATTERNS.any { causeOfDeath.contains(it, ignoreCase = true) }

        if (!isAccidental) {
            val requiredMonths = group.probationMonths
            if (monthsSinceJoining < requiredMonths) {
                return EligibilityResult.Ineligible(
                    "Waiting period not met for natural death. Member has $monthsSinceJoining months membership, but $requiredMonths months are required."
                )
            }
        }

        // 4. Suicide check (Special industry standard: 12 months)
        val isSuicide = SUICIDE_PATTERNS.any { causeOfDeath.contains(it, ignoreCase = true) }
        if (isSuicide && monthsSinceJoining < 12) {
            return EligibilityResult.Ineligible("Claims for suicide have a 12-month waiting period.")
        }

        return EligibilityResult.Eligible
    }

    companion object {
        /** Patterns that qualify a death as accidental for waiting-period bypass. */
        private val ACCIDENTAL_PATTERNS = listOf(
            "accidental", "accident", "motor vehicle", "mvt", "drowning",
            "electrocution", "fire", "fall", "struck by"
        )
        /** Patterns that trigger the 12-month suicide exclusion. */
        private val SUICIDE_PATTERNS = listOf("suicide", "self-inflicted", "self inflicted")
    }
}
