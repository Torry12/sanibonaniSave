package com.sanibonani.save.features.admin.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sanibonani.save.domain.model.AsyncResult
import com.sanibonani.save.features.admin.presentation.*
import com.sanibonani.save.features.admin.presentation.state.*
import com.sanibonani.save.features.admin.ui.tabs.*
import java.io.File

/**
 * Analytics page of the platform console: report content, spinner, and the retry state.
 */
@Composable
internal fun PlatformAdminAnalyticsPage(
    analyticsState: PlatformAnalyticsUiState,
    analyticsVm: PlatformAnalyticsViewModel
) {
    when (val analyticsRes = analyticsState.analytics) {
        is AsyncResult.Success -> PlatformAnalyticsTab(
            analytics = analyticsRes.data,
            totalRevenueShare = analyticsState.totalRevenueShare,
            provinceDistribution = analyticsState.provinceDistribution,
            marketSegments = analyticsState.marketSegments,
            vm = analyticsVm
        )
        is AsyncResult.Loading, is AsyncResult.Uninitialized -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is AsyncResult.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("⚠️", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Unable to load analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(analyticsRes.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { analyticsVm.load() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

/**
 * Payments page: the province grouping the disbursement tab renders is derived here because it
 * needs the group-name/province maps the analytics screen owns.
 */
@Composable
internal fun PlatformAdminDisbursementsPage(
    analyticsState: PlatformAnalyticsUiState,
    disbursementsState: PlatformDisbursementsUiState,
    supportState: PlatformSupportUiState,
    vm: PlatformDisbursementsViewModel,
    onOpenMemberPortalFromDisbursement: (groupId: String, payoutId: String?) -> Unit,
    onImpersonateMember: (memberId: String, groupId: String) -> Unit,
    onNavigateToHealthScore: (String) -> Unit,
    onNavigateToBehaviorTracking: (String) -> Unit
) {
    val groupNames = analyticsState.groupNames
    val groupProvinces = analyticsState.groupProvinces

    val payoutsByProvince = remember(disbursementsState.payouts, groupProvinces) {
        disbursementsState.payouts.groupBy { groupProvinces[it.groupId] ?: "Other" }.toList().sortedBy { it.first }
    }
    val loanEscalations = remember(disbursementsState.loanRequestsByGroup) {
        disbursementsState.loanRequestsByGroup.values.flatten()
    }
    val loansByProvince = remember(loanEscalations, groupProvinces) {
        loanEscalations.groupBy { groupProvinces[it.groupId] ?: "Other" }.toList().sortedBy { it.first }
    }
    val claimsByProvince = remember(disbursementsState.escalatedClaims, groupProvinces) {
        disbursementsState.escalatedClaims.groupBy { groupProvinces[it.groupId] ?: "Other" }.toList().sortedBy { it.first }
    }

    DisbursementsTab(
        state = DisbursementsTabState(
            payoutRequests = disbursementsState.payouts,
            loanEscalations = loanEscalations,
            claimEscalations = disbursementsState.escalatedClaims,
            behaviorInsights = supportState.memberBehaviorInsights,
            groupNames = groupNames,
            loanMemberNames = analyticsState.loanMemberNames,
            groupProvinces = groupProvinces,
            isProcessingPayout = disbursementsState.isProcessingPayout,
            isProcessingLoan = disbursementsState.isProcessingLoanRequest,
            isProcessingClaim = disbursementsState.isProcessingClaim,
            processingIds = disbursementsState.processingIds,
            selectedMemberSummary = disbursementsState.selectedMemberSummary,
            isLoadingMemberSummary = disbursementsState.isLoadingMemberSummary,
            payoutsByProvince = payoutsByProvince,
            loansByProvince = loansByProvince,
            claimsByProvince = claimsByProvince
        ),
        vm = vm,
        onOpenMemberPortalFromDisbursement = onOpenMemberPortalFromDisbursement,
        onImpersonateMember = onImpersonateMember,
        onNavigateToHealthScore = onNavigateToHealthScore,
        onNavigateToBehaviorTracking = onNavigateToBehaviorTracking
    )
}

/**
 * Opens a generated report through a chooser, falling back to a plain send when no app can
 * handle the view intent.
 */
internal fun openSharedPlatformFile(
    context: Context,
    file: File,
    mimeType: String,
    chooserTitle: String
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_ACTIVITY_NEW_TASK

    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(flags)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).apply { addFlags(flags) })
    } catch (_: Exception) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(flags)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle).addFlags(flags))
    }
}
