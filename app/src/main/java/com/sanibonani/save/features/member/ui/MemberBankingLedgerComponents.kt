package com.sanibonani.save.features.member.ui


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.member.ValidateBurialClaimEligibilityUseCase
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.formatZAR
import com.sanibonani.save.features.member.presentation.MemberBeneficiaryViewModel
import com.sanibonani.save.ui.utils.IDNumberTransformation
import java.math.BigDecimal
import kotlinx.coroutines.launch
@Composable
fun BankingDetailsDialog(
    initialBank: String = "",
    initialAccount: String = "",
    initialBranch: String = "",
    onDismiss: () -> Unit,
    onConfirm: (bank: String, account: String, branch: String) -> Unit
) {
    var bank by remember { mutableStateOf(initialBank) }
    var account by remember { mutableStateOf(initialAccount) }
    var branch by remember { mutableStateOf(initialBranch) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Banking Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Enter the account where you want to receive payouts.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                BankingDetailsFields(
                    bankName = bank,
                    accountNo = account,
                    branchCode = branch,
                    onBankNameChange = { bank = it },
                    onAccountNoChange = { account = it },
                    onBranchCodeChange = { branch = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(bank, account, branch) },
                enabled = bank.isNotBlank() && account.isNotBlank() && branch.isNotBlank()
            ) {
                Text("Save Details")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        }
    )
}

@Composable
fun WithdrawalRequestDialog(
    group: Group?,
    type: PayoutType,
    member: Member? = null,
    onDismiss: () -> Unit,
    onConfirm: (Money) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val title = if (type == PayoutType.EMERGENCY) "Request Emergency Funds" else "Request Disinvestment"
    val description = if (type == PayoutType.EMERGENCY)
        "Emergency withdrawals are subject to a 50% liquidity cap and admin approval."
    else
        "Disinvesting your equity may take up to 7 days to process based on group rules."

    val hasBanking = member?.accountNo?.isNotBlank() == true && member.bankName?.isNotBlank() == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hasBanking) {
                    InfoBox("You must configure your banking details in the Profile tab before requesting a withdrawal.", type = InfoType.ERROR)
                }

                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                SanibonaniTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) amount = it },
                    label = "Amount (R)",
                    enabled = hasBanking,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (type == PayoutType.EMERGENCY && group != null) {
                    InfoBox("Current Group Liquidity: ${formatZAR(group.balance)}", type = InfoType.INFO)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                    if (a > BigDecimal.ZERO) onConfirm(Money(a))
                },
                enabled = hasBanking && amount.isNotBlank() && (amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO
            ) {
                Text("Submit Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        }
    )
}

@Composable
fun AddBeneficiaryDialog(beneficiary: Beneficiary?, onDismiss: () -> Unit, onConfirm: (String, String, String, String, Boolean) -> Unit) {
    var n by remember { mutableStateOf(beneficiary?.fullName ?: "") }
    var id by remember { mutableStateOf(beneficiary?.idNumber ?: "") }
    var r by remember { mutableStateOf(beneficiary?.relationship ?: "") }
    var d by remember { mutableStateOf(beneficiary?.dateOfBirth ?: "") }
    var i by remember { mutableStateOf(beneficiary?.isOver65 ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (beneficiary == null) "Add Beneficiary" else "Update Beneficiary") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SanibonaniTextField(n, { n = it }, "Full Name")
                SanibonaniTextField(id, { id = it }, "ID Number", visualTransformation = IDNumberTransformation())
                SanibonaniTextField(r, { r = it }, "Relationship")
                SanibonaniDatePickerField(
                    label = stringResource(R.string.ui_date_of_birth),
                    value = d,
                    onValueChange = { d = it }
                )
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(i, { i = it }); Text(stringResource(R.string.ui_over_65_years_old)) }
            }
        },
        confirmButton = { SanibonaniButton(text = if (beneficiary == null) "Add" else "Update", onClick = { if (n.isNotBlank()) onConfirm(n, id, r, d, i) }, modifier = Modifier.fillMaxWidth()) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } }
    )
}

@Composable
fun LedgerItem(entry: LedgerEntry, onClick: () -> Unit) {
    val isInflow = entry.amount.isPositive
    val color = if (isInflow) SuccessGreen else ErrorRed

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LightGray.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(4.dp, 32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.description,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.createdAt?.take(16)?.replace("T", " ")} Ã¢â‚¬Â¢ ${entry.category.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isInflow) "+" else "") + formatZAR(entry.amount),
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    text = "Bal: " + formatZAR(entry.balanceAfter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun LedgerDetailDialog(
    entry: LedgerEntry,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaction Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoBox(
                    message = "This transaction is immutable and recorded on the permanent group ledger.",
                    type = InfoType.INFO
                )

                DetailRow("Description", entry.description)
                DetailRow("Category", entry.category.uppercase())
                DetailRow("Amount", formatZAR(entry.amount))
                DetailRow("Running Balance", formatZAR(entry.balanceAfter ?: Money.ZERO))
                DetailRow("Date", entry.createdAt?.replace("T", " ") ?: "N/A")

                HorizontalDivider(color = LightGray.copy(alpha = 0.3f))

                Column {
                    Text("Digital Audit Reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(
                        entry.transactionId ?: entry.id ?: "SYSTEM_VERIFIED",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) }
        }
    )
}
