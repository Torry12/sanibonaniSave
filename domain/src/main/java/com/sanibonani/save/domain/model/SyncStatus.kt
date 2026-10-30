package com.sanibonani.save.domain.model

/**
 * The state of a sync pass, as the UI sees it.
 *
 * This sits with the other domain models rather than beside [com.sanibonani.save.domain.repository.system.SyncRepository]
 * because a screen or a state holder has to name it to render progress, and importing a repository
 * port to get the type is what made those files look like data-layer callers.
 */
sealed class SyncStatus {
    object Idle : SyncStatus()
    data class Progress(val message: String, val progress: Float) : SyncStatus()
    object Completed : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
