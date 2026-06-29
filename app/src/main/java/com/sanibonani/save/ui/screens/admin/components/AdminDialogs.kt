package com.sanibonani.save.ui.screens.admin.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.ui.components.SanibonaniTextField

@Composable
fun BeneficiaryEditDialog(
    beneficiary: Beneficiary,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onUpdate: (Beneficiary) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Beneficiary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SanibonaniTextField(
                    value = beneficiary.fullName,
                    onValueChange = { text -> onUpdate(beneficiary.copy(fullName = text)) },
                    label = "Full Name"
                )
                SanibonaniTextField(
                    value = beneficiary.idNumber ?: "",
                    onValueChange = { text -> onUpdate(beneficiary.copy(idNumber = text)) },
                    label = "ID Number"
                )
                SanibonaniTextField(
                    value = beneficiary.relationship ?: "",
                    onValueChange = { text -> onUpdate(beneficiary.copy(relationship = text)) },
                    label = "Relationship"
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                else Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
