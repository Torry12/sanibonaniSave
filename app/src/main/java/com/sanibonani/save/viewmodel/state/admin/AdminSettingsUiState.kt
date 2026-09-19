package com.sanibonani.save.viewmodel.state.admin

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupSettings

data class AdminSettingsUiState(
    val group: Group? = null,
    val settings: GroupSettings = GroupSettings(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)
