package com.sanibonani.save.data.repository.equity
import com.sanibonani.save.data.datasource.*
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.LedgerEntryCreatedEvent
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import javax.inject.Inject

class GroupFinancialRepositoryImpl @Inject constructor(
    private val remoteGroupSource: RemoteGroupDataSource,
    private val localGroupSource: LocalGroupDataSource,
    private val remoteFinSource: RemoteFinancialDataSource
) : BaseRepository("GroupFinancialRepository"), GroupFinancialRepository {

    override suspend fun recordDisbursement(groupId: String, amount: Money, description: String, category: String, transactionId: String?, memberId: String?): Result<Money> = authoritativeCall("record_disbursement") {
        require(groupId.isNotBlank()) { "Group id is required" }
        require(amount.isPositive) { "Disbursement amount must be positive" }
        require(description.isNotBlank()) { "Disbursement description is required" }
        require(category.isNotBlank()) { "Disbursement category is required" }
        val newBalance = remoteGroupSource.recordDisbursement(groupId, amount, description, category, transactionId, memberId).getOrThrow()
        localGroupSource.getGroupById(groupId)?.let {
            localGroupSource.saveGroup(it.copy(balance = newBalance))
        }
        EventBus.emit(LedgerEntryCreatedEvent(LedgerEntry(groupId = groupId, amount = -amount, balanceAfter = newBalance, description = description, category = category, transactionId = transactionId)))
        newBalance
    }

    override suspend fun payPlatformFee(groupId: String, amount: Money, feeType: String, txId: String?): Result<Unit> = authoritativeCall("pay_platform_fee") {
        require(groupId.isNotBlank()) { "Group id is required" }
        require(amount.isPositive) { "Platform fee amount must be positive" }
        require(feeType.isNotBlank()) { "Platform fee type is required" }
        remoteFinSource.payPlatformFee(groupId, amount, feeType, txId, true).getOrThrow()
    }

    override suspend fun getOutstandingPlatformFees(groupId: String): Result<List<PlatformFee>> =
        remoteFinSource.getOutstandingPlatformFees(groupId)

    override suspend fun getVerifiedBalance(groupId: String): Result<Money> = remoteFinSource.getVerifiedBalance(groupId)

    override suspend fun repairGroupData(groupId: String): Result<Unit> = authoritativeCall("repair_group_data") {
        remoteFinSource.repairGroupData(groupId).getOrThrow()
    }
}
