package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.service.FileStorageService
import com.sanibonani.save.domain.usecase.member.AddBeneficiaryUseCase
import com.sanibonani.save.domain.usecase.member.AttachBeneficiaryDocumentUseCase
import com.sanibonani.save.domain.usecase.member.RemoveBeneficiaryUseCase
import com.sanibonani.save.domain.usecase.member.SubmitBurialClaimUseCase
import com.sanibonani.save.domain.usecase.member.UpdateBeneficiaryUseCase
import com.sanibonani.save.domain.usecase.member.ValidateBurialClaimEligibilityUseCase
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.common.validation.BeneficiaryForm
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MemberBeneficiaryViewModel @Inject constructor(
    private val addBeneficiaryUseCase: AddBeneficiaryUseCase,
    private val updateBeneficiaryUseCase: UpdateBeneficiaryUseCase,
    private val removeBeneficiary: RemoveBeneficiaryUseCase,
    private val attachBeneficiaryDocument: AttachBeneficiaryDocumentUseCase,
    private val submitBurialClaim: SubmitBurialClaimUseCase,
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

        launchJob("add_beneficiary") {
            updateState { it.copy(isLoading = true) }
            addBeneficiaryUseCase(record).onSuccess {
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
            updateBeneficiaryUseCase(record)
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
        launchJob("delete_beneficiary") {
            updateState { it.copy(isLoading = true) }
            removeBeneficiary(beneficiary, uiState.value.member, uiState.value.group)
                .onSuccess {
                    updateState { it.copy(isLoading = false) }
                    showMessage("Beneficiary removed")
                }.onFailure {
                    updateState { it.copy(isLoading = false) }
                    handleError(it)
                }
        }
    }

    fun submitClaim(claim: BeneficiaryPayoutClaim) {
        launchJob("submit_claim") {
            updateState { it.copy(isSubmittingClaim = true) }
            submitBurialClaim(claim).onSuccess {
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
            attachBeneficiaryDocument(uiState.value.beneficiaries, memberId, beneficiaryId, fileName, data)
                .onSuccess {
                    updateState { it.copy(isLoading = false) }
                    showMessage("Document uploaded successfully")
                }.onFailure { error ->
                    updateState { it.copy(isLoading = false) }
                    handleError(error)
                }
        }
    }
}
