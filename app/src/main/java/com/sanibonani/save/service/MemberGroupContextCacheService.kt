package com.sanibonani.save.service

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.filterNotificationsForMember
import com.sanibonani.save.domain.utils.partitionMemberNotifications
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.repository.system.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

@Singleton
class MemberGroupContextCacheService @Inject constructor(
	private val memberRepo: MemberRepository,
	private val groupRepo: GroupRepository,
	private val beneficiaryRepo: BeneficiaryRepository,
	private val contributionRepo: MemberContributionRepository,
	private val notificationRepo: NotificationRepository,
	private val loanRepo: LoanRepository,
	private val paymentCalculator: PaymentCalculator,
	private val syncRepo: SyncRepository
) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private val _contexts = MutableStateFlow<Map<String, CachedGroupContext>>(emptyMap())
	val contexts: StateFlow<Map<String, CachedGroupContext>> = _contexts.asStateFlow()

	private var cachedUserId: String? = null
	private val warmupCoordinator = GroupContextWarmupCoordinator()

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
			if (groupId.isBlank()) return@forEach
			warmupCoordinator.launch(groupId, scope) {
				warmGroupContext(userId = userId, groupId = groupId, membership = membership)
			}
		}
	}

	suspend fun warmUpForUser(userId: String) {
		ensureUserSession(userId)
		val memberships = memberRepo.getMemberships(userId).getOrElse {
			// An offline warm-up must keep the cached memberships instead of pruning every
			// context to an empty membership set.
			memberRepo.observeMemberships(userId).first().getOrElse {
				// Both reads failed, so the empty list here is a missing answer rather than an
				// authoritative "no memberships". Pruning with it would delete the very contexts
				// this fallback protects; keep them and warm again once a read succeeds.
				return
			}
		}
		warmMembershipsInBackground(userId, memberships)
	}

	/**
	 * Pulls every group pillar from the network, then rebuilds the snapshot of [userId]'s
	 * membership. Unlike [warmMembershipsInBackground] this always re-hydrates, even when
	 * the context is already marked as fully hydrated.
	 */
	suspend fun refreshGroup(userId: String, groupId: String): Result<Unit> = safeRunCatching {
		if (groupId.isBlank()) return@safeRunCatching
		// Hold the group's warmup slot for the whole refresh so a background sweep cannot start a
		// second hydration that finishes after this one; a sweep arriving once this run has read
		// is queued behind it rather than discarded. The slot is a child job, never the
		// caller's, so a session change cancels the refresh instead of the caller's scope.
		warmupCoordinator.runExclusive(groupId, scope) {
			syncRepo.syncGroupData(groupId)
			val membership = _contexts.value[groupId]?.member
				?: memberRepo.getMemberByUserId(userId, groupId).getOrElse { throw it }
			warmGroupContext(userId = userId, groupId = groupId, membership = membership)
		}
	}

	/**
	 * Re-syncs the group and rebuilds the portal for the member an admin is impersonating.
	 * The member is re-read by id, never by the operator's user id.
	 */
	suspend fun refreshImpersonatedMember(memberId: String, groupId: String): Result<Unit> =
		safeRunCatching {
			if (memberId.isBlank() || groupId.isBlank()) return@safeRunCatching
			warmupCoordinator.runExclusive(groupId, scope) {
				syncRepo.syncGroupData(groupId)
				check(hydrateImpersonatedMember(memberId, groupId).member?.id == memberId) {
					"The selected member could not be verified after hydration."
				}
				Unit
			}
		}

	/**
	 * Hydrates the cache for a member an admin is impersonating (support mode).
	 * The member record is fetched by id rather than by the caller's user id so
	 * the portal reflects the impersonated member, never the operator.
	 */
	suspend fun impersonateMember(memberId: String, groupId: String): Result<CachedGroupContext> =
		safeRunCatching {
			require(memberId.isNotBlank() && groupId.isNotBlank()) { "A member and group must be selected." }
			warmupCoordinator.runExclusive(groupId, scope) {
				hydrateImpersonatedMember(memberId, groupId)
			}
		}

	private suspend fun hydrateImpersonatedMember(memberId: String, groupId: String): CachedGroupContext {
		val member = memberRepo.getMemberById(memberId).getOrThrow()
		require(member.id == memberId) { "The selected member could not be verified." }
		require(member.groupId == groupId) { "The selected member does not belong to this group." }
		warmGroupContext(
			userId = member.userId.orEmpty(),
			groupId = groupId,
			membership = member,
			authoritativeMember = member
		)
		return _contexts.value[groupId] ?: error("Support context was not hydrated for group $groupId")
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

	private suspend fun warmGroupContext(
		userId: String,
		groupId: String,
		membership: Member,
		authoritativeMember: Member? = null
	) {
		// From here the snapshot is being built from data read now, so a warmup request that
		// arrives later needs its own run rather than being credited to this one.
		warmupCoordinator.markReadsStarted(groupId)
		// A failed section read must keep what the snapshot already holds: replacing it with an
		// empty list made an offline refresh look like an empty portal, and the hydrated flag
		// then suppressed every later retry.
		val (groupOutcome, memberOutcome) = coroutineScope {
			val groupTask = async {
				safeRunCatching { groupRepo.getGroupById(groupId).getOrThrow() }
			}
			val memberTask = async {
				when {
					authoritativeMember != null -> Result.success(authoritativeMember)
					userId.isBlank() -> Result.success(membership)
					else -> safeRunCatching {
						memberRepo.getMemberByUserId(userId, groupId).getOrThrow()
					}
				}
			}
			groupTask.await() to memberTask.await()
		}
		val member = memberOutcome.getOrNull() ?: membership

		val hydrated = coroutineScope {
			val contributionsTask = async {
				member.id?.takeIf { it.isNotBlank() }?.let {
					safeRunCatching { contributionRepo.getMemberContributions(it, groupId).first().getOrThrow() }
				} ?: Result.success(emptyList())
			}
			val beneficiariesTask = async {
				member.id?.takeIf { it.isNotBlank() }?.let {
					safeRunCatching { beneficiaryRepo.syncBeneficiaries(it).getOrThrow() }
				} ?: Result.success(emptyList())
			}
			val loansTask = async {
				member.id?.takeIf { it.isNotBlank() }?.let {
					safeRunCatching { loanRepo.getMemberLoans(it).first().getOrThrow() }
				} ?: Result.success(emptyList())
			}
			val notificationsTask = async {
				safeRunCatching {
					notificationRepo.syncNotifications(groupId)
					notificationRepo.observeNotifications(groupId).first().getOrThrow()
				}
			}
			HydratedMemberData(
				contributions = contributionsTask.await(),
				beneficiaries = beneficiariesTask.await(),
				loans = loansTask.await(),
				notifications = notificationsTask.await()
			)
		}

		updateContext(groupId) { prev ->
			val group = groupOutcome.getOrNull() ?: prev.group
			val notifications = hydrated.notifications.getOrNull()
			val (messages, systemNotifs) = if (notifications != null) {
				partitionMemberNotifications(filterNotificationsForMember(notifications, member.id))
			} else {
				prev.messages to prev.notifications
			}
			val contributions = hydrated.contributions.getOrNull() ?: prev.contributions
			prev.copy(
				member = member,
				group = group,
				contributions = contributions,
				beneficiaries = hydrated.beneficiaries.getOrNull() ?: prev.beneficiaries,
				loans = hydrated.loans.getOrNull() ?: prev.loans,
				notifications = systemNotifs,
				messages = messages,
				calculation = if (group != null) {
					paymentCalculator.calculateStatus(group, member, contributions)
				} else {
					prev.calculation
				},
				isFullyHydrated = groupOutcome.isSuccess &&
					memberOutcome.isSuccess &&
					hydrated.contributions.isSuccess &&
					hydrated.beneficiaries.isSuccess &&
					hydrated.loans.isSuccess &&
					hydrated.notifications.isSuccess
			)
		}
	}

	private fun clearForUser(userId: String?) {
		cachedUserId = userId
		_contexts.value = emptyMap()
		warmupCoordinator.cancelAll()
	}

	private fun pruneToMemberships(memberships: List<Member>) {
		val allowedGroupIds = memberships.map { it.groupId }.toSet()
		_contexts.update { cache -> cache.filterKeys { it in allowedGroupIds } }

		warmupCoordinator.retainOnly(allowedGroupIds)
	}
}

private data class HydratedMemberData(
	val contributions: Result<List<Contribution>>,
	val beneficiaries: Result<List<Beneficiary>>,
	val loans: Result<List<Loan>>,
	val notifications: Result<List<AppNotification>>
)

data class CachedGroupContext(
	val member: Member? = null,
	val group: Group? = null,
	val contributions: List<Contribution> = emptyList(),
	val beneficiaries: List<Beneficiary> = emptyList(),
	val loans: List<Loan> = emptyList(),
	val notifications: List<AppNotification> = emptyList(),
	val messages: List<AppNotification> = emptyList(),
	val calculation: PaymentCalculation? = null,
	val isFullyHydrated: Boolean = false,
	val lastUpdatedMillis: Long = 0L
)
