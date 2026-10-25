package com.sanibonani.save.data.service

import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.service.GroupFinancialPolicy
import com.sanibonani.save.domain.service.TreasuryService
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

class TreasuryServiceImpl @Inject constructor(
    private val groupRepository: GroupRepository,
    private val policy: GroupFinancialPolicy
) : TreasuryService {

    override suspend fun getTreasuryBalance(groupId: String): Result<Money> = safeRunCatching {
        groupRepository.getGroupById(groupId).getOrThrow().balance
    }

    override suspend fun schedulePayout(groupId: String, amount: Money): Result<Boolean> = safeRunCatching {
        val balance = getTreasuryBalance(groupId).getOrThrow()
        if (amount > balance) {
            throw Exception("Insufficient treasury liquidity for payout of ${amount.format()}")
        }
        // Logic to record the payout would happen here or in a use case
        true
    }

    override suspend fun calculateRequiredReserve(groupId: String): Result<Money> = safeRunCatching {
        val group = groupRepository.getGroupById(groupId).getOrThrow()

        policy.requiredReserve(group)
    }
}
