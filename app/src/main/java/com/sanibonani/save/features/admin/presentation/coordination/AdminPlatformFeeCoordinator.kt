package com.sanibonani.save.features.admin.presentation.coordination

import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.domain.usecase.admin.GetPlatformSettingsUseCase
import com.sanibonani.save.domain.usecase.payment.GetOutstandingPlatformFeesUseCase
import com.sanibonani.save.domain.usecase.payment.ProcessGroupPlatformFeePaymentUseCase
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.service.AdminGroupContextCacheService
import javax.inject.Inject

class AdminPlatformFeeCoordinator @Inject constructor(
    private val getPlatformSettings: GetPlatformSettingsUseCase,
    private val getOutstandingPlatformFees: GetOutstandingPlatformFeesUseCase,
    private val processPlatformFeePayment: ProcessGroupPlatformFeePaymentUseCase,
    private val feeService: PlatformFeeService,
    private val adminContextCacheService: AdminGroupContextCacheService
) {

    fun applyRegistrationFee(host: AdminActionHost) {
        host.updateAdminState { it.copy(platformRegistrationFee = feeService.getRegistrationFee()) }
    }

    fun loadBankDetails(host: AdminActionHost) {
        host.launchAdminSafeJob("platform_bank_loading", "Failed to load platform settings") {
            getPlatformSettings()
                .onSuccess { config -> host.updateAdminState { it.copy(platformBankDetails = config) } }
                // A blank config is what the Account tab shows when nothing was ever read, so a
                // refused read has to say so instead of looking like an unconfigured platform.
                .onFailure { host.reportError(it) }
        }
    }

    fun loadOutstanding(host: AdminActionHost, groupId: String) {
        host.launchAdminJob("platform_fees_loading") {
            // The Account tab sums this list and switching groups does not clear it, so a read that
            // fails has to drop the previous group's dues rather than bill the new group for them.
            host.updateAdminState { it.copy(outstandingPlatformFees = emptyList()) }
            getOutstandingPlatformFees(groupId)
                .onSuccess { fees ->
                    if (adminContextCacheService.selectedGroupId.value != groupId) return@onSuccess
                    host.updateAdminState { it.copy(outstandingPlatformFees = fees) }
                }
                .onFailure {
                    if (adminContextCacheService.selectedGroupId.value != groupId) return@onFailure
                    host.reportError(it)
                }
        }
    }

    fun pay(host: AdminActionHost) {
        val groupId = host.adminState.value.group?.id ?: return
        host.launchAdminSafeJob("pay_platform_fees", "Failed to pay platform fees") {
            host.updateAdminState { it.copy(isPayingPlatformFee = true) }
            processPlatformFeePayment(groupId)
                .onSuccess {
                    host.updateAdminState { it.copy(isPayingPlatformFee = false) }
                    host.sendUiEvent(UIEvent.ShowMessage("Platform fees paid successfully."))
                    loadOutstanding(host, groupId)
                    adminContextCacheService.warmUpGroup(groupId, force = true)
                }
                .onFailure { e ->
                    host.updateAdminState { it.copy(isPayingPlatformFee = false) }
                    host.reportError(e)
                }
        }
    }
}
