package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupSettings

data class AdminSettingsState(
    val group: Group? = null,
    val settings: GroupSettings = GroupSettings(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)
