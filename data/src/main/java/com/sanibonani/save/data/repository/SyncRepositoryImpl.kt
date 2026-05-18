package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.utils.logAndGetMessage
import com.sanibonani.save.data.utils.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

class SyncRepositoryImpl @Inject constructor(
    private val supabaseRepo: SupabaseRepository,
    private val memberRepo: MemberRepository,
    private val groupRepo: GroupRepository,
    private val notificationRepo: NotificationRepository
) : BaseRepository("SyncRepository"), SyncRepository {

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus = _syncStatus.asStateFlow()

    override suspend fun syncAllData(): Result<Unit> = runCatching {
        kotlinx.coroutines.withTimeout(30000) { // 30s timeout for full sync
            val userId = supabaseRepo.currentUserId ?: run {
                val message = "Please sign in to sync your data."
                _syncStatus.value = SyncStatus.Error(message)
                throw IllegalStateException(message)
            }

            _syncStatus.value = SyncStatus.Progress("Syncing memberships...", 0.1f)
            val memberships = memberRepo.getMemberships(userId).getOrThrow()
            
            if (memberships.isEmpty()) {
                _syncStatus.value = SyncStatus.Completed
                return@withTimeout
            }

            val totalSteps = memberships.size * 2 + 1 
            var currentStep = 1

            memberships.forEach { membership ->
                val groupId = membership.groupId
                if (groupId.isBlank()) {
                    currentStep += 2
                    return@forEach
                }
                
                _syncStatus.value = SyncStatus.Progress("Updating group details...", currentStep.toFloat() / totalSteps)
                runCatching { groupRepo.getGroupById(groupId).getOrThrow() }
                    .onFailure {
                        val userMsg = it.logAndGetMessage(tag)
                        AppLogger.e(tag, "Failed to sync group $groupId: $userMsg")
                    }
                currentStep++

                _syncStatus.value = SyncStatus.Progress("Syncing notifications...", currentStep.toFloat() / totalSteps)
                runCatching { notificationRepo.syncNotifications(groupId).getOrThrow() }
                    .onFailure {
                        val userMsg = it.logAndGetMessage(tag)
                        AppLogger.e(tag, "Failed to sync notifications for $groupId: $userMsg")
                    }
                currentStep++
            }

            _syncStatus.value = SyncStatus.Completed
        }
    }.onFailure { e ->
        val userMessage = e.toUserMessage()
        AppLogger.e(tag, "Full sync failed: $userMessage")
        _syncStatus.value = SyncStatus.Error(userMessage)
    }
}
