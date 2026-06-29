package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.state.admin.AdminAccountState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AdminAccountViewModel @Inject constructor(
    private val adminContextCacheService: AdminGroupContextCacheService,
    private val authViewModel: AuthViewModel
) : ViewModel() {

    private val _state = MutableStateFlow(AdminAccountState())
    val state: StateFlow<AdminAccountState> = _state.asStateFlow()

    init {
        observeGroupContext()
    }

    private fun observeGroupContext() {
        adminContextCacheService.currentGroup.onEach { group ->
            _state.update { it.copy(group = group) }
        }.launchIn(viewModelScope)
    }

    fun logout() {
        authViewModel.signOut()
    }
}
