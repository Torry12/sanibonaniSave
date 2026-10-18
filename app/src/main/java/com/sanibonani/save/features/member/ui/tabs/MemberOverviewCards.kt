package com.sanibonani.save.features.member.ui.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.sanibonani.save.ui.components.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.features.member.ui.LedgerItem
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel

@Composable
fun AccountOverviewCard(
    member: Member?,
    group: Group?,
    calculation: PaymentCalculation?,
    isLoading: Boolean,
    onPay: (String, Money, String) -> Unit,
    profileImageVersion: Long,
    profileLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    vm: MemberOverviewViewModel,
    context: android.content.Context,
    memberMonthlyContribution: Money,
    activeLoans: List<Loan> = emptyList()
) {
    val groupId = group?.id ?: member?.groupId ?: ""
    val isActive = member?.status == MemberStatus.ACTIVE
    val feePaid = (member?.totalContributions ?: 0) > 0
    val joiningFeeDue = group?.joiningFee?.isPositive == true && !isActive && (member?.status == MemberStatus.PENDING_PAYMENT || !feePaid)

    // Monthly contribution directly from group settings / member override
    val monthlyContributionSetting = member?.monthlyContributionOverride ?: group?.monthlyContribution ?: memberMonthlyContribution

    // Accurate Outstanding Arrears (Includes overdue savings shortfall + late fees + platform fees + recovery fees)
    val breakdown = calculation?.breakdown
    val outstandingAmount = if (calculation != null && calculation.totalDueNow.isPositive) {
        val nonLoanDue = (calculation.totalDueNow - (breakdown?.loans ?: Money.ZERO)).max(Money.ZERO)
        if (nonLoanDue.isPositive) nonLoanDue else Money.ZERO
    } else if (calculation?.isOverdue == true) {
        (calculation.shortfall + (breakdown?.lateFees ?: Money.ZERO) + (breakdown?.platformFees ?: Money.ZERO) + (breakdown?.recoveryFee ?: Money.ZERO)).max(Money.ZERO)
    } else Money.ZERO

    val loanRepaymentAmount = breakdown?.loans ?: if (activeLoans.isNotEmpty()) {
        activeLoans.fold(Money.ZERO) { acc, l -> acc + l.monthlyRepayment }
    } else Money.ZERO

    val joiningFeeAmount = group?.joiningFee ?: Money.ZERO

    GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    val showActionRequired = joiningFeeDue || calculation?.isOverdue == true || outstandingAmount.isPositive
                    Text(
                        text = (if (showActionRequired) "Action Required" else "Account Overview").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (showActionRequired) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (joiningFeeDue) "Joining Fee Due" else "Monthly Contribution",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (isLoading) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val primaryAmount = if (joiningFeeDue) joiningFeeAmount else monthlyContributionSetting
                    AutoFitText(
                        text = primaryAmount.format(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )

                    if (calculation?.isOverdue == true) {
                        Surface(
                            color = ErrorRed.copy(0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                stringResource(R.string.ui_account_overdue),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = ErrorRed,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    calculation?.let {
                        if (it.overpayment.isPositive) {
                            Surface(
                                color = SuccessGreen.copy(0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    "Credit Balance: ${formatZAR(it.overpayment)}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                ProfilePhotoBox(member, profileImageVersion, profileLauncher, vm, context)
            }

            // â”€â”€ FINANCIAL BREAKDOWN & PAYMENT OPTIONS â”€â”€
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(12.dp))

            Text(
                "Payment Breakdown & Options",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Option 1: Monthly Contribution (from Group Settings)
                BreakdownOptionRow(
                    title = "Monthly Contribution",
                    subtitle = "From Group Settings",
                    amount = monthlyContributionSetting,
                    buttonLabel = "Pay Contribution",
                    onClick = { onPay("contribution", monthlyContributionSetting, groupId) },
                    enabled = group != null && monthlyContributionSetting.isPositive
                )

                // Option 2: Outstanding Arrears (if any)
                if (outstandingAmount.isPositive) {
                    BreakdownOptionRow(
                        title = "Outstanding Arrears",
                        subtitle = "Overdue & Late Fees",
                        amount = outstandingAmount,
                        buttonLabel = "Pay Arrears",
                        onClick = { onPay("contribution", outstandingAmount, groupId) },
                        isWarning = true,
                        enabled = group != null
                    )
                }

                // Option 3: Loan Repayment (if active loan)
                if (loanRepaymentAmount.isPositive) {
                    BreakdownOptionRow(
                        title = "Loan Repayment",
                        subtitle = "Active Loan Installment",
                        amount = loanRepaymentAmount,
                        buttonLabel = "Pay Loan",
                        onClick = { onPay("loan_repayment", loanRepaymentAmount, groupId) },
                        enabled = group != null
                    )
                }

                // Option 4: Joining Fee (if joining fee due)
                if (joiningFeeDue) {
                    BreakdownOptionRow(
                        title = "Joining Fee",
                        subtitle = "Registration Required",
                        amount = joiningFeeAmount,
                        buttonLabel = "Pay Joining Fee",
                        onClick = { onPay("joining_fee", joiningFeeAmount, groupId) },
                        isWarning = true,
                        enabled = group != null
                    )
                }

                // Combined Total Due Button (if multiple components exist)
                val totalDueCombined = if (joiningFeeDue) {
                    joiningFeeAmount + loanRepaymentAmount
                } else if (outstandingAmount.isPositive) {
                    outstandingAmount + loanRepaymentAmount
                } else {
                    monthlyContributionSetting + loanRepaymentAmount
                }

                val activeComponents = (if (joiningFeeDue) 1 else 0) +
                        (if (outstandingAmount.isPositive) 1 else 0) +
                        (if (loanRepaymentAmount.isPositive) 1 else 0)

                if (activeComponents > 1 && totalDueCombined.isPositive) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { onPay("contribution", totalDueCombined, groupId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) {
                        Text(
                            "Pay All Combined (${totalDueCombined.format()})",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownOptionRow(
    title: String,
    subtitle: String,
    amount: Money,
    buttonLabel: String,
    onClick: () -> Unit,
    isWarning: Boolean = false,
    enabled: Boolean = true
) {
    Surface(
        color = if (isWarning) ErrorRed.copy(alpha = 0.06f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isWarning) ErrorRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isWarning) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amount.format(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isWarning) ErrorRed else MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onClick,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isWarning) ErrorRed else AccentBlue
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = buttonLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ProfilePhotoBox(
    member: Member?,
    profileImageVersion: Long,
    profileLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    vm: MemberOverviewViewModel,
    context: android.content.Context
) {
    Box(
        modifier = Modifier.clickable { profileLauncher.launch("image/*") },
        contentAlignment = Alignment.BottomEnd
    ) {
        val url = member?.profilePhotoUrl
        if (!url.isNullOrBlank()) {
            val headers = vm.getDownloadParams()
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .apply {
                        headers.forEach { (k, v) -> addHeader(k, v) }
                    }
                    .memoryCacheKey("$url-$profileImageVersion")
                    .crossfade(true)
                    .build(),
                contentDescription = "Photo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.5.dp, Navy.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .border(2.5.dp, Navy.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Navy.copy(alpha = 0.3f)
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            modifier = Modifier.size(24.dp).offset(x = 4.dp, y = 4.dp),
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Edit,
                    null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

@Composable
fun PendingPayoutsCard(pendingClaims: List<BeneficiaryPayoutClaim>, pendingPayouts: List<PayoutRequest>, onNavigate: () -> Unit) {
    GlassCard(accentColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(16.dp)) {
            Text("Pending Payouts", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            pendingClaims.forEach { com.sanibonani.save.features.member.ui.PayoutSummaryItem(claim = it) }
            pendingPayouts.forEach { com.sanibonani.save.features.member.ui.PayoutSummaryItem(payout = it) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onNavigate() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("View Disbursement Hub", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Navy)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Navy, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun TrustBanner(onAuditClick: () -> Unit) {
    GlassCard(accentColor = SuccessGreen) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Trust & Transparency", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                Text("This group uses immutable ledgers for all transactions.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAuditClick) {
                Text("AUDIT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
        }
    }
}
