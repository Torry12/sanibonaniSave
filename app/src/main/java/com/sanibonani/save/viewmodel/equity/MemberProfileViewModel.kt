package com.sanibonani.save.viewmodel.equity

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.MemberUiState
import com.sanibonani.save.viewmodel.state.UIEvent
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
    private val storageRepo: StorageRepository,
    private val supabaseRepo: SupabaseRepository,
    private val credentialsRepo: CredentialsRepository,
    private val syncRepository: SyncRepository
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState
    private var contextObservationJob: Job? = null

    init {
        observeContext()
        updateState { it.copy(biometricEnabled = credentialsRepo.isBiometricEnabled()) }
        viewModelScope.launch { syncRepository.syncStatus.collect { status -> updateState { it.copy(syncStatus = status) } } }
    }

    fun syncAllData() {
        viewModelScope.launch {
            val groupId = uiState.value.selectedGroupId
            if (groupId != null) syncRepository.syncGroupData(groupId) else syncRepository.syncAllData()
        }
    }

    private fun observeContext() {
        contextObservationJob?.cancel()
        contextObservationJob = viewModelScope.launch {
            combine(memberContextCacheService.contexts, uiState.map { it.selectedGroupId }.distinctUntilChanged()) { contexts, id -> contexts[id] }
                .collect { context ->
                    if (context == null || uiState.value.isImpersonating) return@collect
                    updateState { it.copy(member = context.member, group = context.group) }
                    context.member?.id?.let { observeDocuments(it) }
                }
        }
    }

    fun setImpersonationContext(member: Member?, group: Group?) {
        updateState { it.copy(member = member, group = group, isImpersonating = member != null) }
        member?.id?.let { observeDocuments(it) }
    }

    private var documentsJob: Job? = null
    private fun observeDocuments(memberId: String) {
        documentsJob?.cancel()
        documentsJob = viewModelScope.launch {
            documentRepo.observeMemberDocuments(memberId).collect { res ->
                res.onSuccess { docs -> updateState { it.copy(documents = docs) } }
            }
        }
    }

    fun updateBankingDetails(bank: String, account: String, branch: String) {
        val member = uiState.value.member ?: return
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            memberRepo.updateMemberBankingDetails(member.id!!, bank, account, branch).onSuccess {
                updateState { it.copy(isLoading = false) }
                sendEvent(UIEvent.ShowMessage("Banking details updated"))
                memberContextCacheService.warmUpForUser(supabaseRepo.currentUserId!!)
            }
        }
    }

    fun uploadDocument(index: Int, data: ByteArray, name: String, mime: String) {
        val member = uiState.value.member ?: return
        viewModelScope.launch {
            updateState { it.copy(isUploading = true) }
            memberRepo.uploadMemberDocument(member.id!!, index, data, name, mime).onSuccess {
                updateState { it.copy(isUploading = false) }
            }
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        credentialsRepo.setBiometricEnabled(enabled)
        updateState { it.copy(biometricEnabled = enabled) }
    }
}
