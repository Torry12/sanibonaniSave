package com.sanibonani.save.viewmodel.state

import java.io.File

/**
 * Common one-time events that should be consumed by the UI.
 */
sealed class UIEvent {
    data class ShowMessage(val message: String) : UIEvent()
    data class ShowError(val message: String) : UIEvent()
    data object NavigateBack : UIEvent()
    data class NavigateTo(val route: String) : UIEvent()
    
    data class OpenFile(
        val file: File,
        val mimeType: String,
        val chooserTitle: String
    ) : UIEvent()

    data class DownloadFile(
        val url: String,
        val fileName: String,
        val mimeType: String,
        val headers: Map<String, String>
    ) : UIEvent()
}
