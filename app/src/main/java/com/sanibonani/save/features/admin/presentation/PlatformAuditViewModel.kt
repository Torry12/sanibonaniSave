package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.admin.presentation.state.PlatformAuditUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformAuditViewModel @Inject constructor(
    private val platformRepo: PlatformRepository
) : BaseViewModel<PlatformAuditUiState>(PlatformAuditUiState()) {

    val state: StateFlow<PlatformAuditUiState> = uiState

    fun load() {
        launchJob("platform_audit") {
            updateState { it.copy(platformAuditReport = AsyncResult.Loading, isLoadingAuditReport = true) }
            platformRepo.triggerPlatformAudit()
                .onSuccess {
                    platformRepo.getPlatformAuditReport()
                        .onSuccess { report ->
                            updateState { it.copy(platformAuditReport = AsyncResult.Success(report), isLoadingAuditReport = false) }
                        }
                        .onFailure { e ->
                            updateState { it.copy(platformAuditReport = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName), isLoadingAuditReport = false) }
                            handleError(e)
                        }
                }
                .onFailure { e ->
                    updateState { it.copy(platformAuditReport = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName), isLoadingAuditReport = false) }
                    handleError(e)
                }
        }
    }
}
