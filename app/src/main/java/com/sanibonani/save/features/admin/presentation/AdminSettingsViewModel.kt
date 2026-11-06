package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.group.UpdateGroupSettingsUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

import com.sanibonani.save.features.admin.presentation.state.AdminSettingsState
@HiltViewModel
class AdminSettingsViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val bankRepo: com.sanibonani.save.domain.repository.payment.BankRepository,
    private val linkBankAccount: com.sanibonani.save.domain.usecase.payment.LinkBankAccountUseCase,
    private val updateGroupSettings: UpdateGroupSettingsUseCase,
    private val syncRepo: com.sanibonani.save.domain.repository.system.SyncRepository,
    private val supabaseRepo: com.sanibonani.save.domain.repository.auth.SupabaseRepository
) : BaseViewModel<AdminSettingsState>(AdminSettingsState()) {

    val state: StateFlow<AdminSettingsState> = uiState

    /** True while the admin has typed into the form and those edits have not been saved. */
    private var settingsDirty = false

    init {
        checkUserRole()
        observeGroupContext(
            groupIdFlow = adminContextCacheService.selectedGroupId,
            contextSource = adminContextCacheService.contexts
        ) { context ->
            val group = context?.group
            val previousId = uiState.value.group?.id
            val nextId = group?.id
            if (nextId != previousId) settingsDirty = false
            updateState { s ->
                s.copy(
                    group = group,
                    settings = when {
                        group == null -> GroupSettings()
                        nextId != previousId || !settingsDirty -> toGroupSettings(group)
                        else -> s.settings
                    },
                    bankConnection = if (nextId != previousId) null else s.bankConnection,
                    saveSuccess = if (nextId != previousId) false else s.saveSuccess
                )
            }

            if (nextId != null) observeBankConnection(nextId)
        }
        observeBankCallbacks()
    }

    private fun checkUserRole() {
        launchJob("role_check") {
            val role = safeRunCatching { supabaseRepo.getUserRole() }.getOrDefault(UserRole.MEMBER)
            updateState { it.copy(isPlatformAdmin = role == UserRole.PLATFORM_ADMIN) }
        }
    }

    private fun observeBankCallbacks() {
        launchJob("bank_callback_observation") {
            com.sanibonani.save.domain.event.EventBus.events
                .filterIsInstance<com.sanibonani.save.domain.event.BankCallbackEvent>()
                .collect { event ->
                    // Just refresh the connection state, the actual finalization
                    // is handled by AdminFinancialViewModel if active, or we could handle it here.
                    // But to be safe, let's just refresh our local state.
                    uiState.value.group?.id?.let { observeBankConnection(it) }
                }
        }
    }

    private fun observeBankConnection(groupId: String) {
        launchJob("bank_connection_observation") {
            bankRepo.observeBankConnection(groupId).collect { res ->
                if (uiState.value.group?.id != groupId) return@collect
                res.onSuccess { connection ->
                    updateState { it.copy(bankConnection = connection) }
                }.onFailure { e ->
                    // A refused read is not evidence that the account is unlinked, so the last
                    // answer the server gave stays on screen while the failure is reported.
                    handleError(e, "Could not read the bank connection for this group.")
                }
            }
        }
    }

    fun initiateBankLinking(providerId: String) {
        val groupId = uiState.value.group?.id ?: return
        launchJob("bank_linking") {
            linkBankAccount(groupId, providerId)
                .onSuccess { result ->
                    sendEvent(UIEvent.OpenUrl(result.authUrl))
                }
                .onFailure { e ->
                    handleError(e)
                }
        }
    }

    fun unlinkBankAccount() {
        val groupId = uiState.value.group?.id ?: return
        launchJob("bank_unlinking") {
            updateState { it.copy(isSaving = true) }
            bankRepo.deleteBankConnection(groupId).onSuccess {
                if (uiState.value.group?.id != groupId) return@onSuccess
                updateState { it.copy(isSaving = false, bankConnection = null) }
                sendEvent(UIEvent.ShowMessage("Bank account unlinked."))
            }.onFailure { handleError(it) }
        }
    }

    fun triggerFullSync() {
        launchJob("manual_full_sync") {
            updateState { it.copy(isSyncing = true) }
            syncRepo.syncAllData()
                .onSuccess {
                    updateState { it.copy(isSyncing = false) }
                    sendEvent(UIEvent.ShowMessage("Full system synchronization complete."))
                }
                .onFailure { e ->
                    updateState { it.copy(isSyncing = false) }
                    handleError(e)
                }
        }
    }

    fun updateSetting(key: String, value: Any) {
        settingsDirty = true
        updateState { s ->
            val updated = when (key) {
                "joining_fee" -> s.settings.copy(joiningFee = value as String)
                "monthly_contribution" -> s.settings.copy(monthlyContribution = value as String)
                "late_fee" -> s.settings.copy(lateFee = value as String)
                "allow_partial_payment" -> s.settings.copy(allowPartialPayment = value as Boolean)
                "bank_name" -> s.settings.copy(bankName = value as String)
                "account_number" -> s.settings.copy(accountNumber = value as String)
                "branch_code" -> s.settings.copy(branchCode = value as String)
                "financial_provider_id" -> s.settings.copy(financialProviderId = value as? String)
                "burial_claim_amount" -> s.settings.copy(burialClaimAmount = value as String)
                "min_members_for_claims" -> s.settings.copy(minMembersForClaims = value as String)
                "min_reserve_percentage" -> s.settings.copy(minReservePercentage = value as String)
                "loan_interest_rate" -> s.settings.copy(loanInterestRate = value as String)
                "loan_max_amount" -> s.settings.copy(loanMaxAmount = value as String)
                "loan_max_months" -> s.settings.copy(loanMaxMonths = value as String)
                else -> s.settings
            }
            s.copy(settings = updated)
        }
    }

    fun saveSettings() {
        val groupId = uiState.value.group?.id ?: return
        launchJob("save_settings") {
            updateState { it.copy(isSaving = true, saveSuccess = false) }
            updateGroupSettings(groupId, uiState.value.settings).onSuccess {
                if (uiState.value.group?.id != groupId) return@onSuccess
                settingsDirty = false
                updateState { it.copy(isSaving = false, saveSuccess = true) }
                sendEvent(UIEvent.ShowMessage("Settings saved"))
                adminContextCacheService.warmUpGroup(groupId, force = true)
            }.onFailure { e ->
                updateState { it.copy(isSaving = false) }
                handleError(e)
            }
        }
    }

    private fun toGroupSettings(group: Group) = GroupSettings(
        joiningFee = group.joiningFee.toPlainString(),
        monthlyContribution = group.monthlyContribution.toPlainString(),
        lateFee = group.lateFee.toPlainString(),
        allowPartialPayment = group.allowPartialPayment,
        bankName = group.bankName ?: "",
        accountNumber = group.accountNumber ?: "",
        branchCode = group.branchCode ?: "",
        financialProviderId = group.financialProviderId,
        burialClaimAmount = group.burialClaimAmount.toPlainString(),
        minMembersForClaims = group.minMembersForClaims.toString(),
        minReservePercentage = group.minReservePercentage.toString(),
        loanInterestRate = group.loanInterestRate?.toString() ?: "5.0",
        loanMaxAmount = group.loanMaxAmount?.toPlainString() ?: "5000",
        loanMaxMonths = group.loanMaxMonths?.toString() ?: "6"
    )
}
