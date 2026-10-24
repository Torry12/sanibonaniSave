package com.sanibonani.save.features.member.presentation.coordination

import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.repository.admin.AuditRepository
import com.sanibonani.save.domain.repository.system.ExportRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.UIEvent
import javax.inject.Inject

class MemberDocumentCoordinator @Inject constructor(
    private val exportRepo: ExportRepository,
    private val ledgerRepo: LedgerRepository,
    private val auditRepo: AuditRepository
) {

    fun loadAuditReport(host: MemberActionHost) {
        val memberId = host.memberState.value.member?.id ?: return
        host.launchMemberSafeJob("audit_report", "Failed to load audit report") {
            host.updateMemberState { it.copy(memberAuditReport = AsyncResult.Loading) }
            auditRepo.triggerMemberAudit(memberId)
                .onSuccess { report ->
                    host.updateMemberState { it.copy(memberAuditReport = AsyncResult.Success(report)) }
                }
                .onFailure { e ->
                    host.updateMemberState {
                        it.copy(memberAuditReport = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName))
                    }
                }
        }
    }

    fun downloadMembershipContract(host: MemberActionHost) {
        val s = host.memberState.value
        val member = s.member ?: return
        val group = s.group ?: return
        host.launchMemberSafeJob("download_contract", "Failed to download contract") {
            host.updateMemberState { it.copy(isExporting = true) }
            exportRepo.exportMembershipContract(member, group).onSuccess { file ->
                host.updateMemberState { it.copy(isExporting = false) }
                host.sendUiEvent(UIEvent.OpenFile(file, "application/pdf", "Membership Certificate"))
            }.onFailure {
                host.updateMemberState { it.copy(isExporting = false) }
                host.reportError(it)
            }
        }
    }

    fun downloadPdfStatement(host: MemberActionHost) {
        val s = host.memberState.value
        val group = s.group ?: return
        val ledger = s.memberLedger
        host.launchMemberSafeJob("download_statement", "Failed to download statement") {
            host.updateMemberState { it.copy(isExporting = true) }
            exportRepo.exportLedgerToPdf(group, ledger).onSuccess { file ->
                host.updateMemberState { it.copy(isExporting = false) }
                host.sendUiEvent(UIEvent.OpenFile(file, "application/pdf", "Statement"))
            }.onFailure {
                host.updateMemberState { it.copy(isExporting = false) }
                host.reportError(it)
            }
        }
    }

    fun downloadCsvStatement(host: MemberActionHost) {
        val s = host.memberState.value
        val group = s.group ?: return
        val ledger = s.memberLedger
        host.launchMemberSafeJob("download_csv_statement", "Failed to export statement") {
            host.updateMemberState { it.copy(isExporting = true) }
            exportRepo.exportLedgerToCsv(group, ledger).onSuccess { file ->
                host.updateMemberState { it.copy(isExporting = false) }
                host.sendUiEvent(UIEvent.OpenFile(file, "text/csv", "Statement"))
            }.onFailure {
                host.updateMemberState { it.copy(isExporting = false) }
                host.reportError(it)
            }
        }
    }

    fun downloadGroupStatement(host: MemberActionHost) {
        val group = host.memberState.value.group ?: return
        val groupId = group.id ?: run {
            host.reportError(
                IllegalStateException("No server id for group"),
                "This group is not saved on the server yet. Please sync and try again."
            )
            return
        }
        host.launchMemberSafeJob("download_group_ledger", "Failed to download group ledger") {
            host.updateMemberState { it.copy(isExporting = true) }
            ledgerRepo.getGroupLedger(groupId).onSuccess { entries ->
                exportRepo.exportLedgerToPdf(group, entries).onSuccess { file ->
                    host.updateMemberState { it.copy(isExporting = false) }
                    host.sendUiEvent(UIEvent.OpenFile(file, "application/pdf", "Group Ledger"))
                }
            }.onFailure {
                host.updateMemberState { it.copy(isExporting = false) }
                host.reportError(it)
            }
        }
    }

    fun downloadGroupConstitution(host: MemberActionHost) {
        val group = host.memberState.value.group ?: return
        host.launchMemberSafeJob("download_constitution", "Failed to download constitution") {
            host.updateMemberState { it.copy(isLoading = true) }
            exportRepo.exportGroupConstitution(group).onSuccess { file ->
                host.updateMemberState { it.copy(isLoading = false) }
                host.sendUiEvent(UIEvent.OpenFile(file, "application/pdf", "Constitution"))
            }.onFailure {
                host.updateMemberState { it.copy(isLoading = false) }
                host.reportError(it)
            }
        }
    }
}
