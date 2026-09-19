package com.sanibonani.save.domain.usecase.disbursement

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
