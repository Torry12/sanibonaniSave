package com.sanibonani.save.features.admin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.R
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.components.*
import com.sanibonani.save.ui.theme.*

@Composable
internal fun MemberDocumentsSection(
    member: Member,
    documents: List<MemberDocument>,
    onVerifyDoc: (Int, Boolean) -> Unit,
    onVerifyRelDoc: (String, Boolean) -> Unit,
    downloadMemberDocument: (String, String) -> Pair<String, Map<String, String>>,
    onFileAction: (String, String, Map<String, String>) -> Unit
) {
    DetailSection("Documents") {
        var hasAnyDoc = false
        // Core KYC Documents (Indexed 1-4)
        for (i in 1..4) {
            val url = when (i) {
                1 -> member.document1Url
                2 -> member.document2Url
                3 -> member.document3Url
                else -> member.document4Url
            }
            val status = when (i) {
                1 -> member.document1Status
                2 -> member.document2Status
                3 -> member.document3Status
                else -> member.document4Status
            }
            val label = when (i) {
                1 -> "Identity Document (ID)"
                2 -> "Proof of Residence"
                3 -> "Beneficiary Form / Documents"
                else -> "Marriage Certificate"
            }

            if (url != null) {
                hasAnyDoc = true
                DocumentAdminCard(
                    label = label,
                    url = url,
                    status = status,
                    onVerify = { approve ->
                        onVerifyDoc(i, approve)
                    },
                    onDownload = { dUrl, dLabel ->
                        val (dlUrl, headers) = downloadMemberDocument(dUrl, dLabel)
                        val extension = dlUrl.substringAfterLast(".", "pdf")
                        val fileName = "${dLabel.replace(" ", "_")}_${System.currentTimeMillis()}.$extension"
                        onFileAction(dlUrl, fileName, headers)
                    }
                )
            }
        }

        // New Relational Documents
        documents.forEach { doc ->
            val docId = doc.id
            if (docId != null) {
                hasAnyDoc = true
                DocumentAdminCard(
                    label = doc.label,
                    url = doc.documentUrl,
                    status = doc.status,
                    onVerify = { approve -> onVerifyRelDoc(docId, approve) },
                    onDownload = { dUrl, dLabel ->
                        val (dlUrl, headers) = downloadMemberDocument(dUrl, dLabel)
                        val extension = dlUrl.substringAfterLast(".", "pdf")
                        val fileName = "${dLabel.replace(" ", "_")}_${System.currentTimeMillis()}.$extension"
                        onFileAction(dlUrl, fileName, headers)
                    }
                )
            }
        }

        if (!hasAnyDoc) {
            Text(stringResource(R.string.ui_no_documents_uploaded_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemberMessageSection(
    member: Member,
    messageText: String,
    onMessageChange: (String) -> Unit,
    selectedNotifChannel: NotifChannel,
    onNotifChannelChange: (NotifChannel) -> Unit,
    isSending: Boolean,
    messageSuccess: Boolean,
    onSendMessage: (String, String) -> Unit
) {
    DetailSection("Send Direct Message") {
        SanibonaniTextField(
            value = messageText,
            onValueChange = onMessageChange,
            label = stringResource(R.string.ui_message_text),
            singleLine = false,
            modifier = Modifier.height(100.dp)
        )

        Text(
            "Send Via:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotifChannel.entries.forEach { channel ->
                val isSelected = selectedNotifChannel == channel
                val (label, icon) = when (channel) {
                    NotifChannel.PUSH -> "In-App" to Icons.Default.NotificationsActive
                    NotifChannel.EMAIL -> "Email" to Icons.Default.Email
                    NotifChannel.SMS -> "SMS" to Icons.Default.Sms
                    NotifChannel.WHATSAPP -> "WhatsApp" to Icons.AutoMirrored.Filled.Chat
                    NotifChannel.BOTH -> "Both" to Icons.Default.AllInclusive
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { onNotifChannelChange(channel) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Color.Transparent,
                        selectedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        if (messageSuccess) {
            InfoBox("Message sent successfully!", type = InfoType.SUCCESS)
        }

        Spacer(Modifier.height(4.dp))

        SanibonaniButton(
            text = "Send Message",
            onClick = {
                member.id?.let { id ->
                    onSendMessage(messageText, id)
                }
            },
            isLoading = isSending,
            enabled = messageText.isNotBlank() && !isSending,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
