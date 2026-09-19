package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupFinancialInsight
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import javax.inject.Inject

class GetGroupBusinessInsightsUseCase @Inject constructor(
    private val actuarialRepository: ActuarialRepository
) {
    suspend operator fun invoke(group: Group, members: List<Member>): Result<GroupFinancialInsight> {
        return actuarialRepository.computeGroupInsight(group, members)
    }
}
