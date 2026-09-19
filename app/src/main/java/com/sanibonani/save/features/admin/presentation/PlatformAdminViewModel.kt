package com.sanibonani.save.features.admin.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.utils.*
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.dynamics.*
import com.sanibonani.save.domain.repository.treasury.*
import com.sanibonani.save.domain.repository.disbursement.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.repository.equity.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.usecase.disbursement.*
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.domain.service.*
import com.sanibonani.save.data.sync.*
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.UIEvent
import com.sanibonani.save.features.admin.presentation.state.PlatformAdminUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PlatformAdminViewModel @Inject constructor(
    private val platformRepo: PlatformRepository,
    private val claimRepo: BeneficiaryClaimRepository,
    private val ledgerRepo: LedgerRepository,
    private val payoutRepo: PayoutRepository,
    private val memberRepo: MemberRepository,
    private val loanRepo: LoanRepository,
    private val reconRepo: ReconciliationRepository,
    private val processLoanDisbursementUseCase: ProcessLoanDisbursementUseCase,
    private val processDisbursementUseCase: ProcessDisbursementUseCase,
    private val processBurialClaimUseCase: ProcessBurialClaimUseCase,
    private val auditLogRepo: AuditLogRepository,
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<PlatformAdminUiState>(PlatformAdminUiState()) {

    val state: StateFlow<PlatformAdminUiState> = uiState

    init {
        viewModelScope.launch {
            if (supabaseRepo.getUserRole() != UserRole.PLATFORM_ADMIN) return@launch
            loadData()
        }
    }

    fun loadData() {
        launchJob("load_data") {
            updateState { it.copy(isLoading = true, analytics = AsyncResult.Loading) }
            coroutineScope {
                val analyticsRes = platformRepo.getPlatformAnalytics()
                val groupsDeferred = async { platformRepo.getAllGroups().getOrNull() ?: emptyList() }
                val claimsDeferred = async { claimRepo.getEscalatedClaimsSync().getOrNull() ?: emptyList() }
                val ledgerDeferred = async { ledgerRepo.getPlatformLedger().getOrNull() ?: emptyList() }
                val payoutsDeferred = async { payoutRepo.getPendingPayouts().getOrNull() ?: emptyList() }
                val loansDeferred = async { loanRepo.getAllPendingLoansSync().getOrNull() ?: emptyList() }

                val groups = groupsDeferred.await()
                val claims = claimsDeferred.await()
                val ledger = ledgerDeferred.await()
                val payouts = payoutsDeferred.await()
                val loans = loansDeferred.await()

                // Derive distributions
                val provinceDist = groups.groupBy { it.province }
                    .map { (it.key ?: "Unknown") to it.value.size }
                    .sortedByDescending { it.second }

                val marketDist = groups.groupBy { it.type.name }
                    .map { it.key to it.value.size }
                    .sortedByDescending { it.second }

                // Map helper data
                val groupNameMap = groups.associate { (it.id ?: "") to it.name }
                val groupProvinceMap = groups.associate { (it.id ?: "") to (it.province ?: "Other") }
                val loansByGroup = loans.groupBy { it.groupId }

                analyticsRes.onSuccess { analytics ->
                    updateState { it.copy(
                        analytics = AsyncResult.Success(analytics),
                        groups = groups,
                        escalatedClaims = claims,
                        platformLedger = ledger,
                        payouts = payouts,
                        loanRequestsByGroup = loansByGroup,
                        groupNames = groupNameMap,
                        groupProvinces = groupProvinceMap,
                        provinceDistribution = provinceDist,
                        marketSegments = marketDist,
                        totalRevenueShare = analytics.totalPlatformFees,
                        isLoading = false
                    ) }
                }.onFailure { e ->
                    updateState { it.copy(
                        analytics = AsyncResult.Error(e.toUserMessage(), e),
                        groups = groups,
                        escalatedClaims = claims,
                        platformLedger = ledger,
                        payouts = payouts,
                        loanRequestsByGroup = loansByGroup,
                        isLoading = false
                    ) }
                }
            }
        }
    }

    fun selectImpersonationGroup(groupId: String) {
        if (groupId.isBlank()) {
            updateState { it.copy(impersonationGroupId = null, impersonationMembers = emptyList()) }
            return
        }
        launchJob("impersonation_members") {
            updateState { it.copy(impersonationGroupId = groupId, isLoadingImpersonationMembers = true) }
            memberRepo.syncGroupMembers(groupId)
            memberRepo.getGroupMembers(groupId).first().onSuccess { members ->
                updateState { it.copy(impersonationMembers = members, isLoadingImpersonationMembers = false) }
            }
        }
    }

    fun searchAllMembers(query: String) {
        if (query.length < 3) return
        launchJob("search_members") {
            platformRepo.searchMembers(query).onSuccess { insights ->
                updateState { it.copy(memberBehaviorInsights = insights) }
            }
        }
    }

    fun setActive(active: Boolean) {
        if (active && !state.value.isLoading) loadData()
    }

    fun setTab(index: Int) {
        updateState { it.copy(selectedTab = index) }
    }

    fun downloadPlatformStatement() {
        showMessage("Platform statement export is currently in development.")
    }

    fun fetchReconciliations(groupId: String) {
        launchJob("fetch_recon_$groupId") {
            reconRepo.observeReconciliations(groupId).collect { result ->
                result.onSuccess { list ->
                    updateState { it.copy(groupReconciliations = it.groupReconciliations + (groupId to list)) }
                }
            }
        }
    }

    fun reconcileGroup(groupId: String) {
        launchJob("reconcile") {
            updateState { it.copy(isReconciling = true) }
            platformRepo.repairGroupData(groupId).onSuccess { loadData() }.onFailure { handleError(it) }
            updateState { it.copy(isReconciling = false) }
        }
    }

    fun repairGroupFinancials(groupId: String) {
        launchJob("repair") {
            updateState { it.copy(isRepairing = true) }
            platformRepo.repairGroupData(groupId).onSuccess { loadData() }.onFailure { handleError(it) }
            updateState { it.copy(isRepairing = false) }
        }
    }

    fun consolidateGroupLedger(groupId: String) {
        launchJob("consolidate") {
            updateState { it.copy(isConsolidating = true) }
            ledgerRepo.consolidateGroupLedger(groupId).onSuccess { loadData() }.onFailure { handleError(it) }
            updateState { it.copy(isConsolidating = false) }
        }
    }

    fun updateBroadcastMessage(message: String) {
        updateState { it.copy(broadcastMessage = message) }
    }

    fun broadcastMessage(message: String) {
        launchJob("broadcast") {
            updateState { it.copy(isBroadcasting = true, broadcastSuccess = false) }
            platformRepo.broadcastPlatformMessage(message).onSuccess {
                updateState { it.copy(isBroadcasting = false, broadcastSuccess = true, broadcastMessage = "") }
                showMessage("Broadcast sent successfully")
            }.onFailure {
                updateState { it.copy(isBroadcasting = false) }
                handleError(it)
            }
        }
    }

    fun resetLocalData() {
        showMessage("Local data reset is currently in development.")
    }

    fun syncPlatformMetrics() {
        launchJob("sync_metrics") {
            updateState { it.copy(isLoading = true) }
            platformRepo.computeSystemWideMetrics().onSuccess {
                showMessage("System-wide metrics recalculated successfully")
                loadData()
            }.onFailure { handleError(it) }
        }
    }

    fun updateMemberCharge(value: String) { updateState { it.copy(memberCharge = value) } }
    fun updateRegistrationFee(value: String) { updateState { it.copy(registrationFee = value) } }
    fun updatePayoutFee(value: String) { updateState { it.copy(payoutFee = value) } }
    fun updateLateFeePercent(value: String) { updateState { it.copy(lateFeePercent = value) } }
    fun updateShortfallRecoveryFee(value: String) { updateState { it.copy(shortfallRecoveryFee = value) } }
    fun updateAutoSuspensionDays(value: String) { updateState { it.copy(autoSuspensionDays = value) } }

    fun saveGlobalFees() {
        launchJob("save_fees") {
            updateState { it.copy(isSaving = true) }
            platformRepo.updateGlobalFees(
                memberCharge = Money(uiState.value.memberCharge.ifBlank { "0.00" }),
                registrationFee = Money(uiState.value.registrationFee.ifBlank { "0.00" }),
                payoutFee = Money(uiState.value.payoutFee.ifBlank { "0.00" }),
                lateFeePercent = Money(uiState.value.lateFeePercent.ifBlank { "0.00" }),
                shortfallRecoveryFee = Money(uiState.value.shortfallRecoveryFee.ifBlank { "0.00" }),
                autoSuspensionDays = uiState.value.autoSuspensionDays.toIntOrNull() ?: 30
            ).onSuccess {
                updateState { it.copy(isSaving = false, saveSuccess = true) }
                showMessage("Global fees updated")
            }.onFailure {
                updateState { it.copy(isSaving = false) }
                handleError(it)
            }
        }
    }

    fun updateGranularFee(type: GroupType, cycle: Int, field: String, value: String) {
        val current = uiState.value.granularFees.find { it.groupType == type && it.roscaCycle == cycle } ?: PlatformFeeConfig(groupType = type, roscaCycle = cycle)
        val updated = when (field) {
            "registration" -> current.copy(registrationFee = Money(value.toDoubleOrNull() ?: 0.0))
            "monthly" -> current.copy(monthlyFee = Money(value.toDoubleOrNull() ?: 0.0))
            "shortfall" -> current.copy(shortfallRecoveryFee = Money(value.toDoubleOrNull() ?: 0.0))
            else -> current
        }
        updateState { it.copy(granularFees = it.granularFees.filterNot { it.groupType == type && it.roscaCycle == cycle } + updated) }
    }

    fun saveGranularFees() {
        launchJob("save_granular_fees") {
            updateState { it.copy(isSavingGranularFee = true) }
            uiState.value.granularFees.forEach {
                platformRepo.updateGranularFeeConfig(it).onFailure { e -> AppLogger.e("PlatformVM", "Failed to save fee for ${it.groupType}", e) }
            }
            updateState { it.copy(isSavingGranularFee = false) }
            showMessage("Granular fees saved")
        }
    }


    fun approvePayout(payoutId: String, groupId: String? = null) {
        processPayoutInternal(payoutId, groupId ?: "", PayoutStatus.GROUP_APPROVED)
    }

    fun rejectPayout(payoutId: String, groupId: String? = null) {
        processPayoutInternal(payoutId, groupId ?: "", PayoutStatus.FAILED)
    }

    fun completePayout(payoutId: String, groupId: String? = null) {
        processPayoutInternal(payoutId, groupId ?: "", PayoutStatus.COMPLETED)
    }

    private fun processPayoutInternal(payoutId: String, groupId: String, status: PayoutStatus) {
        launchJob("process_payout_$payoutId") {
            updateState { it.copy(isProcessingPayout = true) }
            processDisbursementUseCase(
                payoutId = payoutId,
                groupId = groupId,
                status = status,
                adminId = supabaseRepo.currentUserId ?: ""
            ).onSuccess {
                loadData()
                showMessage("Payout status updated to ${status.name}")
            }.onFailure { handleActionFailure(it) }
            updateState { it.copy(isProcessingPayout = false) }
        }
    }

    private fun handleActionFailure(e: Throwable) {
        val msg = e.message ?: ""
        if (msg.contains("MFA_REQUIRED")) {
            updateState { it.copy(showMfaChallenge = true, mfaChallengeError = null) }
        } else {
            handleError(e)
        }
    }

    fun fetchMemberSummary(memberId: String, groupId: String) {
        launchJob("member_summary") {
            updateState { it.copy(isLoadingMemberSummary = true) }
            memberRepo.getMemberById(memberId).onSuccess { member ->
                updateState { it.copy(selectedMemberSummary = MemberSummary(member = member, behavior = null), isLoadingMemberSummary = false) }
            }.onFailure {
                updateState { it.copy(isLoadingMemberSummary = false) }
                handleError(it)
            }
        }
    }

    fun dismissMemberSummary() {
        updateState { it.copy(selectedMemberSummary = null) }
    }

    fun loadPlatformAuditReport() {
        launchJob("platform_audit") {
            updateState { it.copy(platformAuditReport = AsyncResult.Loading, isLoadingAuditReport = true, error = null) }
            platformRepo.getPlatformAuditReport()
                .onSuccess { report ->
                    updateState { it.copy(platformAuditReport = AsyncResult.Success(report), isLoadingAuditReport = false) }
                }
                .onFailure { e ->
                    updateState { it.copy(platformAuditReport = AsyncResult.Error(e.toUserMessage(), e), isLoadingAuditReport = false) }
                    handleError(e)
                }
        }
    }

    fun clearError() {
        updateState { it.copy(error = null) }
    }

    fun fetchGroupMetrics(groupId: String) {
        viewModelScope.launch {
            platformRepo.getGroupMetrics(groupId).onSuccess { metrics -> updateState { it.copy(selectedGroupMetrics = metrics) } }
        }
    }

    fun verifyMfaChallenge(code: String) {
        launchJob("verify_mfa") {
            updateState { it.copy(isVerifyingMfa = true) }
            supabaseRepo.getMfaFactors().onSuccess { factors ->
                val factor = factors.firstOrNull() ?: return@onSuccess
                supabaseRepo.verifyMfa(factor.id, code, true).onSuccess {
                    updateState { it.copy(isVerifyingMfa = false, showMfaChallenge = false) }
                    loadData()
                    showMessage("MFA Verified")
                }.onFailure { e -> updateState { it.copy(isVerifyingMfa = false, mfaChallengeError = e.toUserMessage()) } }
            }
        }
    }

    fun dismissMfaChallenge() {
        updateState { it.copy(showMfaChallenge = false) }
    }




    fun updateSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
    }

    fun triggerImpersonationAudit(memberId: String, groupId: String) {
        launchJob("impersonation_audit") {
            auditLogRepo.logEvent(
                AuditLog(
                    actorId = supabaseRepo.currentUserId ?: "admin",
                    targetMemberId = memberId,
                    targetGroupId = groupId,
                    action = "IMPERSONATION_REQUESTED",
                    details = buildJsonObject {
                        put("source", "PlatformAdminPortal")
                        put("timestamp", System.currentTimeMillis())
                    }
                )
            )
        }
    }

    fun triggerCloudBackup() {
        launchJob("backup") {
            updateState { it.copy(isBackingUp = true) }
            platformRepo.triggerCloudBackup().onSuccess {
                loadBackups()
                showMessage("Cloud backup triggered")
            }.onFailure { handleError(it) }
            updateState { it.copy(isBackingUp = false) }
        }
    }

    fun loadBackups() {
        launchJob("load_backups") {
            updateState { it.copy(isFetchingBackups = true) }
            platformRepo.getCloudBackups().onSuccess { list ->
                updateState { it.copy(backups = list, isFetchingBackups = false) }
            }.onFailure {
                updateState { it.copy(isFetchingBackups = false) }
                handleError(it)
            }
        }
    }

    fun deleteBackup(name: String) {
        launchJob("delete_backup") {
            platformRepo.deleteCloudBackup(name).onSuccess {
                loadBackups()
                showMessage("Backup deleted")
            }.onFailure { handleError(it) }
        }
    }

    fun exportLocalDatabase(useOnline: Boolean) {
        launchJob("export_db") {
            updateState { it.copy(isLoading = true) }
            platformRepo.exportLocalDatabase(useOnline).onSuccess {
                updateState { it.copy(isLoading = false) }
                sendEvent(UIEvent.OpenFile(it, "application/octet-stream", "DB Export"))
            }.onFailure {
                updateState { it.copy(isLoading = false) }
                handleError(it)
            }
        }
    }

    fun approveLoanRequest(loan: Loan) {
        val loanId = loan.id ?: return
        launchJob("approve_loan_$loanId") {
            updateState { it.copy(isProcessingLoanRequest = true) }
            loanRepo.approveLoan(loanId).onSuccess {
                loadData()
                showMessage("Loan approved")
            }.onFailure { handleError(it) }
            updateState { it.copy(isProcessingLoanRequest = false) }
        }
    }

    fun rejectLoanRequest(loan: Loan, reason: String) {
        val loanId = loan.id ?: return
        launchJob("reject_loan_$loanId") {
            updateState { it.copy(isProcessingLoanRequest = true) }
            loanRepo.rejectLoan(loanId, reason).onSuccess {
                loadData()
                showMessage("Loan rejected")
            }.onFailure { handleError(it) }
            updateState { it.copy(isProcessingLoanRequest = false) }
        }
    }

    fun disburseLoan(loan: Loan) {
        val loanId = loan.id ?: return
        val adminId = supabaseRepo.currentUserId ?: return
        launchJob("disburse_loan_$loanId") {
            updateState { it.copy(isProcessingLoanRequest = true) }
            processLoanDisbursementUseCase(loanId, adminId, PaymentMethod.BANK).onSuccess {
                loadData()
                showMessage("Loan disbursed")
            }.onFailure { handleActionFailure(it) }
            updateState { it.copy(isProcessingLoanRequest = false) }
        }
    }

    fun approveBurialClaim(claimId: String, reason: String) {
        processBurialClaimInternal(claimId, BeneficiaryClaimStatus.APPROVED, reason)
    }

    fun rejectBurialClaim(claimId: String, reason: String) {
        processBurialClaimInternal(claimId, BeneficiaryClaimStatus.REJECTED, reason)
    }

    fun payBurialClaim(claimId: String, reason: String) {
        processBurialClaimInternal(claimId, BeneficiaryClaimStatus.PAID, reason)
    }

    private fun processBurialClaimInternal(claimId: String, status: BeneficiaryClaimStatus, reason: String) {
        launchJob("process_claim_$claimId") {
            updateState { it.copy(isProcessingClaim = true) }
            processBurialClaimUseCase(
                claimId = claimId,
                status = status,
                reviewedBy = supabaseRepo.currentUserId ?: "",
                adminNotes = if (status != BeneficiaryClaimStatus.REJECTED) reason else null,
                rejectionReason = if (status == BeneficiaryClaimStatus.REJECTED) reason else null
            ).onSuccess {
                loadData()
                showMessage("Claim status updated to ${status.name}")
            }.onFailure { handleActionFailure(it) }
            updateState { it.copy(isProcessingClaim = false) }
        }
    }

}
