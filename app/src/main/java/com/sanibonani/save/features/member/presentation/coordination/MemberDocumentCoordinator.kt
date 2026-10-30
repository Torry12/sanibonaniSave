package com.sanibonani.save.features.member.presentation.coordination

import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.usecase.admin.GenerateMemberAuditReportUseCase
import com.sanibonani.save.domain.usecase.system.BuildGroupLedgerStatementUseCase
import com.sanibonani.save.domain.usecase.system.ExportGroupConstitutionUseCase
import com.sanibonani.save.domain.usecase.system.ExportLedgerStatementUseCase
import com.sanibonani.save.domain.usecase.system.ExportMembershipContractUseCase
import com.sanibonani.save.features.common.presentation.UIEvent
import javax.inject.Inject

class MemberDocumentCoordinator @Inject constructor(
    private val generateMemberAuditReport: GenerateMemberAuditReportUseCase,
    private val exportMembershipContract: ExportMembershipContractUseCase,
    private val exportGroupConstitution: ExportGroupConstitutionUseCase,
    private val exportLedgerStatement: ExportLedgerStatementUseCase,
    private val buildGroupLedgerStatement: BuildGroupLedgerStatementUseCase
) {

    fun loadAuditReport(host: MemberActionHost) {
        val memberId = host.memberState.value.member?.id ?: return
        host.launchMemberSafeJob("audit_report", "Failed to load audit report") {
            host.updateMemberState { it.copy(memberAuditReport = AsyncResult.Loading) }
            generateMemberAuditReport(memberId)
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
            exportMembershipContract(member, group).onSuccess { file ->
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
            exportLedgerStatement(group, ledger, ExportLedgerStatementUseCase.Format.PDF).onSuccess { file ->
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
            exportLedgerStatement(group, ledger, ExportLedgerStatementUseCase.Format.CSV).onSuccess { file ->
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
        host.launchMemberSafeJob("download_group_ledger", "Failed to download group ledger") {
            host.updateMemberState { it.copy(isExporting = true) }
            buildGroupLedgerStatement(group).onSuccess { file ->
                host.updateMemberState { it.copy(isExporting = false) }
                host.sendUiEvent(UIEvent.OpenFile(file, "application/pdf", "Group Ledger"))
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
            exportGroupConstitution(group).onSuccess { file ->
                host.updateMemberState { it.copy(isLoading = false) }
                host.sendUiEvent(UIEvent.OpenFile(file, "application/pdf", "Constitution"))
            }.onFailure {
                host.updateMemberState { it.copy(isLoading = false) }
                host.reportError(it)
            }
        }
    }
}
