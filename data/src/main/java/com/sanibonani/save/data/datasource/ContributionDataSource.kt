package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

interface RemoteContributionDataSource {
    suspend fun recordContribution(contribution: Contribution): Result<Unit>
    suspend fun getMemberContributions(memberId: String, groupId: String): Result<List<Contribution>>
    suspend fun getGroupContributions(groupId: String): Result<List<Contribution>>
    suspend fun recordPayment(payment: Payment): Result<String>
    suspend fun getPayments(groupId: String): Result<List<Payment>>
    suspend fun processComplexPayment(
        groupId: String,
        memberId: String,
        totalAmount: Money,
        txId: String,
        allocations: List<PaymentAllocation>,
        method: PaymentMethod,
        paymentType: PaymentType,
        destination: PaymentDestination
    ): Result<Money>
}

interface LocalContributionDataSource {
    fun observeMemberContributions(memberId: String): Flow<List<Contribution>>
    fun observeGroupContributions(groupId: String): Flow<List<Contribution>>
    suspend fun saveContributions(contributions: List<Contribution>)
    suspend fun syncMemberContributions(memberId: String, contributions: List<Contribution>)
    suspend fun syncGroupContributions(groupId: String, contributions: List<Contribution>)
    fun observeGroupPayments(groupId: String): Flow<List<Payment>>
    suspend fun savePayments(payments: List<Payment>)
    suspend fun clearAll()
}
