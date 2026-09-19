package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.config.SaReferenceData

/**
 * Reusable banking detail fields (Bank Name dropdown, Account Number, Branch Code).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankingDetailsFields(
    bankName: String,
    accountNo: String,
    branchCode: String,
    onBankNameChange: (String) -> Unit,
    onAccountNoChange: (String) -> Unit,
    onBranchCodeChange: (String) -> Unit,
    isReadOnly: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Bank Name Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded && !isReadOnly,
            onExpandedChange = { if (!isReadOnly) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = bankName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Bank Name *") },
                trailingIcon = { if (!isReadOnly) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            if (!isReadOnly) {
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    SaReferenceData.BANKS.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b) },
                            onClick = {
                                onBankNameChange(b)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // 2. Account Number
        SanibonaniTextField(
            value = accountNo,
            onValueChange = { if (it.all { c -> c.isDigit() }) onAccountNoChange(it) },
            label = "Account Number *",
            enabled = !isReadOnly,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        // 3. Branch Code
        SanibonaniTextField(
            value = branchCode,
            onValueChange = { if (it.all { c -> c.isDigit() }) onBranchCodeChange(it) },
            label = "Branch Code *",
            enabled = !isReadOnly,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}
