package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.viewmodel.state.MemberUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberProfileViewModel @Inject constructor(
    private val memberContextCacheService: MemberGroupContextCacheService,
    private val memberRepo: MemberRepository,
    private val documentRepo: MemberDocumentRepository,
    private val storageRepo: StorageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MemberUiState())
    val state: StateFlow<MemberUiState> = _state.asStateFlow()

    private var contextObservationJob: Job? = null

    init {
        observeContext()
    }

    private fun observeContext() {
        contextObservationJob?.cancel()
        contextObservationJob = viewModelScope.launch {
            memberContextCacheService.contexts.collect { contexts ->
                val selectedId = _state.value.selectedGroupId
                val context = contexts[selectedId] ?: return@collect

                _state.update { it.copy(
                    member = context.member,
                    group = context.group,
                    documents = context.member?.id?.let { memberId ->
                        // Actually the context usually has them but let's check
                        // For now we use context.member and assume docs are nested or fetched separately
                        // In MemberUiState, 'documents' is a separate list
                        emptyList() // Placeholder until we verify how they are stored in CachedGroupContext
                    } ?: emptyList()
                ) }
            }
        }
    }

    fun updateProfile(fullName: String, email: String, phone: String, bank: String, account: String, branch: String) {
        val member = _state.value.member ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            memberRepo.updateMemberBankingDetails(member.id!!, bank, account, branch).onSuccess {
                _state.update { it.copy(isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun uploadDocument(typeIndex: Int, data: ByteArray, fileName: String, mime: String) {
        val member = _state.value.member ?: return
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true) }
            memberRepo.uploadMemberDocument(member.id!!, typeIndex, data, fileName, mime).onSuccess {
                _state.update { it.copy(isUploading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isUploading = false, error = e.message) }
            }
        }
    }

    fun selectGroup(groupId: String) {
        _state.update { it.copy(selectedGroupId = groupId) }
    }
}
