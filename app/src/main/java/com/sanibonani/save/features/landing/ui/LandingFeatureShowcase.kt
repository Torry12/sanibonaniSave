package com.sanibonani.save.features.landing.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.features.landing.ui.components.FeatureCard
import com.sanibonani.save.features.landing.ui.components.FeatureDetail
import com.sanibonani.save.features.landing.ui.components.GroupTypeHelpItem
import com.sanibonani.save.ui.components.SanibonaniSectionHeader
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.ui.theme.NavyMid
import com.sanibonani.save.ui.utils.ClickDebouncer

@Composable
internal fun FeaturesSection(
    clickDebouncer: ClickDebouncer,
    onSelectFeature: (FeatureDetail) -> Unit,
    onNavigateBrowseGroups: () -> Unit
) {
    // ── Core Features ─────────────────────────────────────────────────
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        SanibonaniSectionHeader(stringResource(R.string.ui_designed_for_south_africa))
        Text(stringResource(R.string.ui_tailored_features_for_the_unique_needs_o),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        FeatureCard(
            title = stringResource(R.string.ui_burial_societies_title),
            description = stringResource(R.string.ui_burial_societies_desc),
            icon = Icons.Default.Security,
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = {
                onSelectFeature(
                    FeatureDetail(
                        title = R.string.ui_burial_societies_title,
                        description = R.string.ui_burial_societies_desc,
                        icon = Icons.Default.Security,
                        iconColor = Navy,
                        benefits = listOf(
                            R.string.ui_benefit_burial_1,
                            R.string.ui_benefit_burial_2,
                            R.string.ui_benefit_burial_3,
                            R.string.ui_benefit_burial_4
                        )
                    )
                )
            }
        )

        FeatureCard(
            title = stringResource(R.string.ui_smart_loan_system_title),
            description = stringResource(R.string.ui_smart_loan_system_desc),
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            iconColor = MaterialTheme.colorScheme.secondary,
            onClick = {
                onSelectFeature(
                    FeatureDetail(
                        title = R.string.ui_smart_loan_system_title,
                        description = R.string.ui_smart_loan_system_desc,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        iconColor = NavyMid,
                        benefits = listOf(
                            R.string.ui_benefit_loans_1,
                            R.string.ui_benefit_loans_2,
                            R.string.ui_benefit_loans_3,
                            R.string.ui_benefit_loans_4
                        )
                    )
                )
            }
        )

        FeatureCard(
            title = stringResource(R.string.ui_digital_stokvels_title),
            description = stringResource(R.string.ui_digital_stokvels_desc),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = {
                onSelectFeature(
                    FeatureDetail(
                        title = R.string.ui_digital_stokvels_title,
                        description = R.string.ui_digital_stokvels_desc,
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        iconColor = Navy,
                        benefits = listOf(
                            R.string.ui_benefit_stokvel_1,
                            R.string.ui_benefit_stokvel_2,
                            R.string.ui_benefit_stokvel_3,
                            R.string.ui_benefit_stokvel_4
                        )
                    )
                )
            }
        )

        FeatureCard(
            title = stringResource(R.string.ui_group_discovery_title),
            description = stringResource(R.string.ui_group_discovery_desc),
            icon = Icons.Default.Map,
            iconColor = MaterialTheme.colorScheme.secondary,
            onClick = { clickDebouncer.processClick(onNavigateBrowseGroups) }
        )
    }
}

@Composable
internal fun AboutAndHelpSection() {
    // ── About, Owner Creditation, Contact & Group Types Help ───────────
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ℹ️ About SanibonaniSave", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "SanibonaniSave is South Africa's premier digital savings platform, designed to digitize informal community finance with transparent ledgers, automated banking reconciliation, and actuarial risk modeling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
                Text("👑 App Owner & Creditation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Founded and Architected by Torry Msimango (Lead Platform Architect & Developer). Built with passion to empower African community wealth-building.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📝 Company Contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                // NOTE: no personal/admin email here. This card is on the public
                // landing screen, and exposing an administrator's personal address
                // (especially a platform-admin account) is a phishing and targeting
                // vector. Route support through the public channel only.
                Text("Email: support@sanibonani.save", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Phone: +27 (0) 11 555 0192", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Address: Sandton City Office Towers, 5th Street, Sandton, Johannesburg, 2196, South Africa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("❓ Help & Group Types Guide", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Understanding the different savings structures supported on SanibonaniSave:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                GroupTypeHelpItem("Burial Society", "Mutual aid societies providing death benefit payouts, actuarial solvency monitoring, and contribution tracking.")
                GroupTypeHelpItem("Stokvel", "Traditional rotating or cumulative savings schemes where members contribute fixed monthly amounts.")
                GroupTypeHelpItem("ROSCA", "Rotating Savings and Credit Association with automated turn-taking, random draws, and bidding auctions.")
                GroupTypeHelpItem("Investment Club", "Equity and stock-pooling groups utilizing unit-based Net Asset Value (NAV) and annualised CAGR returns.")
                GroupTypeHelpItem("Emergency Fund", "Immediate liquidity reserves with strict withdrawal caps and instant disbursal validation.")
                GroupTypeHelpItem("Community Savings", "Community co-op pools supporting micro-loans and collective grassroots welfare.")
                GroupTypeHelpItem("Tontine", "Longevity-linked mutual fund where surviving members share accumulated yields.")
                GroupTypeHelpItem("Other", "General purpose custom savings and financial groups.")
            }
        }
    }
}
