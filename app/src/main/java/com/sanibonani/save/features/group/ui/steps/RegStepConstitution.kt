package com.sanibonani.save.features.group.ui.steps

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.domain.config.FileUploadLimits
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationState
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import com.sanibonani.save.features.group.ui.components.SectionHeader
import com.sanibonani.save.ui.components.DocumentUploadCard
import com.sanibonani.save.ui.utils.ToastUtils

/** Step 5: constitution upload / standard constitution selection. */
@Composable
fun RegStepConstitution(s: GroupRegistrationState, vm: GroupRegistrationViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionHeader(
            title = stringResource(R.string.ui_constitution),
            description = stringResource(R.string.ui_upload_your_groups_constitution_or_rules)
        )

        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        val fileName = "constitution_${System.currentTimeMillis()}.pdf"
                        if (bytes.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
                            ToastUtils.showError(context, "File too large. Maximum 3MB allowed.")
                        } else {
                            vm.uploadConstitution(fileName)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(
                        "GroupRegistrationScreen",
                        "Failed to read selected constitution file: ${e.message}",
                    )
                    ToastUtils.showError(context, "Failed to read the selected file. Please try another PDF.")
                }
            }
        }

        DocumentUploadCard(
            label = "Group Constitution",
            isUploaded = s.constitutionUrl != null || s.pendingConstitutionName != null,
            status = if (s.pendingConstitutionName != null) DocumentStatus.PENDING else s.constitutionStatus,
            onUpload = { launcher.launch("application/pdf") }
        )

        Card(
            onClick = { vm.onEvent(GroupFormEvent.UseStandardConstitutionToggled(!s.useStandardConstitution)) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(
                1.dp,
                if (s.useStandardConstitution) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = s.useStandardConstitution,
                    onCheckedChange = { vm.onEvent(GroupFormEvent.UseStandardConstitutionToggled(it)) },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.ui_use_standard_constitution),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ui_generate_a_professional_policy_document_),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (s.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        stringResource(R.string.ui_why_is_this_needed),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ui_a_constitution_helps_resolve_disputes_an),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
