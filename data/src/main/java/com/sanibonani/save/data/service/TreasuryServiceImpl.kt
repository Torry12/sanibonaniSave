package com.sanibonani.save.data.service

import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.service.TreasuryService
import javax.inject.Inject

class TreasuryServiceImpl @Inject constructor(
    private val groupRepository: GroupRepository
) : TreasuryService {

    override suspend fun getTreasuryBalance(groupId: String): Result<Money> = runCatching {
        groupRepository.getGroupById(groupId).getOrThrow().balance
    }

    override suspend fun schedulePayout(groupId: String, amount: Money): Result<Boolean> = runCatching {
        val balance = getTreasuryBalance(groupId).getOrThrow()
        if (amount > balance) {
            throw Exception("Insufficient treasury liquidity for payout of ${amount.format()}")
        }
        // Logic to record the payout would happen here or in a use case
        true
    }

    override suspend fun calculateRequiredReserve(groupId: String): Result<Money> = runCatching {
        val group = groupRepository.getGroupById(groupId).getOrThrow()

        // Basic SA Stokvel/Burial standards:
        // Burial: 3 months of average claims
        // Stokvel: 10% of total contributions
        when (group.type) {
            GroupType.BURIAL_SOCIETY -> group.monthlyContribution * 5.0
            GroupType.STOKVEL -> group.balance * 0.10
            GroupType.EMERGENCY_FUND -> group.balance * 0.20
            else -> Money.ZERO
        }
    }
}
