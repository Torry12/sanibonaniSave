package com.sanibonani.save.data.repository.equity
import com.sanibonani.save.data.datasource.LocalContributionDataSource
import com.sanibonani.save.data.datasource.RemoteContributionDataSource
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PaymentProcessedEvent
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import javax.inject.Inject
import javax.inject.Provider

class ContributionRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteContributionDataSource,
    private val localSource: LocalContributionDataSource,
    private val memberRepo: Provider<MemberRepository>,
    private val groupDao: com.sanibonani.save.data.local.GroupDao,
    private val json: Json,
    private val pendingOpDao: com.sanibonani.save.data.local.PendingOperationDao
) : BaseRepository("ContributionRepository"), ContributionRepository {

    override suspend fun recordContribution(contribution: Contribution): Result<Unit> =
        safeApiCall(
            operationType = "contribution",
            groupId = contribution.groupId,
            payload = json.encodeToString(contribution),
            dao = pendingOpDao
        ) {
            remoteSource.recordContribution(contribution).getOrThrow()
            localSource.saveContributions(listOf(contribution))
        }

    override fun observeMemberContributions(memberId: String, groupId: String): Flow<Result<List<Contribution>>> = observeAndSync(
        dbFlow = localSource.observeMemberContributions(memberId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getMemberContributions(memberId, groupId).getOrThrow() },
        cacheSync = { localSource.syncMemberContributions(memberId, it) }
    )

    override fun observeGroupContributions(groupId: String): Flow<Result<List<Contribution>>> = observeAndSync(
        dbFlow = localSource.observeGroupContributions(groupId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getGroupContributions(groupId).getOrThrow() },
        cacheSync = { localSource.syncGroupContributions(groupId, it) }
    )

    override suspend fun recordPayment(payment: Payment): Result<String> = runCatching {
        val paymentId = remoteSource.recordPayment(payment).getOrThrow()
        localSource.savePayments(listOf(payment.copy(id = paymentId)))
        EventBus.emit(PaymentProcessedEvent(payment.copy(id = paymentId)))
        paymentId
    }

    override fun observePayments(groupId: String): Flow<Result<List<Payment>>> = observeAndSync(
        dbFlow = localSource.observeGroupPayments(groupId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getPayments(groupId).getOrThrow() },
        cacheSync = { localSource.savePayments(it) }
    )

    override suspend fun processComplexPayment(
        groupId: String,
        memberId: String,
        totalAmount: Money,
        txId: String,
        allocations: List<PaymentAllocation>,
        method: PaymentMethod,
        paymentType: PaymentType,
        destination: PaymentDestination
    ): Result<Money> = runCatching {
        val newBalance = remoteSource.processComplexPayment(groupId, memberId, totalAmount, txId, allocations, method, paymentType, destination).getOrThrow()

        // 1. Sync local group balance
        groupDao.getGroupById(groupId)?.let {
            groupDao.upsertGroup(it.copy(balance = newBalance))
        }

        // 2. Cache local contribution records for instant local-first UI reactivity
        val nowStr = java.time.Instant.now().toString()
        val localContribs = allocations.mapIndexed { idx, alloc ->
            Contribution(
                id = "${txId}_$idx",
                memberId = memberId,
                groupId = groupId,
                amount = alloc.amount,
                type = alloc.type,
                dueDate = alloc.dueDate,
                paidAt = nowStr,
                status = ContributionStatus.PAID,
                paymentMethod = method.name.lowercase(),
                transactionId = txId
            )
        }
        localSource.saveContributions(localContribs)

        newBalance
    }

    override suspend fun getTotalContributed(memberId: String, groupId: String): Money =
        observeMemberContributions(memberId, groupId).map { it.getOrDefault(emptyList()) }.first()
            .fold(Money.ZERO) { acc, next -> acc + next.amount }
}
