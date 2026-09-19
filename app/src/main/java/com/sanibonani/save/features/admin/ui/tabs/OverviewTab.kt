package com.sanibonani.save.features.admin.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.Cream
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.features.admin.presentation.state.AdminOverviewUiState
import com.sanibonani.save.features.admin.presentation.AdminOverviewViewModel
import com.sanibonani.save.ui.utils.formatZARShort
import com.sanibonani.save.features.admin.ui.components.AdminFeeBanner

@Composable
fun OverviewTab(
    state: AdminOverviewUiState,
    vm: AdminOverviewViewModel,
    windowSizeClass: WindowSizeClass,
    onPayClick: (Money) -> Unit,
    onNavigateToHealthScore: () -> Unit,
    clickDebouncer: com.sanibonani.save.ui.utils.ClickDebouncer
) {
    val group = state.group

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        group?.let { g ->
            if (state.feeStatus == AdminFeeState.PENDING_ACTIVATION) {
                AdminFeeBanner(g, state.feeStatus, state.daysOverdue) {
                    onPayClick(g.registrationFee)
                }
            } else {
                AdminFeeBanner(g, state.feeStatus, state.daysOverdue) {
                    vm.setTab(5) // Account Tab
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        SanibonaniSectionHeader("Group Snapshot")
        if (!state.isSystemOnline || state.isAirplaneMode) {
            OfflineBanner(
                message = "⚠️ Offline: Data may be outdated.",
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val gridColumns = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) 2 else 4

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = stringResource(R.string.ui_balance),
                        value = formatZARShort(group?.balance ?: Money.ZERO),
                        label = "Total Funds",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(R.string.ui_members),
                        value = "${state.members.size}",
                        label = "Enrolled",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                QuickActionGrid(
                    windowSizeClass = windowSizeClass,
                    actions = listOf(
                        QuickAction("Statement", Icons.Default.Download) { vm.downloadGroupStatement() },
                        QuickAction("Constitution", Icons.Default.Description) { vm.downloadGroupConstitution() },
                        QuickAction("Add Funds", Icons.Default.Add) { onPayClick(Money.ZERO) },
                        QuickAction("Broadcast", Icons.Default.Email) { vm.setTab(3) }
                    )
                )
            }
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
