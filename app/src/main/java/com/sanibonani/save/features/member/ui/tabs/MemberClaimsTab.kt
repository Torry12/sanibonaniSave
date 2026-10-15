package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.SuccessGreen
import com.sanibonani.save.ui.theme.ErrorRed
import com.sanibonani.save.ui.utils.formatDate
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.member.presentation.MemberBeneficiaryViewModel
import com.sanibonani.save.features.member.presentation.MemberFinanceViewModel
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.theme.WarningAmber

@Composable
fun MemberClaimsTab(
    group: Group?,
    member: Member?,
    claims: List<BeneficiaryPayoutClaim>,
    payouts: List<PayoutRequest>,
    insights: Result<GroupFinancialInsight>?,
    onRefresh: () -> Unit,
    beneficiaryVm: MemberBeneficiaryViewModel,
    financeVm: MemberFinanceViewModel
) {
    val groupType = group?.type ?: GroupType.BURIAL_SOCIETY

    val title = when (groupType) {
        GroupType.BURIAL_SOCIETY -> "Burial Payout Claims"
        GroupType.STOKVEL, GroupType.ROSCA -> "Group Payouts"
        GroupType.INVESTMENT_CLUB, GroupType.EMERGENCY_FUND -> "Withdrawal Requests"
        else -> "Payout Claims"
    }

    val subtitle = when (groupType) {
        GroupType.BURIAL_SOCIETY -> "Track the progress of burial claims for your beneficiaries."
        GroupType.STOKVEL -> "View your scheduled and completed annual savings payouts."
        GroupType.ROSCA -> "Track your rotating pot payouts and assigned rotation month."
        GroupType.INVESTMENT_CLUB -> "Manage your investment disinvestment and capital withdrawal requests."
        GroupType.EMERGENCY_FUND -> "Request and track emergency fund disbursements."
        else -> "Track your financial disbursements and payout requests."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        Spacer(Modifier.height(16.dp))

        when (groupType) {
            GroupType.BURIAL_SOCIETY -> BurialClaimsContent(member, claims, onRefresh, beneficiaryVm, insights)
            GroupType.STOKVEL -> StokvelPayoutsContent(group, member, payouts, onRefresh, insights)
            GroupType.ROSCA -> RoscaPayoutsContent(group, member, payouts, onRefresh, insights)
            GroupType.INVESTMENT_CLUB, GroupType.EMERGENCY_FUND -> WithdrawalContent(groupType, group, member, payouts, onRefresh, financeVm, insights)
            else -> GenericClaimsContent(claims, payouts)
        }
    }
}
