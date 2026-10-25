package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupSettings
import com.sanibonani.save.domain.model.BankConnection

data class AdminSettingsState(
    val group: Group? = null,
    val settings: GroupSettings = GroupSettings(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val bankConnection: BankConnection? = null,
    val isLinkingBank: Boolean = false,
    val isSyncing: Boolean = false,
    val isPlatformAdmin: Boolean = false
)
