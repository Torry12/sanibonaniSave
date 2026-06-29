package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.config.FileUploadLimits
import com.sanibonani.save.data.utils.memberDocumentLabel
import com.sanibonani.save.data.utils.sanitizeMemberDocumentUploadError
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberDocumentUiState(
    val documents: List<MemberDocument> = emptyList(),
    val isUploading: Boolean = false,
    val uploadProgress: Double? = null,
    val profileImageVersion: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class MemberDocumentViewModel @Inject constructor(
    private val documentRepo: MemberDocumentRepository,
    private val memberRepo: MemberRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MemberDocumentUiState())
    val state: StateFlow<MemberDocumentUiState> = _state.asStateFlow()

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
            documentRepo.observeMemberDocuments(memberId).collect { res ->
                res.onSuccess { list ->
                    _state.update { it.copy(documents = list, isLoading = false) }
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
            }
        }
    }

    fun uploadDocument(
        memberId: String,
        documentIndex: Int,
        byteArray: ByteArray,
        fileName: String,
        documentType: String? = null
    ) {
        if (byteArray.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
            _state.update { it.copy(error = "File size exceeds 3MB limit") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, error = null, uploadProgress = 0.0) }
            
            memberRepo.uploadMemberDocument(
                memberId = memberId,
                documentIndex = documentIndex,
                byteArray = byteArray,
                fileName = fileName,
                documentType = documentType
            ).onSuccess { uploadedUrl ->
                _state.update { it.copy(isUploading = false, uploadProgress = 1.0, successMessage = "${memberDocumentLabel(documentIndex)} uploaded successfully") }
                
                // Reconcile from backend
                reconcileUploadedDocument(memberId, documentIndex, uploadedUrl, documentType)
            }.onFailure { e ->
                val userMessage = sanitizeMemberDocumentUploadError(e.toUserMessage())
                _state.update { it.copy(isUploading = false, uploadProgress = null, error = userMessage) }
            }
        }
    }

    fun uploadRelationalDocument(
        memberId: String,
        groupId: String,
        label: String,
        byteArray: ByteArray,
        fileName: String
    ) {
        if (byteArray.size > FileUploadLimits.MAX_FILE_SIZE_BYTES) {
            _state.update { it.copy(error = "File size exceeds 3MB limit") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, error = null, uploadProgress = 0.0) }
            
            documentRepo.uploadAndAddMemberDocument(
                memberId = memberId,
                groupId = groupId,
                label = label,
                byteArray = byteArray,
                fileName = fileName
            ).onSuccess {
                _state.update { it.copy(isUploading = false, uploadProgress = 1.0, successMessage = "Document '$label' uploaded successfully") }
            }.onFailure { e ->
                _state.update { it.copy(isUploading = false, error = e.toUserMessage()) }
            }
        }
    }

    private fun reconcileUploadedDocument(
        memberId: String,
        documentIndex: Int,
        uploadedUrl: String,
        documentType: String?
    ) {
        viewModelScope.launch {
            delay(350)
            memberRepo.getMemberById(memberId).onSuccess { refreshed ->
                if (documentIndex == 0) { // PROFILE_PHOTO_INDEX
                    _state.update { it.copy(profileImageVersion = System.currentTimeMillis()) }
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _state.update { it.copy(successMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
    }
}
