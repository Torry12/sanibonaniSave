package com.sanibonani.save.features.common.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.repository.group.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class StartupDataViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {
    private var preloaded = false

    fun preloadMapData() {
        if (preloaded) return
        preloaded = true

        viewModelScope.launch {
            // Warm public groups cache on app launch so map screens render faster.
            withTimeoutOrNull(7000) {
                runCatching { groupRepository.getPublicGroups().first() }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        preloaded = false
    }
}
