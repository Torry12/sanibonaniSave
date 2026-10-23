package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.service.FileStorageService
import com.sanibonani.save.domain.usecase.member.ValidateBurialClaimEligibilityUseCase
import com.sanibonani.save.domain.validation.InputValidator
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.features.common.presentation.UIEvent
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
        val cleanedName = name.trim()
        if (cleanedName.isBlank()) {
            sendEvent(UIEvent.ShowError("Add the beneficiary's full name before saving."))
            return
        }

        // A beneficiary is the record a future burial or emergency claim is settled against, so
        // the same brain that checks a member's ID checks this one before anything is queued.
        val cleanedId = idNumber.trim()
        val idError = when {
            cleanedId.isBlank() -> "Add the beneficiary's SA ID number before saving."
            InputValidator.validateSAIdNumber(cleanedId) !is ValidationResult.Valid ->
                "Add a valid 13-digit SA ID number for the beneficiary."
            else -> null
        }
        if (idError != null) {
            sendEvent(UIEvent.ShowError(idError))
            return
        }

        // beneficiaries.group_id is a NOT NULL uuid with a FK to groups, so the model default of
        // "" is not an omission the server fills in - PostgREST rejects it and the row never lands.
        val groupId = member.groupId.ifBlank { uiState.value.group?.id.orEmpty() }
        if (groupId.isBlank()) {
            sendEvent(UIEvent.ShowError("No group is loaded for this member, so the beneficiary cannot be saved. Sync and try again."))
            return
        }

        launchJob("add_beneficiary") {
            updateState { it.copy(isLoading = true) }
            val beneficiary = Beneficiary(
                groupId = groupId,
                memberId = memberId,
                fullName = cleanedName,
                idNumber = cleanedId,
                relationship = relationship.trim().ifBlank { null },
                dateOfBirth = dateOfBirth.trim().ifBlank { null },
                isOver65 = isOver65
            )
            beneficiaryRepo.addBeneficiary(beneficiary).onSuccess {
                updateState { s -> s.copy(isLoading = false) }
                showMessage("Beneficiary added successfully")
            }.onFailure {
                updateState { s -> s.copy(isLoading = false) }
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
                val b = currentBeneficiaries.find { it.id == beneficiaryId }
                if (b != null) {
                    val updated = b.copy(documentUrl = url)
                    beneficiaryRepo.updateBeneficiary(updated)
                }
                updateState { it.copy(isLoading = false) }
                showMessage("Document uploaded successfully")
            }.onFailure { error ->
                updateState { it.copy(isLoading = false) }
                handleError(error)
            }
        }
    }
}
