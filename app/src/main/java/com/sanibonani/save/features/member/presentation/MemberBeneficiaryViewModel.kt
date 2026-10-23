package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.service.FileStorageService
import com.sanibonani.save.domain.usecase.member.ValidateBurialClaimEligibilityUseCase
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.common.validation.BeneficiaryForm
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class MemberBeneficiaryViewModel @Inject constructor(
    private val beneficiaryRepo: BeneficiaryRepository,
    private val claimRepo: BeneficiaryClaimRepository,
    private val fileStorageService: FileStorageService,
    private val validateClaimEligibility: ValidateBurialClaimEligibilityUseCase
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    fun setImpersonationContext(member: Member?, group: Group?) {
        updateState { it.copy(member = member, group = group, isImpersonating = member != null) }
    }

    fun addBeneficiary(name: String, idNumber: String, relationship: String, dateOfBirth: String, isOver65: Boolean) {
        val member = uiState.value.member ?: return
        val memberId = member.id.serverIdOrReport("member") ?: return

        // beneficiaries.group_id is a NOT NULL uuid with a FK to groups, so the model default of
        // "" is not an omission the server fills in - PostgREST rejects it and the row never lands.
        val groupId = member.groupId.ifBlank { uiState.value.group?.id.orEmpty() }
        val record = when (val validation = BeneficiaryForm.validate(
            template = Beneficiary(groupId = groupId, memberId = memberId),
            name = name,
            idNumber = idNumber,
            relationship = relationship,
            dateOfBirth = dateOfBirth,
            isOver65 = isOver65
        )) {
            is BeneficiaryForm.Validation.Invalid -> {
                sendEvent(UIEvent.ShowError(validation.message))
                return
            }
            is BeneficiaryForm.Validation.Valid -> validation.record
        }
        if (groupId.isBlank()) {
            sendEvent(UIEvent.ShowError("No group is loaded for this member, so the beneficiary cannot be saved. Sync and try again."))
            return
        }

        launchJob("add_beneficiary") {
            updateState { it.copy(isLoading = true) }
            beneficiaryRepo.addBeneficiary(record).onSuccess {
                updateState { s -> s.copy(isLoading = false) }
                showMessage("Beneficiary added successfully")
            }.onFailure {
                updateState { s -> s.copy(isLoading = false) }
                handleError(it)
            }
        }
    }

    fun updateBeneficiary(
        beneficiary: Beneficiary,
        name: String,
        idNumber: String,
        relationship: String,
        dateOfBirth: String,
        isOver65: Boolean
    ) {
        val record = when (val validation = BeneficiaryForm.validate(
            template = beneficiary,
            name = name,
            idNumber = idNumber,
            relationship = relationship,
            dateOfBirth = dateOfBirth,
            isOver65 = isOver65
        )) {
            is BeneficiaryForm.Validation.Invalid -> {
                sendEvent(UIEvent.ShowError(validation.message))
                return
            }
            is BeneficiaryForm.Validation.Valid -> validation.record
        }

        launchJob("update_beneficiary") {
            updateState { it.copy(isLoading = true) }
            beneficiaryRepo.updateBeneficiary(record)
                .onSuccess {
                    updateState { it.copy(isLoading = false) }
                    showMessage("Beneficiary updated successfully")
                }.onFailure {
                    updateState { it.copy(isLoading = false) }
                    handleError(it)
                }
        }
    }

    fun deleteBeneficiary(beneficiary: Beneficiary) {
        val id = beneficiary.id
        if (id == null) {
            sendEvent(UIEvent.ShowError("This beneficiary is not saved on the server yet. Sync and try again."))
            return
        }
        val memberId = beneficiary.memberId.ifBlank { uiState.value.member?.id.orEmpty() }
        val groupId = beneficiary.groupId.ifBlank {
            uiState.value.member?.groupId?.ifBlank { null } ?: uiState.value.group?.id.orEmpty()
        }
        if (memberId.isBlank() || groupId.isBlank()) {
            sendEvent(UIEvent.ShowError("No member or group is loaded, so the beneficiary cannot be removed. Sync and try again."))
            return
        }

        launchJob("delete_beneficiary") {
            updateState { it.copy(isLoading = true) }
            beneficiaryRepo.deleteBeneficiary(groupId, memberId, id)
                .onSuccess {
                    updateState { it.copy(isLoading = false) }
                    showMessage("Beneficiary removed")
                }
                .onFailure {
                    updateState { it.copy(isLoading = false) }
                    handleError(it)
                }
        }
    }

    fun submitClaim(claim: BeneficiaryPayoutClaim) {
        launchJob("submit_claim") {
            updateState { it.copy(isSubmittingClaim = true) }
            claimRepo.submitClaim(claim).onSuccess {
                updateState { it.copy(isSubmittingClaim = false) }
                showMessage("Claim submitted successfully")
            }.onFailure {
                updateState { it.copy(isSubmittingClaim = false) }
                handleError(it)
            }
        }
    }

    fun clearClaimState() {
        updateState { it.copy(isSubmittingClaim = false) }
    }

    fun checkEligibility(cause: String, date: String): ValidateBurialClaimEligibilityUseCase.EligibilityResult? {
        val member = uiState.value.member ?: return null
        val group = uiState.value.group ?: return null
        return validateClaimEligibility(member, group, cause, date)
    }
    suspend fun uploadClaimDocument(fileName: String, data: ByteArray): Result<String> {
        val claimId = UUID.randomUUID().toString()
        val extension = fileName.substringAfterLast(".", "pdf")
        return fileStorageService.uploadClaimDocument(claimId, data, extension)
    }
    fun uploadBeneficiaryDocument(beneficiaryId: String, data: ByteArray, fileName: String) {
        val memberId = uiState.value.member?.let { it.id.serverIdOrReport("member") } ?: return
        launchJob("upload_beneficiary_doc_$beneficiaryId") {
            updateState { it.copy(isLoading = true) }
            val extension = fileName.substringAfterLast(".", "pdf")
            val result = fileStorageService.uploadBeneficiaryDocument(memberId, beneficiaryId, data, extension)

            result.onSuccess { url ->
                // Fetch current beneficiary to update its URL
                val currentBeneficiaries = uiState.value.beneficiaries.ifEmpty {
                    beneficiaryRepo.observeBeneficiaries(memberId).first().getOrNull() ?: emptyList()
                }
                val target = currentBeneficiaries.find { it.id == beneficiaryId }
                val persisted = target?.let { beneficiaryRepo.updateBeneficiary(it.copy(documentUrl = url)) }
                    ?: Result.failure(
                        IllegalStateException(
                            "The document uploaded, but the beneficiary could not be found. Sync and try again."
                        )
                    )
                persisted.onSuccess {
                    updateState { it.copy(isLoading = false) }
                    showMessage("Document uploaded successfully")
                }.onFailure { error ->
                    updateState { it.copy(isLoading = false) }
                    handleError(error)
                }
            }.onFailure { error ->
                updateState { it.copy(isLoading = false) }
                handleError(error)
            }
        }
    }
}
