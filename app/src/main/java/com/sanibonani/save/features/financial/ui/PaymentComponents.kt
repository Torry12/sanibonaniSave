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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
fun DueBreakdownCard(
    breakdown: com.sanibonani.save.domain.utils.DueBreakdown,
    currentInput: Money = Money.ZERO
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (breakdown.total.isPositive) MaterialTheme.colorScheme.errorContainer else SuccessGreen.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, (if (breakdown.total.isPositive) MaterialTheme.colorScheme.error else SuccessGreen).copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (breakdown.total.isPositive) "Outstanding Balance Breakdown" else "Account Balanced",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (breakdown.total.isPositive) MaterialTheme.colorScheme.onErrorContainer else SuccessGreen
            )

            var remainingInput = currentInput

            val items = listOf(
                "Platform Fees" to breakdown.platformFees,
                "Shortfall Recovery Fee" to breakdown.recoveryFee,
                "Late Fees" to breakdown.lateFees,
                "Loan Installments" to breakdown.loans,
                "Monthly Contributions" to breakdown.contributions
            ).filter { it.second.isPositive }

            items.forEach { (label, amt) ->
                val allocated = if (remainingInput.isPositive) remainingInput.min(amt) else Money.ZERO
                remainingInput -= allocated

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodySmall)
                        if (allocated.isPositive) {
                            Text("Allocated: ${allocated.format()}", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(amt.format(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 1.dp,
                color = (if (breakdown.total.isPositive) MaterialTheme.colorScheme.onErrorContainer else SuccessGreen).copy(alpha = 0.1f)
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Outstanding", fontWeight = FontWeight.ExtraBold)
                Text(breakdown.total.format(), fontWeight = FontWeight.ExtraBold, color = if (breakdown.total.isPositive) MaterialTheme.colorScheme.error else SuccessGreen)
            }
        }
    }
}

@Composable
fun PaymentGatewayForm(
    amount: Money,
    description: String,
    selectedMethod: PaymentMethod,
    onMethodChanged: (PaymentMethod) -> Unit,
    onPay: (String, String, String, Money) -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean = false,
    onAmountChanged: (Money) -> Unit = {},
    realtimeShortfall: Money = Money.ZERO,
    realtimeOverpayment: Money = Money.ZERO,
    nextDueDate: String = "",
    allowPartialPayments: Boolean = true,
    minDueAmount: Money = Money.ZERO,
    destination: PaymentDestination = PaymentDestination.GROUP,
    platformConfig: PlatformConfig? = null,
    group: Group? = null
) {
    var paymentAmount by remember { mutableStateOf(amount) }
    var cardNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var cvv        by remember { mutableStateOf("") }

    val isContribution = description.contains("Contribution")

    LaunchedEffect(amount) {
        if (paymentAmount.isZero) {
            paymentAmount = amount
            onAmountChanged(amount)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.ui_amount_to_pay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (isContribution) {
                        var amountText by remember { mutableStateOf(if (paymentAmount.isZero) "" else paymentAmount.toPlainString()) }

                        BasicTextField(
                            value = amountText,
                            onValueChange = {
                                if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                                    amountText = it
                                    val parsed = it.parseMoneyAmountOrNull()
                                    if (parsed != null) {
                                        val newMoney = Money(parsed)
                                        paymentAmount = newMoney
                                        onAmountChanged(newMoney)
                                    } else if (it.isEmpty()) {
                                        paymentAmount = Money.ZERO
                                        onAmountChanged(Money.ZERO)
                                    }
                                }
                            },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    } else {
                        Text(paymentAmount.format(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Icon(
                    imageVector = when(selectedMethod) {
                        PaymentMethod.STITCH -> Icons.Default.AccountBalance
                        PaymentMethod.PAYFAST -> Icons.Default.Language
                        else -> Icons.Default.CreditCard
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (isContribution && !allowPartialPayments && minDueAmount.isPositive) {
                InfoBox(stringResource(R.string.ui_partial_payments_disabled, minDueAmount.format()), type = InfoType.WARNING)
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Text(stringResource(R.string.ui_payment_method), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(PaymentMethod.BANK, PaymentMethod.YOCO, PaymentMethod.STITCH, PaymentMethod.PAYFAST).forEach { method ->
                    FilterChip(
                        selected = selectedMethod == method,
                        onClick = { onMethodChanged(method) },
                        label = {
                            val label = when(method) {
                                PaymentMethod.YOCO -> "Card Payment"
                                PaymentMethod.BANK -> "Bank Transfer"
                                else -> method.name.lowercase().replaceFirstChar { it.titlecase(Locale.ROOT) }
                            }
                            Text(label)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            if (isContribution) {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_resulting_shortfall_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(realtimeShortfall.format(), color = if (realtimeShortfall.isPositive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.ui_resulting_credit_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(realtimeOverpayment.format(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    if (nextDueDate.isNotEmpty()) {
                        Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.ui_new_due_date_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(nextDueDate, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (selectedMethod == PaymentMethod.YOCO) {
                SanibonaniTextField(
                    value = cardNumber,
                    onValueChange = {
                        val filtered = it.filter { char -> char.isDigit() }
                        if(filtered.length <= 16) cardNumber = filtered
                    },
                    label = stringResource(R.string.ui_card_number),
                    placeholder = stringResource(R.string.ui_0000_0000_0000_0000),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CardNumberTransformation()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SanibonaniTextField(
                        value = expiryDate,
                        onValueChange = {
                            val filtered = it.filter { char -> char.isDigit() }
                            if(filtered.length <= 4) expiryDate = filtered
                        },
                        label = stringResource(R.string.ui_expiry_mmyy),
                        placeholder = stringResource(R.string.ui_mmyy),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ExpiryDateTransformation()
                    )
                    SanibonaniTextField(
                        value = cvv,
                        onValueChange = {
                            val filtered = it.filter { char -> char.isDigit() }
                            if(filtered.length <= 3) cvv = filtered
                        },
                        label = stringResource(R.string.ui_cvv),
                        placeholder = "123",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            } else if (selectedMethod == PaymentMethod.BANK) {
                val details = if (destination == PaymentDestination.PLATFORM && platformConfig != null) {
                    "Bank: ${platformConfig.bankName}\nAccount: ${platformConfig.accountNumber}\nBranch: ${platformConfig.branchCode}\nHolder: ${platformConfig.accountHolder}"
                } else if (group != null && !group.accountNumber.isNullOrBlank()) {
                    "Bank: ${group.bankName ?: "N/A"}\nAccount: ${group.accountNumber}\nBranch: ${group.branchCode ?: "N/A"}"
                } else {
                    "Please contact your group administrator for bank details."
                }

                InfoBox(
                    message = "Please make a manual bank transfer of ${paymentAmount.format()} to:\n\n$details\n\nUse your unique member key as reference. Once done, click the button below to notify the admin.",
                    type = InfoType.INFO
                )
            } else if (selectedMethod == PaymentMethod.CASH) {
                InfoBox(
                    message = "Hand ${paymentAmount.format()} cash to your group treasurer. Once done, click the button below to record the transaction.",
                    type = InfoType.WARNING
                )
            } else {
                val gatewayName = selectedMethod.name.lowercase().replaceFirstChar { it.titlecase(Locale.ROOT) }
                InfoBox(
                    message = stringResource(R.string.ui_proceed_to_gateway, gatewayName),
                    type = InfoType.INFO
                )
            }

            Spacer(Modifier.height(8.dp))

            SanibonaniButton(
                text = when (selectedMethod) {
                    PaymentMethod.YOCO -> stringResource(R.string.ui_pay_amount, paymentAmount.format())
                    PaymentMethod.BANK -> stringResource(R.string.ui_confirm_bank_transfer)
                    PaymentMethod.CASH -> stringResource(R.string.ui_record_cash_payment)
                    else -> stringResource(R.string.ui_proceed_to_gateway, selectedMethod.name.lowercase().replaceFirstChar { it.titlecase(Locale.ROOT) })
                },
                onClick = { onPay(cardNumber, expiryDate, cvv, paymentAmount) },
                modifier = Modifier.fillMaxWidth(),
                isLoading = isLoading,
                enabled = (selectedMethod != PaymentMethod.YOCO || (cardNumber.length >= 13 && expiryDate.length >= 4 && cvv.length >= 3)) &&
                        paymentAmount.isPositive && (allowPartialPayments || paymentAmount >= minDueAmount - Money("0.01"))
            )

            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.ui_cancel_payment), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}
