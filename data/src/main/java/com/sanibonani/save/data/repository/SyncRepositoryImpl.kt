package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.utils.logAndGetMessage
import com.sanibonani.save.data.utils.toUserMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class SyncRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val supabaseRepo: SupabaseRepository,
    private val memberRepo: MemberRepository,
    private val groupRepo: GroupRepository,
    private val notificationRepo: NotificationRepository,
    private val db: SanibonaniDatabase
) : BaseRepository("SyncRepository"), SyncRepository {

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus = _syncStatus.asStateFlow()

    override suspend fun syncAllData(): Result<Unit> = runCatching {
        kotlinx.coroutines.withTimeout(120_000) {
            val userId = supabaseRepo.currentUserId ?: run {
                val message = "Please sign in to sync your data."
                _syncStatus.value = SyncStatus.Error(message)
                throw IllegalStateException(message)
            }

            _syncStatus.value = SyncStatus.Progress("Syncing memberships...", 0.02f)
            val memberships = memberRepo.getMemberships(userId).getOrThrow()

            val adminGroups = groupRepo.getGroupsByAdmin(userId).getOrElse { emptyList() }
            adminGroups.forEach { group ->
                group.id?.let { groupId ->
                    runCatching { groupRepo.getGroupById(groupId).getOrThrow() }
                        .onFailure { AppLogger.e(tag, "Admin group sync failed for $groupId: ${it.logAndGetMessage(tag)}") }
                }
            }

            val groupIds = buildSet {
                memberships.mapTo(this) { it.groupId }
                adminGroups.mapNotNullTo(this) { it.id }
            }.filter { it.isNotBlank() }

            if (groupIds.isEmpty()) {
                _syncStatus.value = SyncStatus.Completed
                return@withTimeout
            }

            val totalSteps = groupIds.size * 9
            var currentStep = 0

            groupIds.forEach { groupId ->
                currentStep++
                _syncStatus.value = SyncStatus.Progress("Updating group details...", currentStep.toFloat() / totalSteps)
                runCatching { groupRepo.getGroupById(groupId).getOrThrow() }
                    .onFailure { AppLogger.e(tag, "Failed to sync group $groupId: ${it.logAndGetMessage(tag)}") }

                currentStep++
                _syncStatus.value = SyncStatus.Progress("Syncing members...", currentStep.toFloat() / totalSteps)
                runCatching { memberRepo.syncGroupMembers(groupId).getOrThrow() }
                    .onFailure { AppLogger.e(tag, "Failed to sync members for $groupId: ${it.logAndGetMessage(tag)}") }

                currentStep++
                _syncStatus.value = SyncStatus.Progress("Syncing contributions...", currentStep.toFloat() / totalSteps)
                runCatching {
                    val data = supabase.postgrest["contributions"].select {
                        filter { eq("group_id", groupId) }
                    }.decodeList<com.sanibonani.save.domain.model.Contribution>()
                    db.contributionDao().syncGroupContributions(groupId, data.map { it.toEntity() })
                }.onFailure { AppLogger.e(tag, "Failed to sync contributions for $groupId: ${it.logAndGetMessage(tag)}") }

                currentStep++
                _syncStatus.value = SyncStatus.Progress("Syncing payments...", currentStep.toFloat() / totalSteps)
                runCatching {
                    val data = supabase.postgrest["payments"].select {
                        filter { eq("group_id", groupId) }
                    }.decodeList<com.sanibonani.save.domain.model.Payment>()
                    db.paymentDao().syncPayments(groupId, data.map { it.toEntity() })
                }.onFailure { AppLogger.e(tag, "Failed to sync payments for $groupId: ${it.logAndGetMessage(tag)}") }

                currentStep++
                _syncStatus.value = SyncStatus.Progress("Syncing loans...", currentStep.toFloat() / totalSteps)
                runCatching {
                    val data = supabase.postgrest["loans"].select {
                        filter { eq("group_id", groupId) }
                    }.decodeList<com.sanibonani.save.domain.model.Loan>()
                    db.loanDao().syncGroupLoans(groupId, data.map { it.toEntity() })
                }.onFailure { AppLogger.e(tag, "Failed to sync loans for $groupId: ${it.logAndGetMessage(tag)}") }

                currentStep++
                _syncStatus.value = SyncStatus.Progress("Syncing ledger...", currentStep.toFloat() / totalSteps)
                runCatching {
                    val data = supabase.postgrest["group_ledger"].select(columns = Columns.raw("*")) {
                        filter { eq("group_id", groupId) }
                    }.decodeList<com.sanibonani.save.domain.model.LedgerEntry>()
                    db.ledgerDao().syncLedger(groupId, data.map { it.toLedgerEntity() })
                }.onFailure { AppLogger.e(tag, "Failed to sync ledger for $groupId: ${it.logAndGetMessage(tag)}") }

                currentStep++
                _syncStatus.value = SyncStatus.Progress("Syncing payouts...", currentStep.toFloat() / totalSteps)
                runCatching {
                    val data = supabase.postgrest["payouts"].select {
                        filter { eq("group_id", groupId) }
                    }.decodeList<com.sanibonani.save.domain.model.PayoutRequest>()
                    db.payoutDao().syncPayouts(groupId, data.map { it.toEntity() })
                }.onFailure { AppLogger.e(tag, "Failed to sync payouts for $groupId: ${it.logAndGetMessage(tag)}") }

                currentStep++
                _syncStatus.value = SyncStatus.Progress("Syncing claims...", currentStep.toFloat() / totalSteps)
                runCatching {
                    try {
                        val data = supabase.postgrest["beneficiary_payout_claims"].select(columns = Columns.raw("*")) {
                            filter { eq("group_id", groupId) }
                        }.decodeList<com.sanibonani.save.domain.model.BeneficiaryPayoutClaim>()
                        db.beneficiaryClaimDao().syncForGroup(groupId, data.map { it.toEntity() })
                    } catch (e: Exception) {
                        val data = supabase.postgrest["burial_claims"].select(columns = Columns.raw("*")) {
                            filter { eq("group_id", groupId) }
                        }.decodeList<com.sanibonani.save.domain.model.BeneficiaryPayoutClaim>()
                        db.beneficiaryClaimDao().syncForGroup(groupId, data.map { it.toEntity() })
                    }
                }.onFailure { AppLogger.e(tag, "Failed to sync claims for $groupId: ${it.logAndGetMessage(tag)}") }

                currentStep++
                _syncStatus.value = SyncStatus.Progress("Syncing notifications...", currentStep.toFloat() / totalSteps)
                runCatching { notificationRepo.syncNotifications(groupId).getOrThrow() }
                    .onFailure { AppLogger.e(tag, "Failed to sync notifications for $groupId: ${it.logAndGetMessage(tag)}") }
            }

            _syncStatus.value = SyncStatus.Completed
        }
    }.onFailure { e ->
        val userMessage = e.toUserMessage()
        AppLogger.e(tag, "Full sync failed: $userMessage")
        _syncStatus.value = SyncStatus.Error(userMessage)
    }
}
