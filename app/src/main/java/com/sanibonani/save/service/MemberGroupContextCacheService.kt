package com.sanibonani.save.service

import com.sanibonani.save.data.utils.filterNotificationsForMember
import com.sanibonani.save.data.utils.partitionMemberNotifications
import com.sanibonani.save.data.utils.PaymentCalculation
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.BeneficiaryRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.LoanRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class MemberGroupContextCacheService @Inject constructor(
	private val memberRepo: MemberRepository,
	private val groupRepo: GroupRepository,
	private val beneficiaryRepo: BeneficiaryRepository,
	private val notificationRepo: NotificationRepository,
	private val loanRepo: LoanRepository
) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private val _contexts = MutableStateFlow<Map<String, CachedGroupContext>>(emptyMap())
	val contexts: StateFlow<Map<String, CachedGroupContext>> = _contexts.asStateFlow()

	private var cachedUserId: String? = null
	private val warmupJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

	fun getContext(groupId: String): CachedGroupContext? = _contexts.value[groupId]

	fun ensureUserSession(userId: String) {
		if (cachedUserId == userId) return
		clearForUser(userId)
	}

	fun clearForSignOut() {
		clearForUser(userId = null)
	}

	fun warmMembershipsInBackground(userId: String, memberships: List<Member>) {
		ensureUserSession(userId)
		pruneToMemberships(memberships)

		memberships.forEach { membership ->
			val groupId = membership.groupId
			if (groupId.isBlank() || warmupJobs[groupId]?.isActive == true) return@forEach
			if (_contexts.value[groupId]?.isFullyHydrated == true) return@forEach

			warmupJobs[groupId] = scope.launch {
				warmGroupContext(userId = userId, groupId = groupId, membership = membership)
			}
		}
	}

	suspend fun warmUpForUser(userId: String) {
		ensureUserSession(userId)
		val memberships = memberRepo.getMemberships(userId).getOrElse { emptyList() }
		warmMembershipsInBackground(userId, memberships)
	}

	fun updateContext(
		groupId: String,
		reducer: (CachedGroupContext) -> CachedGroupContext
	) {
		_contexts.update { cache ->
			val current = cache[groupId] ?: CachedGroupContext()
			cache + (groupId to reducer(current).copy(lastUpdatedMillis = System.currentTimeMillis()))
		}
	}

	private suspend fun warmGroupContext(userId: String, groupId: String, membership: Member) {
		val group = groupRepo.getGroupById(groupId).getOrNull()
		val member = memberRepo.getMemberByUserId(userId, groupId).getOrNull() ?: membership
		val memberId = member.id

		val contributions = if (!memberId.isNullOrBlank()) {
			memberRepo.getMemberContributions(memberId, groupId).first().getOrElse { emptyList() }
		} else {
			emptyList()
		}

		val beneficiaries = if (!memberId.isNullOrBlank()) {
			beneficiaryRepo.syncBeneficiaries(memberId).getOrElse { emptyList() }
		} else {
			emptyList()
		}

		val loans = if (!memberId.isNullOrBlank()) {
			loanRepo.getMemberLoans(memberId).first().getOrElse { emptyList() }
		} else {
			emptyList()
		}

		val notifications = runCatching {
			notificationRepo.syncNotifications(groupId)
			notificationRepo.observeNotifications(groupId).first().getOrElse { emptyList() }
		}.getOrElse { emptyList() }

		val (messages, systemNotifs) = partitionMemberNotifications(
			filterNotificationsForMember(notifications, memberId)
		)

		val calculation = group?.let {
			PaymentCalculator.calculateStatus(it, member, contributions)
		}

		updateContext(groupId) {
			CachedGroupContext(
				member = member,
				group = group,
				contributions = contributions,
				beneficiaries = beneficiaries,
				loans = loans,
				notifications = systemNotifs,
				messages = messages,
				calculation = calculation,
				isFullyHydrated = true
			)
		}
	}

	private fun clearForUser(userId: String?) {
		cachedUserId = userId
		_contexts.value = emptyMap()
		warmupJobs.values.forEach { it.cancel() }
		warmupJobs.clear()
	}

	private fun pruneToMemberships(memberships: List<Member>) {
		val allowedGroupIds = memberships.map { it.groupId }.toSet()
		_contexts.update { cache -> cache.filterKeys { it in allowedGroupIds } }

		val staleJobs = warmupJobs.keys.filter { it !in allowedGroupIds }
		staleJobs.forEach { groupId ->
			warmupJobs.remove(groupId)?.cancel()
		}
	}
}

data class CachedGroupContext(
	val member: Member? = null,
	val group: Group? = null,
	val contributions: List<Contribution> = emptyList(),
	val beneficiaries: List<Beneficiary> = emptyList(),
	val loans: List<Loan> = emptyList(),
	val loanRepayments: List<LoanRepayment> = emptyList(),
	val notifications: List<AppNotification> = emptyList(),
	val messages: List<AppNotification> = emptyList(),
	val calculation: PaymentCalculation? = null,
	val isFullyHydrated: Boolean = false,
	val lastUpdatedMillis: Long = 0L
)

