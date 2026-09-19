package com.sanibonani.save.ui.screens.member

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.screens.member.tabs.*
import com.sanibonani.save.ui.screens.audit.MemberAuditTab
import com.sanibonani.save.viewmodel.equity.MemberOverviewViewModel
import com.sanibonani.save.viewmodel.equity.MemberProfileViewModel
import com.sanibonani.save.viewmodel.credit.MemberFinanceViewModel
import com.sanibonani.save.viewmodel.disbursement.MemberBeneficiaryViewModel
import com.sanibonani.save.viewmodel.state.MemberUiState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.sanibonani.save.ui.components.DashboardTabSpec

@Composable
fun MemberTabContent(
    page: Int,
    state: MemberUiState,
    windowSizeClass: WindowSizeClass,
    onNavigatePayment: (String, Money, String) -> Unit,
    onNavigateAdmin: () -> Unit,
    overviewVm: MemberOverviewViewModel,
    financeVm: MemberFinanceViewModel,
    profileVm: MemberProfileViewModel,
    beneficiaryVm: MemberBeneficiaryViewModel,
    onEntryClick: (LedgerEntry) -> Unit,
    onFileAction: (String, String, Map<String, String>) -> Unit,
    onLogout: () -> Unit
) {
    when (page) {
        0 -> MemberOverviewTab(
            member = state.member,
            group = state.group,
            userRole = state.userRole,
            contributions = state.contributions,
            calculation = state.paymentCalculation,
            profileImageVersion = state.profileImageVersion,
            onPay = onNavigatePayment,
            onNavigateAdmin = onNavigateAdmin,
            vm = overviewVm,
            recentActivity = state.memberLedger,
            onEntryClick = onEntryClick,
            windowSizeClass = windowSizeClass
        )
        1 -> MemberTransactionsTab(
            ledger = state.memberLedger,
            isExporting = state.isExporting,
            onExportCsv = { overviewVm.downloadPdfStatement() },
            onDownloadPdf = { overviewVm.downloadPdfStatement() },
            onDownloadGroupPdf = { overviewVm.downloadGroupStatement() },
            onItemClick = onEntryClick,
            windowSizeClass = windowSizeClass
        )
        2 -> MemberInsightsTab(state)
        3 -> MemberFinanceTab(
            loans = state.loans,
            group = state.group,
            vm = financeVm,
            onFileAction = onFileAction
        )
        4 -> MemberAuditTab(
            report = state.memberAuditReport,
            isLoading = state.isLoadingAuditReport,
            onRefresh = { overviewVm.loadMemberAuditReport() }
        )
        5 -> MemberBeneficiaryTab(
            beneficiaries = state.beneficiaries,
            vm = beneficiaryVm
        )
        6 -> MemberClaimsTab(
            group = state.group,
            member = state.member,
            claims = state.burialClaims,
            payouts = state.payouts,
            isLoading = state.isSubmittingClaim,
            insights = state.businessInsight,
            onRefresh = { overviewVm.refreshData() },
            beneficiaryVm = beneficiaryVm,
            financeVm = financeVm
        )
        7 -> MemberDocumentsTab(
            member = state.member,
            group = state.group,
            vm = profileVm,
            overviewVm = overviewVm,
            onFileAction = onFileAction
        )
        8 -> MemberMessagesTab(
            messages = state.messages,
            vm = overviewVm
        )
        9 -> MemberNotificationsTab(
            notifications = state.notifications
        )
        10 -> MemberProfileTab(
            member = state.member,
            group = state.group,
            vm = profileVm,
            profileImageVersion = state.profileImageVersion,
            onLogout = onLogout
        )
    }
}

fun getMemberDashboardTabs(group: Group?): List<DashboardTabSpec> {
    return listOf(
        DashboardTabSpec(0, "Overview"),
        DashboardTabSpec(1, "Transactions"),
        DashboardTabSpec(2, "Insights"),
        DashboardTabSpec(3, "Loans"),
        DashboardTabSpec(4, "Audit"),
        DashboardTabSpec(5, "Beneficiaries"),
        DashboardTabSpec(
            6,
            when (group?.type) {
                GroupType.BURIAL_SOCIETY -> "Claims"
                GroupType.STOKVEL, GroupType.ROSCA -> "Payouts"
                GroupType.INVESTMENT_CLUB, GroupType.EMERGENCY_FUND -> "Withdrawals"
                else -> "Claims"
            }
        ),
        DashboardTabSpec(7, "Documents"),
        DashboardTabSpec(8, "Messages"),
        DashboardTabSpec(9, "Notifications"),
        DashboardTabSpec(10, "Profile")
    )
}
