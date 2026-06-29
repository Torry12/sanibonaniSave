package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.SendNotificationUseCase
import com.sanibonani.save.domain.usecase.ValidateBurialClaimEligibilityUseCase
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.domain.validation.ValidationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberClaimUiState(
    val burialClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val isSubmittingClaim: Boolean = false,
    val claimSubmitSuccess: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class MemberClaimViewModel @Inject constructor(
    private val claimRepo: BeneficiaryClaimRepository,
    private val validateBurialClaimEligibilityUseCase: ValidateBurialClaimEligibilityUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MemberClaimUiState())
    val state: StateFlow<MemberClaimUiState> = _state.asStateFlow()

    private var observationJob: Job? = null
    private var currentGroupId: String? = null
    private var currentMemberId: String? = null

    fun setContext(groupId: String, memberId: String) {
        if (currentGroupId == groupId && currentMemberId == memberId) return
        currentGroupId = groupId
        currentMemberId = memberId
        startObserving(groupId, memberId)
    }

    private fun startObserving(groupId: String, memberId: String) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            claimRepo.observeClaimsForMember(memberId, groupId).collect { res ->
                res.onSuccess { list ->
                    _state.update { it.copy(burialClaims = list, isLoading = false) }
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
            }
        }
    }

    fun submitBeneficiaryClaim(
        member: Member,
        group: Group,
        beneficiary: Beneficiary,
        causeOfDeath: String,
        dateOfDeath: String,
        amount: Double,
        bankName: String,
        accountNo: String,
        branchCode: String,
        accountHolder: String,
        notes: String?
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmittingClaim = true, error = null) }

            // Validate eligibility
            val eligibility = validateBurialClaimEligibilityUseCase(
                member = member,
                group = group,
                beneficiary = beneficiary,
                causeOfDeath = causeOfDeath,
                dateOfDeath = dateOfDeath
            )
            
            if (eligibility is ValidateBurialClaimEligibilityUseCase.EligibilityResult.Ineligible) {
                _state.update { it.copy(isSubmittingClaim = false, error = eligibility.reason) }
                return@launch
            }

            // Banking validation
            val bankingValidation = ValidationUtils.validateBankingDetails(
                bankName.ifBlank { group.bankName.orEmpty() },
                accountNo.ifBlank { group.accountNumber.orEmpty() },
                branchCode.ifBlank { group.branchCode.orEmpty() }
            )
            if (bankingValidation !is ValidationResult.Valid) {
                _state.update { it.copy(isSubmittingClaim = false, error = bankingValidation.getErrorMessage()) }
                return@launch
            }

            val claim = BeneficiaryPayoutClaim(
                groupId = group.id ?: "",
                memberId = member.id ?: "",
                beneficiaryId = beneficiary.id ?: "",
                beneficiaryName = beneficiary.fullName,
                causeOfDeath = causeOfDeath,
                dateOfDeath = dateOfDeath,
                claimAmount = amount,
                bankName = bankName,
                accountNo = accountNo,
                branchCode = branchCode,
                accountHolder = accountHolder,
                notes = notes,
                status = BeneficiaryClaimStatus.SUBMITTED
            )

            claimRepo.submitClaim(claim).onSuccess {
                _state.update { it.copy(isSubmittingClaim = false, claimSubmitSuccess = true) }
                sendNotificationUseCase(
                    groupId = group.id ?: "",
                    message = "NEW BURIAL CLAIM: Member ${member.fullName} for ${beneficiary.fullName}",
                    triggerEvent = NotifEvent.CUSTOM,
                    channel = NotifChannel.BOTH
                )
            }.onFailure { e ->
                _state.update { it.copy(isSubmittingClaim = false, error = e.toUserMessage()) }
            }
        }
    }

    fun dismissClaimSuccess() {
        _state.update { it.copy(claimSubmitSuccess = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
    }
}
