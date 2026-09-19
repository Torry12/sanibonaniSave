package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import javax.inject.Inject

class GenerateStandardConstitutionUseCase @Inject constructor() {
    operator fun invoke(group: Group): String {
        return """
            CONSTITUTION OF ${group.name.uppercase()}

            1. NAME
               The name of the group shall be ${group.name}.

            2. OBJECTIVES
               To provide financial support for members in accordance with ${group.type.displayName} standards.

            3. CONTRIBUTIONS
               Each member shall contribute ${group.monthlyContribution.format()} per month.

            4. LATE FEES
               A late fee of ${group.lateFee.format()} will be applied after ${group.lateFeeGraceDays} days.
        """.trimIndent()
    }
}
