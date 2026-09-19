package com.sanibonani.save.ui.screens.member.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.sanibonani.save.ui.screens.member.BeneficiaryItem
import com.sanibonani.save.ui.screens.member.ClaimBeneficiaryDialog
import com.sanibonani.save.ui.screens.member.AddBeneficiaryDialog
import com.sanibonani.save.viewmodel.MemberBeneficiaryViewModel

@Composable
fun MemberBeneficiaryTab(
    beneficiaries: List<Beneficiary>,
    group: Group?,
    member: Member?,
    vm: MemberBeneficiaryViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val isBurial = group?.type == GroupType.BURIAL_SOCIETY
    val limit = group?.maxBeneficiaries ?: 0
    val count = beneficiaries.size
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth().padding(16.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isBurial) "Beneficiary Allowance" else "Dependents", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (isBurial) (if (limit <= 0) "$count added" else "$count of $limit slots used") else "Add covered people.", style = MaterialTheme.typography.bodySmall)
                }
                if (!isBurial || limit <= 0 || count < limit) {
                    Button(onClick = { showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text(stringResource(R.string.ui_add_new)) }
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (beneficiaries.isEmpty()) item { EmptyState(icon = "👥", title = "No beneficiaries", description = "Add dependents here.") }
            else items(items = beneficiaries) { b: Beneficiary ->
                var showEdit by remember { mutableStateOf(false) }
                var showClaim by remember { mutableStateOf(false) }
                BeneficiaryItem(
                    beneficiary = b,
                    isBurial = isBurial,
                    onEdit = { showEdit = true },
                    onDelete = { /* vm.deleteBeneficiary(it) */ },
                    onUpload = { bytes, name -> /* b.id?.let { vm.uploadBeneficiaryDocument(it, bytes, name) } */ },
                    onClaim = { showClaim = true },
                    onDownload = { url, label ->
                        val headers = emptyMap<String, String>()
                        val ext = url.substringAfterLast(".", "pdf").substringBefore("?")
                        val fileName = "Beneficiary_ID_${label.replace(" ", "_")}_${System.currentTimeMillis()}.$ext"
                        onFileAction(url, fileName, headers)
                    }
                )
                if (showEdit) AddBeneficiaryDialog(b, { showEdit = false }, { n, id, r, d, i -> /* b.id?.let { vm.updateBeneficiary(it, n, id, r, d, i) } */; showEdit = false })
                if (showClaim) {
                    ClaimBeneficiaryDialog(
                        beneficiary = b,
                        group = group,
                        member = member,
                        onDismiss = { showClaim = false },
                        onConfirm = { c, d, a, bk, ac, br, h, nt ->
                            vm.submitClaim(b, Money(a), c, d)
                            showClaim = false
                        }
                    )
                }
            }
        }
    }
    if (showAddDialog) AddBeneficiaryDialog(null, { showAddDialog = false }, { n, id, r, d, i -> vm.addBeneficiary(n, id, r, d, i); showAddDialog = false })
}
