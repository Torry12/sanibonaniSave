package com.sanibonani.save.domain.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

object FileDownloader {

    fun downloadFile(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String = "application/pdf",
        headers: Map<String, String> = emptyMap()
    ): Long {
        return try {
            if (url.isBlank()) {
                Toast.makeText(context, "Download URL is empty", Toast.LENGTH_SHORT).show()
                return -1L
            }

            val finalUrl = when {
                url.startsWith("http://") || url.startsWith("https://") -> url
                url.startsWith("//") -> "https:$url"
                else -> "https://$url"
            }

            val request = DownloadManager.Request(Uri.parse(finalUrl))
                .setTitle(fileName)
                .setDescription("Downloading $fileName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setMimeType(mimeType)

            headers.forEach { (key, value) ->
                request.addRequestHeader(key, value)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
            downloadId
        } catch (e: Exception) {
            android.util.Log.e("FileDownloader", "Download failed: ${e.message}", e)
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            -1L
        }
    }
}
