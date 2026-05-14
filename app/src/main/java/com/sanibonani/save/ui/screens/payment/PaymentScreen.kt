package com.sanibonani.save.ui.screens.payment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanibonani.save.domain.model.PlatformFees
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.viewmodel.PaymentViewModel

@Composable
fun PaymentScreen(
    paymentType       : String,
    amount            : Double,
    groupId           : String,
    onPaymentComplete : () -> Unit,
    onBack            : () -> Unit,
    vm                : PaymentViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(groupId, paymentType) {
        // Load member context for joining_fee and contribution payment types
        if (paymentType == "contribution" || paymentType == "joining_fee") {
            vm.loadPaymentContext(groupId)
        }
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            ToastUtils.showSuccess(context, "Payment successful! Your transaction has been recorded.")
            onPaymentComplete()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            ToastUtils.showError(context, it)
        }
    }

    Scaffold(
        topBar = {
            SanibonaniTopBar(
                title  = when (paymentType) {
                    "registration" -> "Registration Fee"
                    "admin_fee"    -> "Platform Admin Fee"
                    "joining_fee"  -> "Joining Fee"
                    else           -> "Monthly Contribution"
                },
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(Cream).padding(padding)
                .padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Payment context info
            when (paymentType) {
                "registration" -> InfoBox(
                    "This ${formatZAR(PlatformFees.REGISTRATION)} once-off fee activates your group on SanibonaniSave. " +
                    "Your group will be publicly listed and operational immediately after payment.",
                    InfoType.INFO
                )
                "admin_fee" -> InfoBox(
                    "Monthly platform fee: ${formatZAR(PlatformFees.MONTHLY_PER_MEMBER)} × ${if (PlatformFees.MONTHLY_PER_MEMBER > 0.0) (amount / PlatformFees.MONTHLY_PER_MEMBER).toInt() else 0} members = ${formatZAR(amount)}. " +
                    "Due on the 1st of each month. Unpaid after 7 days suspends the group.",
                    InfoType.WARNING
                )
                "joining_fee" -> {
                    InfoBox(
                        "This is your one-time joining fee to become a member of this savings group. " +
                        "Once paid, you'll be registered as an active member and can start contributing.",
                        InfoType.INFO
                    )

                    // Show member status while loading
                    if (state.isProcessing && state.member == null) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = InfoBg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = InfoBlue
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Loading membership details...", color = InfoBlue, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                "contribution" -> {
                    InfoBox(
                        "Monthly contribution to your savings group. " +
                        "Funds are settled directly into the group's bank account via YoCo on T+1.",
                        InfoType.INFO
                    )
                    
                    state.calculation?.let { calc ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (calc.shortfall > 0) Color(0xFFFFF3E0) else Color(0xFFE8F5E9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Payment Overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (calc.shortfall > 0) {
                                    Text("Current Shortfall: ${formatZAR(calc.shortfall)}", color = Color(0xFFE65100))
                                } else if (calc.overpayment > 0) {
                                    Text("Overpayment Credit: ${formatZAR(calc.overpayment)}", color = Forest)
                                } else {
                                    Text("All caught up!", color = Forest)
                                }
                                
                                val displayNextDate = if (state.nextDueDate.isNotEmpty()) state.nextDueDate else calc.nextDueDate
                                Text("Next Due Date: $displayNextDate", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // YoCo form
            YoCoPaymentForm(
                amount      = amount,
                description = when (paymentType) {
                    "registration" -> "Group Registration — SanibonaniSave"
                    "admin_fee"    -> "Monthly Platform Fee — ${if (PlatformFees.MONTHLY_PER_MEMBER > 0.0) (amount / PlatformFees.MONTHLY_PER_MEMBER).toInt() else 0} members"
                    "joining_fee"  -> "Member Joining Fee"
                    else           -> "Monthly Contribution"
                },
                onPay    = { card, expiry, cvv, finalAmount ->
                    vm.processPayment(paymentType, finalAmount, groupId, card, expiry, cvv)
                },
                onCancel  = onBack,
                isLoading = state.isProcessing,
                onAmountChanged = { newAmount ->
                    vm.onAmountChanged(newAmount)
                },
                realtimeShortfall = state.realtimeShortfall,
                realtimeOverpayment = state.realtimeOverpayment,
                nextDueDate = state.nextDueDate,
                allowPartialPayments = state.group?.allowPartialPayment ?: true,
                minDueAmount = state.calculation?.totalDueNow ?: 0.0
            )

            state.error?.let {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    InfoBox(it, InfoType.ERROR)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.loadPaymentContext(groupId) }) {
                        Text("Retry Connection")
                    }
                }
            }

            // Security note
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Cream2)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Text("🔒", fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                    Column {
                        Text("Secure Payment by YoCo",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Card details are handled entirely by YoCo's PCI-DSS infrastructure. " +
                            "SanibonaniSave never stores your card number, expiry, or CVV.",
                            style = MaterialTheme.typography.bodySmall, color = MidGray,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun YoCoPaymentForm(
    amount: Double,
    description: String,
    onPay: (String, String, String, Double) -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean = false,
    onAmountChanged: (Double) -> Unit = {},
    realtimeShortfall: Double = 0.0,
    realtimeOverpayment: Double = 0.0,
    nextDueDate: String = "",
    allowPartialPayments: Boolean = true,
    minDueAmount: Double = 0.0
) {
    var paymentAmount by remember { mutableDoubleStateOf(amount) }
    var cardNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var cvv        by remember { mutableStateOf("") }

    val isContribution = description.contains("Contribution")
    val canEditAmount = isContribution // Always allow editing to see effects on due date

    LaunchedEffect(amount) {
        if (paymentAmount == 0.0) {
            paymentAmount = amount
            onAmountChanged(amount)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Amount to Pay", style = MaterialTheme.typography.labelSmall, color = MidGray)
                    
                    // Allow editable amount for contributions if partial allowed
                    if (isContribution) {
                        var amountText by remember { mutableStateOf(if (paymentAmount == 0.0) "" else paymentAmount.toString()) }
                        
                        if (canEditAmount) {
                            BasicTextField(
                                value = amountText,
                                onValueChange = { 
                                    if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                                        amountText = it
                                        it.toDoubleOrNull()?.let { newVal ->
                                            paymentAmount = newVal
                                            onAmountChanged(newVal)
                                        } ?: run {
                                            paymentAmount = 0.0
                                            onAmountChanged(0.0)
                                        }
                                    }
                                },
                                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = Forest),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        } else {
                            // Fixed amount if partial not allowed
                            Text(formatZAR(paymentAmount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Forest)
                            Text("Fixed (Partial disabled)", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                        }
                    } else {
                        Text(formatZAR(paymentAmount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Forest)
                    }
                }
                Icon(Icons.Default.CreditCard, null, tint = Forest, modifier = Modifier.size(32.dp))
            }
            
            Text(description, style = MaterialTheme.typography.bodySmall, color = MidGray)
            
            if (isContribution && !allowPartialPayments && minDueAmount > 0) {
                InfoBox("Partial payments are disabled by the group admin. You must pay at least ${formatZAR(minDueAmount)} to proceed.", InfoType.WARNING)
            }

            HorizontalDivider(thickness = 0.5.dp, color = LightGray)

            if (isContribution) {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Resulting Shortfall", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        Text(formatZAR(realtimeShortfall), color = if (realtimeShortfall > 0) Color.Red else Forest, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Resulting Credit", style = MaterialTheme.typography.labelSmall, color = MidGray)
                        Text(formatZAR(realtimeOverpayment), color = Forest, fontWeight = FontWeight.Bold)
                    }
                    if (nextDueDate.isNotEmpty()) {
                        Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                            Text("New Due Date", style = MaterialTheme.typography.labelSmall, color = MidGray)
                            Text(nextDueDate, color = Forest, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = LightGray)
            }

            SanibonaniTextField(
                value = cardNumber,
                onValueChange = { 
                    val filtered = it.filter { char -> char.isDigit() }
                    if(filtered.length <= 16) cardNumber = filtered 
                },
                label = "Card Number",
                placeholder = "0000 0000 0000 0000",
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
                    label = "Expiry (MM/YY)",
                    placeholder = "MM/YY",
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
                    label = "CVV",
                    placeholder = "123",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(Modifier.height(8.dp))

            SanibonaniButton(
                text = "Pay ${formatZAR(paymentAmount)}",
                onClick = { onPay(cardNumber, expiryDate, cvv, paymentAmount) },
                modifier = Modifier.fillMaxWidth(),
                isLoading = isLoading,
                enabled = cardNumber.length >= 13 && expiryDate.length >= 4 && cvv.length >= 3 && paymentAmount > 0 && 
                        (allowPartialPayments || paymentAmount >= minDueAmount - 0.01)
            )
            
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancel Payment", color = MidGray)
            }
        }
    }
}
