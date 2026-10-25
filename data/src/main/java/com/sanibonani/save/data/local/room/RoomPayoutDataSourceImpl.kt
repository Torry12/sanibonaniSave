package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.datasource.LocalPayoutDataSource
import com.sanibonani.save.data.local.PayoutDao
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.PayoutRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPayoutDataSourceImpl @Inject constructor(
    private val payoutDao: PayoutDao
) : LocalPayoutDataSource {

    override fun observePayouts(groupId: String): Flow<List<PayoutRequest>> =
        payoutDao.observePayouts(groupId).map { list -> list.map { it.toModel() } }

    override fun observePayoutsForMember(memberId: String): Flow<List<PayoutRequest>> =
        payoutDao.observePayoutsForMember(memberId).map { list -> list.map { it.toModel() } }

    override suspend fun savePayouts(payouts: List<PayoutRequest>) {
        payoutDao.upsertPayouts(payouts.map { it.toEntity() })
    }

    override suspend fun savePayout(payout: PayoutRequest) {
        payoutDao.upsertPayout(payout.toEntity())
    }

    override suspend fun syncPayouts(groupId: String, payouts: List<PayoutRequest>) {
        payoutDao.syncPayouts(groupId, payouts.map { it.toEntity() })
    }

    override suspend fun clearAll() {
        payoutDao.clearAll()
    }
}
