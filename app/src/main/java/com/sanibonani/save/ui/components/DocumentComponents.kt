package com.sanibonani.save.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.ui.theme.*

@Composable
fun DocumentUploadCard(
    label: String,
    isUploaded: Boolean,
    status: DocumentStatus,
    onUpload: () -> Unit,
    onView: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (isUploaded) SuccessGreen.copy(alpha = 0.3f) else Color.LightGray),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isUploaded) SuccessGreen.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isUploaded) Icons.Default.Description else Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = if (isUploaded) SuccessGreen else Color.Gray
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                if (isUploaded) {
                    val statusText = when (status) {
                        DocumentStatus.PENDING -> "Pending Verification"
                        DocumentStatus.VERIFIED -> "Verified"
                        DocumentStatus.REJECTED -> "Rejected"
                    }
                    val statusColor = when (status) {
                        DocumentStatus.PENDING -> WarningAmber
                        DocumentStatus.VERIFIED -> SuccessGreen
                        DocumentStatus.REJECTED -> ErrorRed
                    }
                    Text(statusText, color = statusColor, style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("Not uploaded", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            }

            if (isUploaded && onView != null) {
                IconButton(onClick = onView) {
                    Icon(Icons.Default.Visibility, "View", tint = Navy)
                }
            }

            Button(
                onClick = onUpload,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isUploaded) Navy.copy(alpha = 0.1f) else Navy,
                    contentColor = if (isUploaded) Navy else Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(if (isUploaded) "Update" else "Upload", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DocumentAdminCard(
    label: String,
    url: String?,
    status: DocumentStatus,
    onVerify: (Boolean) -> Unit,
    onReject: (String, String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val (statusText, statusColor) = when (status) {
                    DocumentStatus.PENDING -> "Pending" to WarningAmber
                    DocumentStatus.VERIFIED -> "Verified" to SuccessGreen
                    DocumentStatus.REJECTED -> "Rejected" to ErrorRed
                }
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (url.isNullOrBlank()) {
                Text("No document uploaded", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { /* View Logic */ },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("View")
                    }

                    if (status == DocumentStatus.PENDING) {
                        Button(
                            onClick = { onVerify(true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Verify")
                        }
                    }
                }
            }
        }
    }
}
