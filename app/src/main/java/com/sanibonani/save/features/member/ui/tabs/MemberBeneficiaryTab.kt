package com.sanibonani.save.features.member.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.ErrorRed
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.features.member.ui.BeneficiaryItem
import com.sanibonani.save.features.member.ui.ClaimBeneficiaryDialog
import com.sanibonani.save.features.member.ui.AddBeneficiaryDialog
import com.sanibonani.save.viewmodel.disbursement.MemberBeneficiaryViewModel

@Composable
fun MemberBeneficiaryTab(
    beneficiaries: List<Beneficiary>,
    vm: MemberBeneficiaryViewModel,
    onFileAction: (String, String, Map<String, String>) -> Unit = { _, _, _ -> }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val group = state.group
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    val isBurial = group?.type == GroupType.BURIAL_SOCIETY
    val limit = group?.maxBeneficiaries ?: 0
    val count = beneficiaries.size
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            SanibonaniSectionHeader(
                title = "Beneficiaries & Dependents",
                subtitle = if (isBurial) {
                    if (limit <= 0) "Manage covered individuals ($count added)"
                    else "$count of $limit slots used"
                } else "Manage covered individuals"
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isBurial || limit <= 0 || count < limit) {
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy),
                        border = BorderStroke(1.dp, Navy.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_add_new))
                    }
                }

                if (isBurial) {
                    Button(
                        onClick = {
                             ToastUtils.showInfo(context, "Select a beneficiary below to initiate a claim.")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Icon(Icons.Default.MedicalServices, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New Claim")
                    }
                }
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (beneficiaries.isEmpty()) {
                item {
                    EmptyState(
                        icon = "👥",
                        title = "No Beneficiaries",
                        description = "Add your dependents and loved ones to ensure they are covered by the group.",
                        actionLabel = "Add First Dependent",
                        onAction = { showAddDialog = true }
                    )
                }
            } else {
                items(items = beneficiaries) { b: Beneficiary ->
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
                            vm = vm,
                            onDismiss = {
                                showClaim = false
                                vm.clearClaimState()
                            }
                        )
                    }
                }
            }
        }
    }
    if (showAddDialog) AddBeneficiaryDialog(null, { showAddDialog = false }, { n, id, r, d, i -> vm.addBeneficiary(n, id, r, d, i); showAddDialog = false })
}
