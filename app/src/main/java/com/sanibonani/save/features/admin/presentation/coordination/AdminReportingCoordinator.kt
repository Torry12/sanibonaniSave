package com.sanibonani.save.features.admin.presentation.coordination

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.admin.AuditRepository
import com.sanibonani.save.domain.repository.system.ExportRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.service.DataVerificationService
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.UIEvent
import javax.inject.Inject

class AdminReportingCoordinator @Inject constructor(
    private val auditRepo: AuditRepository,
    private val ledgerRepo: LedgerRepository,
    private val exportRepo: ExportRepository,
    private val verificationService: DataVerificationService
) {

    fun loadAuditReport(host: AdminActionHost) {
        val groupId = host.adminState.value.group?.id ?: return
        AppLogger.i("AdminOverview", "Loading Group Audit Report for ID: $groupId")
        host.launchAdminSafeJob("audit_report_loading", "Failed to load audit report") {
            host.updateAdminState { it.copy(groupAuditReport = AsyncResult.Loading) }
            auditRepo.triggerGroupAudit(groupId)
                .onSuccess { report ->
                    host.updateAdminState { it.copy(groupAuditReport = AsyncResult.Success(report)) }
                }
                .onFailure { e ->
                    host.updateAdminState {
                        it.copy(groupAuditReport = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName))
                    }
                }
        }
    }

    fun downloadStatement(host: AdminActionHost) {
        val group = host.adminState.value.group ?: return
        val groupId = group.id ?: return
        host.launchAdminSafeJob("download_statement", "Failed to download statement") {
            host.updateAdminState { it.copy(isLoading = true) }
            ledgerRepo.getGroupLedger(groupId).onSuccess { entries ->
                exportRepo.exportLedgerToPdf(group, entries).onSuccess { file ->
                    host.updateAdminState { it.copy(isLoading = false) }
                    host.sendUiEvent(UIEvent.OpenFile(file, "application/pdf", "Group Statement"))
                }.onFailure { e ->
                    host.updateAdminState { it.copy(isLoading = false) }
                    host.reportError(e)
                }
            }.onFailure { e ->
                host.updateAdminState { it.copy(isLoading = false) }
                host.reportError(e)
            }
        }
    }

    fun downloadConstitution(host: AdminActionHost) {
        val group = host.adminState.value.group ?: return
        host.launchAdminSafeJob("download_constitution", "Failed to download constitution") {
            host.updateAdminState { it.copy(isLoading = true) }
            host.runWithLoading {
                exportRepo.exportGroupConstitution(group).onSuccess { file ->
                    host.updateAdminState { it.copy(isLoading = false) }
                    host.sendUiEvent(UIEvent.OpenFile(file, "application/pdf", "Group Constitution"))
                }.onFailure { e ->
                    host.updateAdminState { it.copy(isLoading = false) }
                    host.reportError(e)
                }
            }
        }
    }

    suspend fun verify(host: AdminActionHost, group: Group, members: List<Member>, ledger: List<LedgerEntry>) {
        val verification = verificationService.verifyGroupAdminData(group, members, ledger)
        host.updateAdminState { it.copy(verificationResult = verification) }
    }
}
