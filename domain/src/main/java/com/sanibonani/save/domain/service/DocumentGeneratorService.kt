package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentGeneratorService @Inject constructor() {

    fun generateMembershipContractContent(member: Member, group: Group): String {
        return """
            MEMBERSHIP AGREEMENT

            This agreement is made between ${group.name} ("the Group")
            and ${member.fullName} ("the Member"), ID Number: ${member.idNumber ?: "N/A"}.

            1. CONTRIBUTION COMMITMENT
            The Member agrees to pay a monthly contribution of ${group.monthlyContribution.format()}
            to the Group's pool by the ${group.paymentDueDay}th of each month.

            2. GROUP TYPE SPECIAL CONDITIONS
            ${getSpecialConditions(group.type)}

            3. TERMINATION
            A member may exit after providing 30 days notice. Any outstanding loans must be
            settled before withdrawal of savings.

            Signed: __________________________
            Date: ${java.time.LocalDate.now()}
        """.trimIndent()
    }

    private fun getSpecialConditions(type: GroupType): String {
        return when (type) {
            GroupType.BURIAL_SOCIETY -> "This group provides funeral insurance coverage. A waiting period of 6 months applies for natural death claims."
            GroupType.STOKVEL -> "This is a savings group. Payouts are made annually or at the end of the savings cycle as defined in the constitution."
            GroupType.ROSCA -> "This is a rotating group. Members must complete the full cycle of 3, 6, or 12 months as agreed during registration."
            GroupType.INVESTMENT_CLUB -> "Contributions are used for capital investments. Capital is at risk and returns are based on Net Asset Value (NAV)."
            GroupType.EMERGENCY_FUND -> "Funds are held for unexpected emergencies. Withdrawals are capped at 50% of available liquidity per request."
            else -> "The member agrees to follow all group-specific rules as defined in the official constitution."
        }
    }
}
