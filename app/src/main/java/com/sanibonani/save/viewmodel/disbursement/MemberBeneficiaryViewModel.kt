package com.sanibonani.save.viewmodel.disbursement

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.disbursement.BeneficiaryClaimRepository
import com.sanibonani.save.domain.usecase.disbursement.ValidateBurialClaimEligibilityUseCase
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.MemberUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberBeneficiaryViewModel @Inject constructor(
    private val memberContextCacheService: MemberGroupContextCacheService,
    private val beneficiaryRepo: BeneficiaryRepository,
    private val claimRepo: BeneficiaryClaimRepository,
    private val storageRepo: StorageRepository,
    private val validateBurialClaimEligibilityUseCase: ValidateBurialClaimEligibilityUseCase
) : BaseViewModel<MemberUiState>(MemberUiState()) {

    val state: StateFlow<MemberUiState> = uiState
    private var contextObservationJob: Job? = null

    init {
        observeContext()
    }

    private fun observeContext() {
        contextObservationJob?.cancel()
        contextObservationJob = viewModelScope.launch {
            combine(memberContextCacheService.contexts, uiState.map { it.selectedGroupId }.distinctUntilChanged()) { contexts, id -> contexts[id] }
                .collect { context ->
                    if (context == null || uiState.value.isImpersonating) return@collect
                    updateState { it.copy(member = context.member, group = context.group, beneficiaries = context.beneficiaries) }
                }
        }
    }

    fun setImpersonationContext(member: Member?, group: Group?) {
        updateState { it.copy(member = member, group = group, isImpersonating = member != null) }
    }

    fun addBeneficiary(name: String, idNumber: String, relationship: String, dob: String, isOver65: Boolean) {
        val member = uiState.value.member ?: return
        val group = uiState.value.group ?: return
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            val beneficiary = Beneficiary(groupId = group.id!!, memberId = member.id!!, fullName = name, idNumber = idNumber, relationship = relationship, dateOfBirth = dob, isOver65 = isOver65)
            beneficiaryRepo.addBeneficiary(beneficiary).onSuccess { updateState { it.copy(isLoading = false) } }
        }
    }

    fun checkEligibility(cause: String, date: String): ValidateBurialClaimEligibilityUseCase.EligibilityResult {
        val s = uiState.value
        return validateBurialClaimEligibilityUseCase(s.member!!, s.group!!, cause, date, s.paymentCalculation?.shortfall ?: Money.ZERO)
    }

    fun submitClaim(beneficiary: Beneficiary, amount: Money, cause: String, date: String, bid: String, dc: String, mid: String) {
        val m = uiState.value.member ?: return
        val g = uiState.value.group ?: return
        viewModelScope.launch {
            updateState { it.copy(isSubmittingClaim = true) }
            val claim = BeneficiaryPayoutClaim(groupId = g.id!!, memberId = m.id!!, beneficiaryId = beneficiary.id!!, beneficiaryName = beneficiary.fullName, claimAmount = amount, causeOfDeath = cause, dateOfDeath = date, beneficiaryIdUrl = bid, deathCertificateUrl = dc, memberIdUrl = mid, status = BeneficiaryClaimStatus.SUBMITTED)
            claimRepo.submitClaim(claim).onSuccess { updateState { it.copy(isSubmittingClaim = false, claimSubmitSuccess = true) } }
        }
    }
}
