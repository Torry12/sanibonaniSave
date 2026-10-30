package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.admin.GeneratePlatformAuditReportUseCase
import com.sanibonani.save.domain.usecase.admin.ReadPlatformAuditReportUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.admin.presentation.state.PlatformAuditUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformAuditViewModel @Inject constructor(
    private val readPlatformAuditReport: ReadPlatformAuditReportUseCase,
    private val generatePlatformAuditReport: GeneratePlatformAuditReportUseCase
) : BaseViewModel<PlatformAuditUiState>(PlatformAuditUiState()) {

    val state: StateFlow<PlatformAuditUiState> = uiState

    /** Shows the newest snapshot. Never generates one: see [refresh]. */
    fun load() {
        launchJob("platform_audit") {
            updateState { it.copy(platformAuditReport = AsyncResult.Loading) }
            readPlatformAuditReport()
                .onSuccess { report ->
                    // A platform that has never been audited has no snapshot to show. Staying
                    // Uninitialized keeps the tab's "Run Platform Audit" prompt instead of an error.
                    updateState {
                        it.copy(
                            platformAuditReport = report?.let { AsyncResult.Success(it) }
                                ?: AsyncResult.Uninitialized
                        )
                    }
                }
                .onFailure { e ->
                    updateState { it.copy(platformAuditReport = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName)) }
                    handleError(e)
                }
        }
    }

    /** Regenerates the snapshot, which writes two rows server-side. */
    fun refresh() {
        launchJob("platform_audit") {
            updateState { it.copy(platformAuditReport = AsyncResult.Loading) }
            generatePlatformAuditReport()
                .onSuccess { report ->
                    updateState { it.copy(platformAuditReport = AsyncResult.Success(report)) }
                }
                .onFailure { e ->
                    updateState { it.copy(platformAuditReport = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName)) }
                    handleError(e)
                }
        }
    }
}
