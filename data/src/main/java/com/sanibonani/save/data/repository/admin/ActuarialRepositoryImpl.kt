package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.actuarial.GroupTypeActuarialEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

@Suppress("UNCHECKED_CAST")
class ActuarialRepositoryImpl @Inject constructor(
    private val groupRepo: GroupRepository,
    private val financialRepo: GroupFinancialRepository,
    private val memberRepo: Provider<MemberRepository>,
    private val paymentCalculator: PaymentCalculator,
    private val actuarialEngine: GroupTypeActuarialEngine,
) : BaseRepository("ActuarialRepository"), ActuarialRepository {

    override suspend fun computeMetrics(groupId: String): Result<ActuarialMetrics> =
        withContext(Dispatchers.IO) {
            try {
                financialRepo.repairGroupData(groupId)
                val group = groupRepo.getGroupById(groupId).getOrElse { return@withContext Result.failure(it) }
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrElse { return@withContext Result.failure(it) }
                withContext(Dispatchers.Default) { calculateMetrics(group, members) }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun calculateDynamicJoiningFee(groupId: String): Result<Money> =
        withContext(Dispatchers.IO) {
            runCatching {
                val group = groupRepo.getGroupById(groupId).getOrThrow()
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
                withContext(Dispatchers.Default) {
                    val memberCount = max(1, members.count { it.status != MemberStatus.SUSPENDED })
                    val baseFee = group.joiningFee
                    val reservesPerMember = group.balance / memberCount
                    val equityContribution = reservesPerMember * java.math.BigDecimal("0.40")
                    baseFee + equityContribution
                }
            }
        }

    override suspend fun calculateViabilityPlan(
        groupId: String,
        goalAmount: Money,
        periodMonths: Int,
        actuarialSettings: ActuarialSettings,
    ): Result<ViabilityPlan> = withContext(Dispatchers.IO) {
        kotlinx.coroutines.withTimeoutOrNull(10.seconds) {
            runCatching {
                val group = groupRepo.getGroupById(groupId).getOrThrow()
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
                withContext(Dispatchers.Default) {
                    actuarialEngine.calculateViabilityPlan(group, members, goalAmount, periodMonths, actuarialSettings)
                }
            }
        } ?: Result.failure(Exception("Calculation timed out. Please try again."))
    }

    override suspend fun fetchGroupInsight(groupId: String): Result<GroupFinancialInsight> =
        withContext(Dispatchers.IO) {
            runCatching {
                financialRepo.repairGroupData(groupId)
                val group = groupRepo.getGroupById(groupId).getOrThrow()
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
                withContext(Dispatchers.Default) {
                    actuarialEngine.computeGroupInsight(group, members)
                }
            }
        }

    override fun calculateMetrics(group: Group, members: List<Member>): Result<ActuarialMetrics> = runCatching {
        actuarialEngine.calculateMetrics(group, members)
    }

    override fun computeGroupInsight(group: Group, members: List<Member>): Result<GroupFinancialInsight> = runCatching {
        actuarialEngine.computeGroupInsight(group, members)
    }

    override fun calculateMemberContribution(group: Group, member: Member): Money =
        paymentCalculator.calculateExpectedMemberGroupPart(group, member)
}
