package com.sanibonani.save.features.admin.presentation.actions

import com.sanibonani.save.domain.event.BankCallbackEvent
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.usecase.payment.FinalizeBankConnectionUseCase
import com.sanibonani.save.domain.usecase.payment.LinkBankAccountUseCase
import com.sanibonani.save.domain.usecase.payment.SyncBankTransactionsUseCase
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.service.AdminGroupContextCacheService
import kotlinx.coroutines.flow.filterIsInstance
import javax.inject.Inject

/**
 * Bank integration lifecycle: provider linking, callback finalization,
 * transaction refresh and manual transaction hygiene.
 */
class AdminBankActions @Inject constructor(
    private val syncBankTransactionsUseCase: SyncBankTransactionsUseCase,
    private val linkBankAccountUseCase: LinkBankAccountUseCase,
    private val finalizeBankConnectionUseCase: FinalizeBankConnectionUseCase,
    private val bankRepo: BankRepository,
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
            syncBankTransactionsUseCase(groupId)
                .onSuccess {
                    host.updateActionState { it.copy(isRefreshingBank = false, bankRefreshSuccess = true) }
                    host.notifyUser("Bank data synchronized successfully")
                    adminContextCacheService.warmUpGroup(groupId)
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isRefreshingBank = false) }
                    host.reportError(e)
                }
        }
    }

    fun initiateBankLinking(host: AdminFinancialActionHost, providerId: String) {
        val groupId = host.actionState.group?.id ?: return
        host.launchAction("initiate_bank_linking") {
            host.updateActionState { it.copy(isLinkingBank = true, error = null) }
            linkBankAccountUseCase(groupId, providerId)
                .onSuccess { result ->
                    host.updateActionState { it.copy(isLinkingBank = false, bankLinkingUrl = result.authUrl) }
                    host.publishEvent(UIEvent.OpenUrl(result.authUrl))
                }
                .onFailure { e ->
                    host.updateActionState { it.copy(isLinkingBank = false) }
                    host.reportError(e)
                }
        }
    }

    fun unlinkBankAccount(host: AdminFinancialActionHost) {
        val groupId = host.actionState.group?.id ?: return
        host.launchAction("unlink_bank_account") {
            host.updateActionState { it.copy(isLoading = true) }
            bankRepo.deleteBankConnection(groupId).onSuccess {
                host.updateActionState { it.copy(isLoading = false, bankConnection = null) }
                host.notifyUser("Bank account unlinked.")
            }.onFailure { host.reportError(it) }
        }
    }

    fun handleBankCallback(host: AdminFinancialActionHost, connectionId: String, status: String) {
        val groupId = host.actionState.group?.id ?: return
        host.launchAction("handle_bank_callback") {
            host.updateActionState { it.copy(isLoading = true, error = null) }
            finalizeBankConnectionUseCase(groupId, connectionId, status)
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
            bankRepo.ignoreTransaction(bankTxId, reason)
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
