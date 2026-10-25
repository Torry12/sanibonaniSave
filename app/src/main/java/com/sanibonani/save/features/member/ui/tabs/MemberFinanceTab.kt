package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.features.member.presentation.MemberFinanceViewModel
import com.sanibonani.save.features.member.ui.LoanItem
import com.sanibonani.save.features.member.ui.LoanRequestDialog
import com.sanibonani.save.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun MemberFinanceTab(
    loans: List<Loan>,
    group: Group?,
    vm: MemberFinanceViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    var showRequest by remember { mutableStateOf(false) }
    var eligibilityReason by remember { mutableStateOf<String?>(null) }
    var showIneligibleDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val handleApplyClick: () -> Unit = {
        scope.launch {
            when (val result = vm.checkLoanEligibility()) {
                is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible -> {
                    showRequest = true
                }
                is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible -> {
                    eligibilityReason = result.reason
                    showIneligibleDialog = true
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(stringResource(R.string.ui_emergency_loans), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text(stringResource(R.string.ui_apply_for_a_loan_based_on_group_rules), style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = handleApplyClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(stringResource(R.string.ui_apply))
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loans.isEmpty()) item {
                EmptyState(
                    icon = "🏦",
                    title = "No loans",
                    description = "You haven't requested any loans yet.",
                    actionLabel = "Apply Now",
                    onAction = handleApplyClick
                )
            }
            else items(items = loans, key = { it.id ?: it.hashCode().toString() }) { loan: Loan ->
                LoanItem(
                    loan = loan,
                    onDownloadContract = {
                        val url = loan.contractUrl
                        if (!url.isNullOrBlank()) {
                            val headers = emptyMap<String, String>()
                            val ext = url.substringAfterLast(".", "pdf").substringBefore("?")
                            val fileName = "Loan_Agreement_${loan.id?.take(5)}_${System.currentTimeMillis()}.$ext"
                            onFileAction(url, fileName, headers)
                        }
                    }
                )
            }
        }
    }
    if (showRequest) LoanRequestDialog(group, { showRequest = false }, { a, m, p -> vm.requestLoan(Money(a), p, m); showRequest = false })

    if (showIneligibleDialog) {
        AlertDialog(
            onDismissRequest = { showIneligibleDialog = false },
            title = { Text("Loan Qualification Notice", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You do not currently qualify for a loan.", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    eligibilityReason?.let { Text("Reason: $it", style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(4.dp))
                    Text("Loan Qualification Rules:", fontWeight = FontWeight.Bold)
                    Text("• Member status must be Active.")
                    Text("• Group must support loans (Investment Clubs excluded).")
                    Text("• Member must be in good standing (no contribution shortfalls or overdue payments).")
                    Text("• No active or outstanding loans currently.")
                    Text("• Maximum borrowing power is up to 30% of total contributions made over the last 6 months.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showIneligibleDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }
}
