package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.config.FileUploadLimits
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.data.utils.sanitizeMemberDocumentUploadError
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberBeneficiaryUiState(
    val beneficiaries: List<Beneficiary> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class MemberBeneficiaryViewModel @Inject constructor(
    private val beneficiaryRepo: BeneficiaryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MemberBeneficiaryUiState())
    val state: StateFlow<MemberBeneficiaryUiState> = _state.asStateFlow()

    private var observationJob: Job? = null
    private var currentMemberId: String? = null

    fun setContext(memberId: String) {
        if (currentMemberId == memberId) return
        currentMemberId = memberId
        startObserving(memberId)
    }

    private fun startObserving(memberId: String) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            beneficiaryRepo.observeBeneficiaries(memberId).collect { res ->
                res.onSuccess { list ->
                    _state.update { it.copy(beneficiaries = list, isLoading = false) }
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
            }
        }
    }

    fun addBeneficiary(
        memberId: String,
        groupId: String,
        name: String,
        idNumber: String?,
        relationship: String?,
        dob: String?,
        isOver65: Boolean = false
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val beneficiary = Beneficiary(
                groupId = groupId,
                memberId = memberId,
                fullName = name,
                idNumber = idNumber,
                relationship = relationship,
                dateOfBirth = dob,
                isOver65 = isOver65
            )
            beneficiaryRepo.addBeneficiary(beneficiary).onSuccess {
                _state.update { it.copy(isLoading = false, successMessage = "Beneficiary added successfully") }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun updateBeneficiary(
        id: String,
        memberId: String,
        groupId: String,
        name: String,
        idNumber: String?,
        relationship: String?,
        dob: String?,
        isOver65: Boolean = false
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val beneficiary = Beneficiary(
                id = id,
                groupId = groupId,
                memberId = memberId,
                fullName = name,
                idNumber = idNumber,
                relationship = relationship,
                dateOfBirth = dob,
                isOver65 = isOver65
            )
            beneficiaryRepo.updateBeneficiary(beneficiary).onSuccess {
                _state.update { it.copy(isLoading = false, successMessage = "Beneficiary updated successfully") }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun deleteBeneficiary(groupId: String, memberId: String, id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            beneficiaryRepo.deleteBeneficiary(groupId, memberId, id).onSuccess {
                _state.update { it.copy(isLoading = false, successMessage = "Beneficiary removed") }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun uploadBeneficiaryDocument(
        beneficiaryId: String,
        groupId: String,
        memberId: String,
        byteArray: ByteArray,
        fileName: String
    ) {
        if (byteArray.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
            _state.update { it.copy(error = "File size exceeds 3MB limit") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, error = null) }
            beneficiaryRepo.uploadBeneficiaryDocument(
                beneficiaryId = beneficiaryId,
                groupId = groupId,
                memberId = memberId,
                byteArray = byteArray,
                fileName = fileName
            ).onSuccess {
                _state.update { it.copy(isUploading = false, successMessage = "Document uploaded successfully") }
            }.onFailure { e ->
                val userMessage = sanitizeMemberDocumentUploadError(e.toUserMessage())
                _state.update { it.copy(isUploading = false, error = userMessage) }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
    }
}
