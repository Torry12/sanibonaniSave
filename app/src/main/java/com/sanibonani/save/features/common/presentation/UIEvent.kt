package com.sanibonani.save.features.common.presentation

import java.io.File

/**
 * Standardized UI Events for single-shot side effects.
 */
open class UIEvent {
    data class ShowMessage(val message: String) : UIEvent()
    data class ShowError(val message: String) : UIEvent()
    data class Navigate(val route: String, val popUpTo: String? = null, val inclusive: Boolean = false) : UIEvent()
    data class OpenFile(val file: File, val mimeType: String, val chooserTitle: String) : UIEvent()
    data class OpenUrl(val url: String) : UIEvent()
    data class DownloadFile(val url: String, val fileName: String, val headers: Map<String, String>) : UIEvent()
}
