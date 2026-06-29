package com.sanibonani.save.ui.screens.admin.tabs.platform

import androidx.compose.runtime.Composable
import com.sanibonani.save.viewmodel.PlatformAdminUiState
import com.sanibonani.save.viewmodel.PlatformAdminViewModel

/**
 * Extracted from PlatformAdminScreen for maintainability.
 * See PlatformAdminScreen.kt for full implementation details.
 */
@Composable
fun MaintenanceTab(
    state: PlatformAdminUiState,
    vm: PlatformAdminViewModel,
    onNavigateToCreateAdmin: () -> Unit,
    onNavigateToSandbox: () -> Unit,
    onLogout: () -> Unit,
    onImpersonateGroupAdmin: (groupId: String) -> Unit,
    onImpersonateMember: (memberId: String, groupId: String) -> Unit
) {
    // ...existing code from PlatformAdminScreen.kt...
}

