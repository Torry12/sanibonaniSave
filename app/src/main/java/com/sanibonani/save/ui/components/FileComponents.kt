package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.sanibonani.save.ui.theme.Navy

@Composable
fun FileActionDialog(
    onView: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    fileName: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File Options") },
        text = { Text("How would you like to handle '$fileName'?") },
        confirmButton = {
            TextButton(onClick = onView) {
                Icon(Icons.Default.Visibility, null)
                Spacer(Modifier.width(8.dp))
                Text("View In-App")
            }
        },
        dismissButton = {
            TextButton(onClick = onDownload) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Download")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun FileViewerDialog(
    url: String,
    type: String,
    headers: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(Modifier.fillMaxSize()) {
                if (type.contains("pdf", ignoreCase = true)) {
                    PdfViewer(url, headers)
                } else {
                    ImageViewer(url, headers)
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ImageViewer(url: String, headers: Map<String, String>) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PdfViewer(url: String, headers: Map<String, String>) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(64.dp), tint = Color.White)
            Spacer(Modifier.height(16.dp))
            Text("PDF Viewing in Browser or External App", color = Color.White)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { /* External View Logic */ }) {
                Text("Open Externally")
            }
        }
    }
}
