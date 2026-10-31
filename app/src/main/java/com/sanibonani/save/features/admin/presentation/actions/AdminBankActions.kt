package com.sanibonani.save.features.admin.presentation.actions

import com.sanibonani.save.domain.event.BankCallbackEvent
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.usecase.payment.FinalizeBankConnectionUseCase
import com.sanibonani.save.domain.usecase.payment.IgnoreBankTransactionUseCase
import com.sanibonani.save.domain.usecase.payment.SyncBankTransactionsUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import kotlinx.coroutines.flow.filterIsInstance
import javax.inject.Inject

/**
 * Bank integration lifecycle for the group ledger screen: callback finalization,
 * transaction refresh and manual transaction hygiene.
 *
 * Provider linking lives in [com.sanibonani.save.features.admin.presentation.AdminSettingsViewModel],
 * which is the screen that lists providers.
 */
class AdminBankActions @Inject constructor(
    private val syncBankTransactions: SyncBankTransactionsUseCase,
    private val finalizeBankConnection: FinalizeBankConnectionUseCase,
    private val ignoreBankTransactionUseCase: IgnoreBankTransactionUseCase,
    private val adminContextCacheService: AdminGroupContextCacheService,
) {

    fun observeBankCallbacks(host: AdminFinancialActionHost) {
        host.launchAction("bank_callback_observation") {
            EventBus.events
                .filterIsInstance<BankCallbackEvent>()
                .collect { event ->
                    handleBankCallback(host, event.connectionId, event.status)
                }
        }
    }

    fun refreshBankData(host: AdminFinancialActionHost) {
        val group = host.actionState.group ?: return
        val groupId = group.id ?: return

        host.launchAction("refresh_bank_data") {
            host.updateActionState { it.copy(isRefreshingBank = true, error = null) }
            syncBankTransactions(groupId)
                .onSuccess {
                    host.updateActionState { it.copy(isRefreshingBank = false, bankRefreshSuccess = true) }
                    host.notifyUser("Bank data synchronized successfully")
                    adminContextCacheService.warmUpGroup(groupId, force = true)
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isRefreshingBank = false) }
                    host.reportError(e)
                }
        }
    }

    private fun handleBankCallback(host: AdminFinancialActionHost, connectionId: String, status: String) {
        val groupId = host.actionState.group?.id ?: return
        host.launchAction("handle_bank_callback") {
            host.updateActionState { it.copy(isLoading = true, error = null) }
            finalizeBankConnection(groupId, connectionId, status)
                .onSuccess {
                    host.updateActionState { it.copy(isLoading = false) }
                    host.notifyUser("Bank account linked successfully!")
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isLoading = false) }
                    host.reportError(e)
                }
        }
    }

    fun ignoreBankTransaction(host: AdminFinancialActionHost, bankTxId: String, reason: String) {
        host.launchAction("ignore_bank_transaction") {
            host.updateActionState { it.copy(isLoading = true, error = null) }
            ignoreBankTransactionUseCase(bankTxId, reason)
                .onSuccess {
                    host.updateActionState { it.copy(isLoading = false, successMessage = "Bank transaction ignored") }
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isLoading = false) }
                    host.reportError(e)
                }
        }
    }
}
