package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.datasource.LocalContributionDataSource
import com.sanibonani.save.data.local.ContributionDao
import com.sanibonani.save.data.local.PaymentDao
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Payment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomContributionDataSourceImpl @Inject constructor(
    private val contributionDao: ContributionDao,
    private val paymentDao: PaymentDao
) : LocalContributionDataSource {

    override fun observeMemberContributions(memberId: String): Flow<List<Contribution>> =
        contributionDao.observeContributions(memberId).map { list -> list.map { it.toModel() } }

    override fun observeGroupContributions(groupId: String): Flow<List<Contribution>> =
        contributionDao.observeGroupContributions(groupId).map { list -> list.map { it.toModel() } }

    override suspend fun saveContributions(contributions: List<Contribution>) {
        contributionDao.upsertContributions(contributions.map { it.toEntity() })
    }

    override suspend fun syncMemberContributions(memberId: String, contributions: List<Contribution>) {
        contributionDao.syncMemberContributions(memberId, contributions.map { it.toEntity() })
    }

    override suspend fun syncGroupContributions(groupId: String, contributions: List<Contribution>) {
        contributionDao.syncGroupContributions(groupId, contributions.map { it.toEntity() })
    }

    override fun observeGroupPayments(groupId: String): Flow<List<Payment>> =
        paymentDao.observeGroupPayments(groupId).map { list -> list.map { it.toModel() } }

    override suspend fun savePayments(payments: List<Payment>) {
        paymentDao.upsertPayments(payments.map { it.toEntity() })
    }

    override suspend fun clearAll() {
        contributionDao.clearAll()
        paymentDao.clearAll()
    }
}
