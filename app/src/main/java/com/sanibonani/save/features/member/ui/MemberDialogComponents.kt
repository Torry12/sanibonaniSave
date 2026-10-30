package com.sanibonani.save.features.member.ui


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import java.math.BigDecimal
@Composable
fun LoanRequestDialog(group: Group?, onDismiss: () -> Unit, onConfirm: (java.math.BigDecimal, Int, String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("3") }
    var p by remember { mutableStateOf("") }

    val cap = group?.loanMaxAmount?.takeIf { it.isPositive }

    val amountError = remember(a, cap) {
        val parsed = a.toBigDecimalOrNull()
        when {
            a.isEmpty() -> null
            parsed == null -> "Invalid amount"
            parsed <= java.math.BigDecimal.ZERO -> "Must be greater than 0"
            cap != null && Money(parsed) > cap -> "Maximum per loan is ${cap.format()}"
            else -> null
        }
    }

    val monthsError = remember(m) {
        val months = m.toIntOrNull()
        when {
            m.isEmpty() -> null
            months == null -> "Invalid number"
            months < 1 -> "Minimum 1 month"
            months > 3 -> "Maximum 3 months"
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_request_loan), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SanibonaniTextField(
                    value = a,
                    onValueChange = { a = it },
                    label = "Amount (R) *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountError != null,
                    supportingText = amountError
                )
                SanibonaniTextField(
                    value = m,
                    onValueChange = { m = it },
                    label = "Months to Repay *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = monthsError != null,
                    supportingText = monthsError
                )
                SanibonaniTextField(
                    value = p,
                    onValueChange = { p = it },
                    label = "Purpose of Loan",
                    placeholder = "e.g. Medical emergency"
                )
                InfoBox(
                    "Interest Rate: ${group?.loanInterestRate ?: 0}% • Max term: 3 months" +
                        (cap?.let { " • Max per loan: ${it.format()}" } ?: ""),
                    type = InfoType.INFO
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(a.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO, m.toIntOrNull() ?: 3, p) },
                enabled = a.isNotBlank() && m.isNotBlank() && amountError == null && monthsError == null
            ) { Text(stringResource(R.string.ui_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        }
    )
}

@Composable
fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LoanSummaryCard(loans: List<Loan>) {
    val activeLoans = loans.filter { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID || it.status == LoanStatus.OVERDUE }
    if (activeLoans.isEmpty()) return

    val totalRemaining = activeLoans.fold(Money.ZERO) { acc, l -> acc + l.balanceRemaining }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("RUNNING LOANS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
                Text("${activeLoans.size} Active Loan${if (activeLoans.size > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text("REMAINING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                AutoFitText(
                    text = formatZAR(totalRemaining),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    minFontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun PayoutSummaryItem(claim: BeneficiaryPayoutClaim? = null, payout: PayoutRequest? = null) {
    val name = claim?.beneficiaryName ?: payout?.bankName ?: "Pending Payout"
    val amount = claim?.claimAmount ?: payout?.amount ?: Money.ZERO
    val status = claim?.status?.displayName ?: payout?.status?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Pending"

    val isBurial = claim != null

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (isBurial) ErrorRed.copy(0.1f) else SuccessGreen.copy(0.1f),
            shape = CircleShape,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (isBurial) "🕊️" else "💰", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, style = MaterialTheme.typography.labelSmall, color = if (status.contains("Approved", true) || status.contains("Paid", true)) SuccessGreen else WarningAmber)
        }
        Text(formatZAR(amount), fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
    }
}

