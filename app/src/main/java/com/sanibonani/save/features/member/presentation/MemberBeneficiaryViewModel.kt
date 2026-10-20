package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.FileStorageService
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
    private val memberRepo: MemberRepository,
    private val fileStorageService: FileStorageService
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    fun setImpersonationContext(member: Member?, group: Group?) {
        updateState { it.copy(member = member, group = group, isImpersonating = member != null) }
    }

    fun addBeneficiary(name: String, idNumber: String, relationship: String, dateOfBirth: String, isImmediate: Boolean) {
        val memberId = uiState.value.member?.let { it.id.serverIdOrReport("member") } ?: return
        launchJob("add_beneficiary") {
            updateState { it.copy(isLoading = true) }
            val beneficiary = Beneficiary(
                memberId = memberId,
                fullName = name,
                idNumber = idNumber,
                relationship = relationship
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

    fun checkEligibility(cause: String, date: String): com.sanibonani.save.domain.usecase.member.ValidateBurialClaimEligibilityUseCase.EligibilityResult? {
        val member = uiState.value.member ?: return null
        val group = uiState.value.group ?: return null
        val useCase = com.sanibonani.save.domain.usecase.member.ValidateBurialClaimEligibilityUseCase()
        return useCase(member, group, cause, date)
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
