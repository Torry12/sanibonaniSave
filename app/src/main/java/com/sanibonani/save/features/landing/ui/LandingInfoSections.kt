package com.sanibonani.save.features.landing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.config.PlatformFees
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.features.landing.ui.components.CompactStatItem
import com.sanibonani.save.features.landing.ui.components.HowItWorksItem
import com.sanibonani.save.features.landing.ui.components.StatsShimmer
import com.sanibonani.save.ui.components.AsyncResultHandler
import com.sanibonani.save.ui.components.SanibonaniButton
import com.sanibonani.save.ui.components.SanibonaniSectionHeader
import com.sanibonani.save.ui.theme.LightGray
import com.sanibonani.save.ui.utils.ClickDebouncer

@Composable
internal fun StatsSection(
    analytics: AsyncResult<PlatformAnalytics>,
    onRetry: () -> Unit
) {
    // ── Stats Section ─────────────────────────────────────────────────
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-24).dp)
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            AsyncResultHandler(
                result = analytics,
                onLoading = { StatsShimmer() },
                onError = { message, _ ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.ui_retry_connection_btn), fontWeight = FontWeight.Bold)
                    }
                }
            ) { stats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val groupsText = if (stats.totalGroups > 0) stats.totalGroups.toString() else stringResource(R.string.ui_new)
                    val membersText = if (stats.totalMembers > 0) stats.totalMembers.toString() else stringResource(R.string.ui_join_now)

                    CompactStatItem(stringResource(R.string.ui_groups_active), groupsText)
                    Box(Modifier.width(1.dp).height(32.dp).background(LightGray.copy(alpha = 0.5f)))
                    CompactStatItem(stringResource(R.string.ui_trusted_members), membersText)
                }
            }
        }
    }
}

@Composable
internal fun ComplianceSection() {
    // ── Compliance & Security ──────────────────────────────────────────
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.ui_bankgrade_security),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.ui_your_data_and_group_finances_are_protect),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun CallToActionSection(
    clickDebouncer: ClickDebouncer,
    onNavigateRegisterGroup: () -> Unit
) {
    // ── Call to Action Section ──────────────────────────────────────
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.ui_start_your_legacy),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ui_start_legacy_desc, PlatformFees.REGISTRATION.format()),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            SanibonaniButton(
                text = stringResource(R.string.ui_register_group_now),
                onClick = { clickDebouncer.processClick(onNavigateRegisterGroup) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
    }
}

@Composable
internal fun HowItWorksSection() {
    // ── How It Works ──────────────────────────────────────────────────
    Column(Modifier.padding(24.dp)) {
        SanibonaniSectionHeader(stringResource(R.string.ui_how_it_works))
        Spacer(Modifier.height(16.dp))

        HowItWorksItem(
            step = "1",
            title = stringResource(R.string.ui_step_create_group),
            description = stringResource(R.string.ui_step_create_group_desc)
        )
        HowItWorksItem(
            step = "2",
            title = stringResource(R.string.ui_step_invite_members),
            description = stringResource(R.string.ui_step_invite_members_desc)
        )
        HowItWorksItem(
            step = "3",
            title = stringResource(R.string.ui_step_save_grow),
            description = stringResource(R.string.ui_step_save_grow_desc)
        )
    }
}
