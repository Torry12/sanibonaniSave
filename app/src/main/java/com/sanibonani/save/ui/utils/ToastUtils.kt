package com.sanibonani.save.ui.utils

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Toast utility for consistent, non-blocking user feedback.
 * Use this for notifications, errors, and confirmations.
 */
object ToastUtils {
    fun showShort(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun showLong(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun showError(context: Context, message: String) {
        showLong(context, "❌ $message")
    }

    fun showSuccess(context: Context, message: String) {
        showShort(context, "✅ $message")
    }

    fun showWarning(context: Context, message: String) {
        showLong(context, "⚠️ $message")
    }

    fun showInfo(context: Context, message: String) {
        showShort(context, "ℹ️ $message")
    }

    fun showProcessing(context: Context, message: String) {
        showShort(context, "⏳ $message")
    }
}

/**
 * Composable helper to show toast from side effects
 */
@Composable
fun ShowToast(message: String?, duration: Int = Toast.LENGTH_SHORT) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, duration).show()
        }
    }
}

