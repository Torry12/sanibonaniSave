package com.sanibonani.save.features.member.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import com.sanibonani.save.ui.utils.formatPct
import com.sanibonani.save.ui.utils.formatZAR

import com.sanibonani.save.features.member.presentation.MemberFinanceViewModel
import com.sanibonani.save.features.member.presentation.MemberBeneficiaryViewModel
import com.sanibonani.save.features.member.presentation.MemberProfileViewModel
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import com.sanibonani.save.features.admin.ui.tabs.platform.LedgerEntryDetailDialog
import com.sanibonani.save.features.member.presentation.state.MemberEvent
import com.sanibonani.save.features.common.presentation.UIEvent
import kotlinx.coroutines.launch

@Composable
fun MemberStatCards(state: com.sanibonani.save.features.member.presentation.state.MemberUiState) {
    val member = state.member ?: return
    val group = state.group
    val calculation = state.paymentCalculation
    val insight = state.businessInsight?.getOrNull()

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Card 1: Primary Value ---
        item {
            when (group?.type) {
                GroupType.BURIAL_SOCIETY -> {
                    val activeCount = member.beneficiaries.count { it.documentStatus == DocumentStatus.VERIFIED }
                    StatCard(
                        title = "🛡️",
                        value = "$activeCount Active",
                        label = "Policy Coverage",
                        trend = if (activeCount > 0) "Covered" else "At Risk",
                        color = if (activeCount > 0) SuccessGreen else ErrorRed,
                        modifier = Modifier.width(160.dp)
                    )
                }
                GroupType.ROSCA -> {
                    StatCard(
                        title = "💰",
                        value = formatZAR(state.totalSavings),
                        label = "Total Contributed",
                        modifier = Modifier.width(160.dp)
                    )
                }
                else -> {
                    StatCard(
                        title = "💰",
                        value = formatZAR(state.totalSavings),
                        label = stringResource(R.string.ui_total_savings),
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }

        // --- Card 2: Obligations / Next Action ---
        item {
            val memberMonthlyAmount = state.monthlyContributionAmount
            val nextAmount = if (calculation?.totalDueNow?.isPositive == true) calculation.totalDueNow else memberMonthlyAmount
            StatCard(
                title = "📅",
                value = formatZAR(nextAmount),
                label = stringResource(R.string.ui_next_payment_label),
                trend = if (calculation?.isOverdue == true) stringResource(R.string.ui_overdue) else stringResource(R.string.ui_due_soon),
                color = if (calculation?.isOverdue == true) ErrorRed else WarningAmber,
                modifier = Modifier.width(160.dp)
            )
        }

        // --- Card 3: Performance / Goal ---
        item {
            when (group?.type) {
                GroupType.BURIAL_SOCIETY -> {
                    val claimCount = state.burialClaims.count { it.status == BeneficiaryClaimStatus.SUBMITTED || it.status == BeneficiaryClaimStatus.UNDER_REVIEW }
                    StatCard(
                        title = "📑",
                        value = "$claimCount Claims",
                        label = "In Progress",
                        trend = if (claimCount > 0) "Action Required" else "No Claims",
                        color = if (claimCount > 0) WarningAmber else SuccessGreen,
                        modifier = Modifier.width(160.dp)
                    )
                }
                GroupType.STOKVEL, GroupType.INVESTMENT_CLUB -> {
                    val projected = insight?.totalProjectedFund ?: Money.ZERO
                    StatCard(
                        title = "🎯",
                        value = if (projected.isPositive) formatZAR(projected / group.currentMembers.coerceAtLeast(1)) else "---",
                        label = "Projected Payout",
                        trend = "Year-end Est.",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(160.dp)
                    )
                }
                else -> {
                    val rate = calculation?.paymentRate ?: 100.0
                    StatCard(
                        title = "🎯",
                        value = formatPct(rate),
                        label = stringResource(R.string.ui_payment_rate),
                        trend = if (rate >= 90) stringResource(R.string.ui_healthy) else stringResource(R.string.ui_low),
                        color = if (rate >= 90) SuccessGreen else ErrorRed,
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }
    }
}
