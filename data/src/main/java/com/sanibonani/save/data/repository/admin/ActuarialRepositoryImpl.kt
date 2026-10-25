package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.actuarial.GroupTypeActuarialEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Duration.Companion.seconds
import com.sanibonani.save.domain.utils.safeRunCatching

class ActuarialRepositoryImpl @Inject constructor(
    private val groupRepo: GroupRepository,
    private val financialRepo: GroupFinancialRepository,
    private val memberRepo: Provider<MemberRepository>,
    private val actuarialEngine: GroupTypeActuarialEngine,
) : BaseRepository("ActuarialRepository"), ActuarialRepository {

    override suspend fun computeMetrics(groupId: String): Result<ActuarialMetrics> =
        withContext(Dispatchers.IO) {
            try {
                financialRepo.repairGroupData(groupId)
                val group = groupRepo.getGroupById(groupId).getOrElse { return@withContext Result.failure(it) }
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrElse { return@withContext Result.failure(it) }
                withContext(Dispatchers.Default) { calculateMetrics(group, members) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun calculateViabilityPlan(
        groupId: String,
        goalAmount: Money,
        periodMonths: Int,
        actuarialSettings: ActuarialSettings,
    ): Result<ViabilityPlan> = withContext(Dispatchers.IO) {
        kotlinx.coroutines.withTimeoutOrNull(10.seconds) {
            safeRunCatching {
                val group = groupRepo.getGroupById(groupId).getOrThrow()
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
                withContext(Dispatchers.Default) {
                    actuarialEngine.calculateViabilityPlan(group, members, goalAmount, periodMonths, actuarialSettings)
                }
            }
        } ?: Result.failure(Exception("Calculation timed out. Please try again."))
    }

    override fun calculateMetrics(group: Group, members: List<Member>): Result<ActuarialMetrics> = safeRunCatching {
        actuarialEngine.calculateMetrics(group, members)
    }

    override fun computeGroupInsight(group: Group, members: List<Member>): Result<GroupFinancialInsight> = safeRunCatching {
        actuarialEngine.computeGroupInsight(group, members)
    }
}
