package com.sanibonani.save.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sanibonani.save.domain.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@Composable
fun FileActionDialog(
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onDownload: () -> Unit,
    fileName: String = "File"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FilePresent, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("File Options") },
        text = { Text("Would you like to view '$fileName' inside the app or download it to your device?") },
        confirmButton = {
            Button(onClick = { onView(); onDismiss() }) {
                Text("View In-App")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDownload(); onDismiss() }) {
                Text("Download")
            }
        }
    )
}

@Composable
fun FileViewerDialog(
    url: String,
    fileName: String,
    headers: Map<String, String>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.9f)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                    Text(fileName, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val isPdf = url.substringAfterLast(".", "").substringBefore("?").lowercase() == "pdf"
                    if (isPdf) {
                        PdfViewer(url, headers)
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .apply { headers.forEach { (k,v) -> addHeader(k,v) } }
                                .build(),
                            contentDescription = fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewer(url: String, headers: Map<String, String>) {
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                val input = connection.getInputStream()
                val file = File(context.cacheDir, "temp_view.pdf")
                FileOutputStream(file).use { input.copyTo(it) }

                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                pdfBitmap = bitmap
                page.close()
                renderer.close()
                isLoading = false
            } catch (e: Exception) {
                AppLogger.e("PdfViewer", "Failed to render PDF", e)
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        pdfBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Failed to load PDF", color = Color.White)
        }
    }
}
