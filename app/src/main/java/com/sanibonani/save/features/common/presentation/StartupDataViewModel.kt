package com.sanibonani.save.features.common.presentation

import com.sanibonani.save.domain.utils.safeRunCatching
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.usecase.group.WarmPublicGroupsCacheUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class StartupDataViewModel @Inject constructor(
    private val warmPublicGroupsCache: WarmPublicGroupsCacheUseCase
) : ViewModel() {
    private var preloaded = false

    fun preloadMapData() {
        if (preloaded) return
        preloaded = true

        viewModelScope.launch {
            // Cache-only read, so a slow server never blocks startup; the timeout is the backstop.
            withTimeoutOrNull(7000) {
                safeRunCatching { warmPublicGroupsCache() }
            }
        }
    }

    override fun onCleared() {
        preloaded = false
    }
}
