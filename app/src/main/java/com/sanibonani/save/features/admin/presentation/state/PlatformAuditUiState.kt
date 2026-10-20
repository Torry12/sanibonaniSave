package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*

data class PlatformAuditUiState(
    val platformAuditReport: AsyncResult<PlatformAuditReport> = AsyncResult.Uninitialized
)
