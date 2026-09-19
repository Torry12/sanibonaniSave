package com.sanibonani.save.ui.screens.member

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.viewmodel.MemberViewModel

fun isBurialSocietyLike(group: Group?): Boolean {
    return group?.type == GroupType.BURIAL_SOCIETY ||
        group?.maxBeneficiaries != null ||
        group?.beneficiaryIncreasePct != null
}

fun formatRelativeSyncTime(timestampMillis: Long?): String {
    if (timestampMillis == null || timestampMillis <= 0L) return "Not synced yet"
    val delta = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
    val seconds = delta / 1000
    return when {
        seconds < 60 -> "Synced just now"
        seconds < 3600 -> "Synced ${seconds / 60}m ago"
        seconds < 86400 -> "Synced ${seconds / 3600}h ago"
        else -> "Synced ${seconds / 86400}d ago"
    }
}

fun getFileNameFromUri(cr: ContentResolver, uri: Uri): String {
    var name = ""
    cr.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx != -1 && cursor.moveToFirst()) name = cursor.getString(idx)
    }
    return if (name.isBlank()) "upload_${System.currentTimeMillis()}" else name
}

fun handleProfilePhotoSelection(context: Context, vm: MemberViewModel, uri: Uri?) {
    uri?.let {
        runCatching {
            val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
            if (bytes != null) vm.uploadDocument(0, bytes, getFileNameFromUri(context.contentResolver, it), "Profile Photo")
        }
    }
}
