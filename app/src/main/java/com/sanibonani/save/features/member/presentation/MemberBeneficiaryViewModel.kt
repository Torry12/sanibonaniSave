package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.disbursement.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.equity.BeneficiaryRepository
import com.sanibonani.save.domain.repository.equity.MemberRepository
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberBeneficiaryViewModel @Inject constructor(
    private val beneficiaryRepo: BeneficiaryRepository,
    private val claimRepo: BeneficiaryClaimRepository,
    private val memberRepo: MemberRepository
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState

    fun setImpersonationContext(member: Member?, group: Group?) {
        updateState { it.copy(member = member, group = group, isImpersonating = member != null) }
    }

    fun addBeneficiary(name: String, idNumber: String, relationship: String, dateOfBirth: String, isImmediate: Boolean) {
        val member = uiState.value.member ?: return
        launchJob("add_beneficiary") {
            updateState { it.copy(isLoading = true) }
            val beneficiary = Beneficiary(
                memberId = member.id!!,
                fullName = name,
                idNumber = idNumber,
                relationship = relationship
            )
            beneficiaryRepo.addBeneficiary(beneficiary).onSuccess {
                updateState { it.copy(isLoading = false) }
                showMessage("Beneficiary added successfully")
            }.onFailure {
                updateState { it.copy(isLoading = false) }
                handleError(it)
            }
        }
    }

    fun submitClaim(claim: BeneficiaryPayoutClaim) {
        launchJob("submit_claim") {
            updateState { it.copy(isSubmittingClaim = true) }
            claimRepo.submitClaim(claim).onSuccess {
                updateState { it.copy(isSubmittingClaim = false, claimSubmitSuccess = true) }
                showMessage("Claim submitted successfully")
            }.onFailure {
                updateState { it.copy(isSubmittingClaim = false) }
                handleError(it)
            }
        }
    }

    fun clearClaimState() {
        updateState { it.copy(claimSubmitSuccess = false, isSubmittingClaim = false) }
    }

    fun checkEligibility(beneficiaryId: String) {
        // Implementation for eligibility check if needed
    }

    fun uploadClaimDocument(claimId: String, index: Int, data: ByteArray, name: String, mime: String) {
        // Implementation for document upload
    }
}
