package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.auth.BuildDocumentAuthHeadersUseCase
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.IsBiometricEnabledUseCase
import com.sanibonani.save.domain.usecase.auth.SetBiometricEnabledUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMemberDocumentsUseCase
import com.sanibonani.save.domain.usecase.member.UpdateMemberBankingDetailsUseCase
import com.sanibonani.save.domain.usecase.member.UploadMemberDocumentUseCase
import com.sanibonani.save.domain.usecase.member.UploadRelationalDocumentUseCase
import com.sanibonani.save.domain.usecase.system.ObserveSyncStatusUseCase
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.features.common.presentation.BaseViewModel
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.features.common.presentation.UIEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MemberProfileViewModel @Inject constructor(
    private val memberContextCacheService: MemberGroupContextCacheService,
    private val observeMemberDocuments: ObserveMemberDocumentsUseCase,
    private val updateMemberBankingDetails: UpdateMemberBankingDetailsUseCase,
    private val uploadMemberDocument: UploadMemberDocumentUseCase,
    private val uploadRelationalDocumentUseCase: UploadRelationalDocumentUseCase,
    private val observeSyncStatus: ObserveSyncStatusUseCase,
    private val getAttributableActorId: GetAttributableActorIdUseCase,
    private val isBiometricEnabled: IsBiometricEnabledUseCase,
    private val setBiometricEnabled: SetBiometricEnabledUseCase,
    private val buildDocumentAuthHeaders: BuildDocumentAuthHeadersUseCase
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    init {
        observeContext()
        updateState { it.copy(biometricEnabled = isBiometricEnabled()) }
        launchJob("sync_status_observation") {
            observeSyncStatus().collect { status -> updateState { it.copy(syncStatus = status) } }
        }
    }

    private fun observeContext() {
        launchJob("context_observation") {
            combine(memberContextCacheService.contexts, uiState.map { it.selectedGroupId }.distinctUntilChanged()) { contexts, id -> contexts[id] }
                .collect { context ->
                    if ((context == null) || uiState.value.isImpersonating) return@collect
                    updateState { it.copy(member = context.member, group = context.group) }
                    context.member?.id?.let { observeDocuments(it) }
                }
        }
    }

    fun setImpersonationContext(member: Member?, group: Group?) {
        updateState { it.copy(member = member, group = group, isImpersonating = member != null) }
        member?.id?.let { observeDocuments(it) }
    }

    private fun observeDocuments(memberId: String) {
        launchJob("documents_observation") {
            observeMemberDocuments(memberId).collect { res ->
                res.onSuccess { docs -> updateState { it.copy(documents = docs) } }
            }
        }
    }

    fun updateBankingDetails(bank: String, account: String, branch: String) {
        val memberId = uiState.value.member?.let { it.id.serverIdOrReport("member") } ?: return
        launchJob("update_banking") {
            updateState { it.copy(isLoading = true) }
            updateMemberBankingDetails(memberId, bank, account, branch).onSuccess {
                updateState { s -> s.copy(isLoading = false) }
                sendEvent(UIEvent.ShowMessage("Banking details updated"))
                // The session can end before this callback runs; the warm-up is best-effort.
                getAttributableActorId()?.let { memberContextCacheService.warmUpForUser(it) }
            }.onFailure {
                updateState { s -> s.copy(isLoading = false) }
                handleError(it)
            }
        }
    }

    fun uploadDocument(index: Int, data: ByteArray, name: String, mime: String) {
        val memberId = uiState.value.member?.let { it.id.serverIdOrReport("member") } ?: return
        launchJob("upload_doc_$index") {
            updateState { it.copy(isUploading = true) }
            uploadMemberDocument(memberId, index, data, name, mime).onSuccess { url ->
                val updatedMember = when (index) {
                    0 -> uiState.value.member?.copy(profilePhotoUrl = url)
                    1 -> uiState.value.member?.copy(document1Url = url, document1Type = mime)
                    2 -> uiState.value.member?.copy(document2Url = url, document2Type = mime)
                    3 -> uiState.value.member?.copy(document3Url = url, document3Type = mime)
                    4 -> uiState.value.member?.copy(document4Url = url, document4Type = mime)
                    5 -> uiState.value.member?.copy(document5Url = url, document5Type = mime)
                    else -> uiState.value.member
                }
                updateState {
                    it.copy(
                        isUploading = false,
                        member = updatedMember,
                        profileImageVersion = if (index == 0) it.profileImageVersion + 1 else it.profileImageVersion
                    )
                }
                showMessage("Document uploaded successfully")
            }.onFailure {
                updateState { it.copy(isUploading = false) }
                handleError(it)
            }
        }
    }

    fun uploadRelationalDocument(label: String, data: ByteArray, fileName: String, mime: String) {
        val memberId = uiState.value.member?.let { it.id.serverIdOrReport("member") } ?: return
        val groupId = uiState.value.selectedGroupId ?: return
        launchJob("upload_relational_doc_$label") {
            updateState { it.copy(isUploading = true) }
            uploadRelationalDocumentUseCase(memberId, groupId, label, data, fileName, mime).onSuccess {
                updateState { it.copy(isUploading = false) }
                sendEvent(UIEvent.ShowMessage("'$label' uploaded successfully"))
            }.onFailure {
                updateState { it.copy(isUploading = false) }
                handleError(it)
            }
        }
    }

    fun getDownloadParams(url: String): Map<String, String> = buildDocumentAuthHeaders(url)

    fun toggleBiometric(enabled: Boolean) {
        setBiometricEnabled(enabled)
        updateState { it.copy(biometricEnabled = enabled) }
    }
}
