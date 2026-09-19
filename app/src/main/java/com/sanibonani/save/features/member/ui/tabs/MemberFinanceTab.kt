package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.features.member.ui.LoanItem
import com.sanibonani.save.features.member.ui.LoanRequestDialog
import com.sanibonani.save.viewmodel.credit.MemberFinanceViewModel

@Composable
fun MemberFinanceTab(
    loans: List<Loan>,
    group: Group?,
    vm: MemberFinanceViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    var showRequest by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth().padding(16.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(stringResource(R.string.ui_emergency_loans), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text(stringResource(R.string.ui_apply_for_a_loan_based_on_group_rules), style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = { showRequest = true },
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
                    onAction = { showRequest = true }
                )
            }
            else items(items = loans) { loan: Loan ->
                LoanItem(
                    loan = loan,
                    onDownloadContract = {
                        val url = loan.contractUrl
                        if (!url.isNullOrBlank()) {
                            // val headers = vm.getDownloadParams(url)
                            val headers = emptyMap<String, String>() // Placeholder
                            val ext = url.substringAfterLast(".", "pdf").substringBefore("?")
                            val fileName = "Loan_Agreement_${loan.id?.take(5)}_${System.currentTimeMillis()}.$ext"
                            onFileAction(url, fileName, headers)
                        }
                    }
                )
            }
        }
    }
    if (showRequest) LoanRequestDialog(group, { showRequest = false }, { a, m, p -> vm.requestLoan(Money(a), m, p); showRequest = false })
}
