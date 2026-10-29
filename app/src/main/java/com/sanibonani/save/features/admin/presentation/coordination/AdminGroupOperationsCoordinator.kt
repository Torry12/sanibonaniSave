package com.sanibonani.save.features.admin.presentation.coordination

import com.sanibonani.save.domain.usecase.disbursement.ExecuteRoscaPayoutUseCase
import com.sanibonani.save.domain.usecase.member.SyncGroupMembersUseCase
import com.sanibonani.save.domain.usecase.treasury.ConsolidateGroupLedgerUseCase
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.service.AdminGroupContextCacheService
import javax.inject.Inject

class AdminGroupOperationsCoordinator @Inject constructor(
    private val syncGroupMembers: SyncGroupMembersUseCase,
    private val consolidateGroupLedger: ConsolidateGroupLedgerUseCase,
    private val executeRoscaPayoutUseCase: ExecuteRoscaPayoutUseCase,
    private val adminContextCacheService: AdminGroupContextCacheService
) {

    fun refreshMembers(host: AdminActionHost) {
        val groupId = host.adminState.value.group?.id ?: return
        host.launchAdminSafeJob("refresh_members", "Failed to sync members") {
            host.updateAdminState { it.copy(isLoading = true) }
            host.runWithLoading {
                syncGroupMembers(groupId)
                    .onSuccess {
                        adminContextCacheService.warmUpGroup(groupId, force = true)
                        host.updateAdminState { it.copy(isLoading = false) }
                    }
                    .onFailure { e ->
                        host.updateAdminState { it.copy(isLoading = false) }
                        host.reportError(e)
                    }
            }
        }
    }

    fun consolidateLedger(host: AdminActionHost) {
        val groupId = host.adminState.value.group?.id ?: return
        host.launchAdminSafeJob("ledger_consolidation", "Failed to consolidate ledger") {
            host.updateAdminState { it.copy(isLoading = true) }
            host.runWithLoading {
                consolidateGroupLedger(groupId)
                    .onSuccess {
                        refreshMembers(host)
                        host.updateAdminState { it.copy(isLoading = false) }
                        host.sendUiEvent(UIEvent.ShowMessage("Audit trail repaired."))
                    }
                    .onFailure { e ->
                        host.updateAdminState { it.copy(isLoading = false) }
                        host.reportError(e)
                    }
            }
        }
    }

    fun executeRoscaPayout(host: AdminActionHost) {
        val groupId = host.adminState.value.group?.id ?: return
        host.launchAdminSafeJob("execute_rosca_payout", "Failed to process ROSCA payout") {
            host.updateAdminState { it.copy(isLoading = true) }
            executeRoscaPayoutUseCase(groupId)
                .onSuccess { msg ->
                    host.updateAdminState { it.copy(isLoading = false) }
                    host.sendUiEvent(UIEvent.ShowMessage("ROSCA Payout Processed: $msg"))
                    adminContextCacheService.warmUpGroup(groupId)
                }
                .onFailure { e ->
                    host.updateAdminState { it.copy(isLoading = false) }
                    host.reportError(e)
                }
        }
    }
}
