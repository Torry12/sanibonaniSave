package com.sanibonani.save.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.ui.theme.*

@Composable
fun DocumentUploadCard(
    label: String,
    isUploaded: Boolean,
    status: DocumentStatus = DocumentStatus.PENDING,
    onUpload: () -> Unit,
    onView: (() -> Unit)? = null,
    imageUrl: String? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isUploaded) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isUploaded) SuccessBg.copy(alpha = if (isSystemInDarkTheme()) 0.2f else 1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isUploaded && !imageUrl.isNullOrBlank() && (imageUrl.endsWith(".jpg", true) || imageUrl.endsWith(".png", true) || imageUrl.endsWith(".jpeg", true))) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Document Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (isUploaded) Icons.Default.Check else Icons.Default.CloudUpload,
                        null,
                        tint = if (isUploaded) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                if (isUploaded) {
                    Text(
                        status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (status) {
                            DocumentStatus.VERIFIED -> SuccessGreen
                            DocumentStatus.REJECTED -> ErrorRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (isUploaded && onView != null) {
                IconButton(onClick = onView) {
                    Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (!isUploaded || status == DocumentStatus.REJECTED) {
                IconButton(onClick = onUpload) {
                    Icon(if (isUploaded) Icons.Default.Refresh else Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
