package com.sanibonani.save.features.financial.ui

import androidx.compose.ui.res.stringResource
import com.sanibonani.save.R

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.domain.config.PlatformFees
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.parseMoneyAmountOrNull
import com.sanibonani.save.ui.components.InfoType
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.features.financial.presentation.PaymentViewModel
import com.sanibonani.save.ui.utils.CardNumberTransformation
import com.sanibonani.save.ui.utils.ExpiryDateTransformation
import com.sanibonani.save.ui.utils.rememberClickDebouncer
import java.util.Locale

@Composable
fun PaymentScreen(
    paymentType       : String,
    amount            : Money,
    groupId           : String,
    onPaymentComplete : () -> Unit,
    onPaymentFailed   : (Throwable) -> Unit,
    onBack            : () -> Unit,
    vm                : PaymentViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clickDebouncer = rememberClickDebouncer()

    val successMsg = stringResource(R.string.ui_payment_successful)

    DisposableEffect(Unit) {
        vm.setActive(true)
        onDispose {
            vm.setActive(false)
        }
    }

    LaunchedEffect(groupId, paymentType) {
        // Load member context for joining_fee and contribution payment types
        vm.loadPaymentContext(groupId, paymentType)
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            ToastUtils.showSuccess(context, successMsg)
            onPaymentComplete()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            ToastUtils.showError(context, it)
            onPaymentFailed(Exception(it))
        }
    }

    // Launch external checkout URL if present
    LaunchedEffect(state.checkoutUrl) {
        state.checkoutUrl?.let { url ->
            uriHandler.openUri(url)
            // In a production app, we would also navigate to a "Waiting for Payment" screen
        }
    }

    if (state.isProcessing && state.member == null) {
        SanibonaniLoadingScreen(message = "Preparing secure checkout...")
    } else {
        Scaffold(
            topBar = {
                SanibonaniTopBar(
                    title  = when (paymentType) {
                        "registration" -> stringResource(R.string.ui_registration_fee)
                        "admin_fee"    -> stringResource(R.string.ui_platform_admin_fee)
                        "joining_fee"  -> stringResource(R.string.ui_joining_fee_title)
                        else           -> stringResource(R.string.ui_monthly_contribution_title)
                    },
                    onBack = { clickDebouncer.processClick(onBack) }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Payment context info
                when (paymentType) {
                    "registration" -> InfoBox(
                        "This ${PlatformFees.REGISTRATION.format()} once-off fee activates your group on SanibonaniSave. " +
                        "Your group will be publicly listed and operational immediately after payment.", type = InfoType.INFO
                    )
                    "admin_fee" -> {
                        val memberCount = if (PlatformFees.MONTHLY_PER_MEMBER.isPositive) (amount.ratio(PlatformFees.MONTHLY_PER_MEMBER)).toInt() else 0
                        InfoBox(
                            "Monthly platform fee: ${PlatformFees.MONTHLY_PER_MEMBER.format()} Ã— $memberCount members = ${amount.format()}. " +
                            "Due on the 1st of each month. Unpaid after 7 days suspends the group.", type = InfoType.WARNING
                        )
                    }
                    "joining_fee" -> {
                        InfoBox(
                            "This is your one-time joining fee to become a member of this savings group. " +
                            "Once paid, you'll be registered as an active member and can start contributing.", type = InfoType.INFO
                        )
                    }
                    "contribution" -> {
                        InfoBox(
                            "Monthly contribution to your savings group. " +
                            "Funds are settled directly into the group's bank account via our secure gateways.", type = InfoType.INFO
                        )

                        state.calculation?.let { calc ->
                            val breakdown = calc.breakdown
                            if (breakdown.total.isPositive) {
                                DueBreakdownCard(breakdown, state.currentInputAmount)
                            } else if (calc.overpayment.isPositive) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                ) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(R.string.ui_payment_overview).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                        Text("Overpayment Credit: ${calc.overpayment.format()}", color = SuccessGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                                        Text("Next Due Date: ${state.nextDueDate.ifEmpty { calc.nextDueDate }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            } else {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                ) {
                                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(color = SuccessGreen.copy(alpha = 0.1f), shape = CircleShape, modifier = Modifier.size(32.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Text(stringResource(R.string.ui_all_caught_up), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }

                // Unified Payment Form
                PaymentGatewayForm(
                    amount      = if (state.currentInputAmount.isPositive) state.currentInputAmount else amount,
                    description = when (paymentType) {
                        "registration" -> "Group Registration â€” SanibonaniSave"
                        "admin_fee"    -> {
                            val memberCount = if (PlatformFees.MONTHLY_PER_MEMBER.isPositive) (amount.ratio(PlatformFees.MONTHLY_PER_MEMBER)).toInt() else 0
                            "Monthly Platform Fee â€” $memberCount members"
                        }
                        "joining_fee"  -> "Member Joining Fee"
                        else           -> "Monthly Contribution"
                    },
                    selectedMethod = state.selectedMethod,
                    onMethodChanged = vm::onMethodChanged,
                    onPay    = { card, expiry, cvv, finalAmount ->
                        vm.processPayment(paymentType, finalAmount, groupId, card, expiry, cvv)
                    },
                    onCancel  = { clickDebouncer.processClick(onBack) },
                    isLoading = state.isProcessing,
                    onAmountChanged = vm::onAmountChanged,
                    realtimeShortfall = state.realtimeShortfall,
                    realtimeOverpayment = state.realtimeOverpayment,
                    nextDueDate = state.nextDueDate,
                    allowPartialPayments = state.group?.allowPartialPayment ?: true,
                    minDueAmount = state.calculation?.totalDueNow ?: Money.ZERO,
                    destination = state.destination,
                    platformConfig = state.platformConfig,
                    group = state.group
                )

                state.error?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        InfoBox(it, type = InfoType.ERROR)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.loadPaymentContext(groupId, paymentType) }) {
                            Text(stringResource(R.string.ui_retry_connection))
                        }
                    }
                }

                // Security note
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Text("ðŸ”’", fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                        Column {
                            Text(stringResource(R.string.ui_secure_payment_gateway),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.ui_your_transaction_is_handled_via_pcidss_c) +
                                "SanibonaniSave never stores your card number, expiry, or CVV.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
