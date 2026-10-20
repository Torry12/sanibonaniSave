package com.sanibonani.save.features.member.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sanibonani.save.features.member.presentation.MemberProfileViewModel

fun formatRelativeSyncTime(lastSyncMillis: Long?): String {
    if (lastSyncMillis == null || lastSyncMillis == 0L) return "Never synced"
    val diff = System.currentTimeMillis() - lastSyncMillis
    return when {
        diff < 60_000 -> "Synced just now"
        diff < 3600_000 -> "Synced ${diff / 60_000}m ago"
        diff < 86400_000 -> "Synced ${diff / 3600_000}h ago"
        else -> "Synced ${diff / 86400_000}d ago"
    }
}

fun getFileNameFromUri(contentResolver: ContentResolver, uri: Uri): String {
    var name = "file_${System.currentTimeMillis()}"
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) name = cursor.getString(nameIndex)
        }
    }
    return name
}

fun handleProfilePhotoSelection(context: Context, vm: MemberProfileViewModel, uri: Uri?) {
    uri?.let {
        val bytes = context.contentResolver.openInputStream(it)?.readBytes()
        if (bytes != null) {
            val fileName = getFileNameFromUri(context.contentResolver, it)
            vm.uploadDocument(0, bytes, fileName, "image/jpeg")
        }
    }
}
