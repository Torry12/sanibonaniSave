package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberFinancialUiState(
    val contributions: List<Contribution> = emptyList(),
    val calculation: PaymentCalculation? = null,
    val selectedContribution: Contribution? = null,
    val isExporting: Boolean = false,
    val exportFile: java.io.File? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MemberFinancialViewModel @Inject constructor(
    private val memberRepo: MemberRepository,
    private val groupRepo: GroupRepository,
    private val exportRepo: ExportRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MemberFinancialUiState())
    val state: StateFlow<MemberFinancialUiState> = _state.asStateFlow()

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
            
            val memberFlow = memberRepo.observeMemberById(memberId)
            val groupFlow = groupRepo.observeGroup(groupId)
            val contribFlow = memberRepo.getMemberContributions(memberId, groupId)

            combine(memberFlow, groupFlow, contribFlow) { memberRes, groupRes, contribRes ->
                val member = memberRes.getOrNull()
                val group = groupRes.getOrNull()
                val contributions = contribRes.getOrElse { emptyList() }
                
                val calculation = if (member != null && group != null) {
                    PaymentCalculator.calculateStatus(group, member, contributions)
                } else null

                _state.update { it.copy(
                    contributions = contributions,
                    calculation = calculation,
                    isLoading = false,
                    error = memberRes.exceptionOrNull()?.toUserMessage() 
                        ?: groupRes.exceptionOrNull()?.toUserMessage()
                ) }
            }.collect()
        }
    }

    fun exportMyStatement(group: Group, member: Member, contributions: List<Contribution>) {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            exportRepo.exportContributionsToCsv(group, member, contributions)
                .onSuccess { file ->
                    _state.update { it.copy(isExporting = false, exportFile = file) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isExporting = false, error = error.toUserMessage()) }
                }
        }
    }

    fun downloadPdfStatement(group: Group, member: Member, contributions: List<Contribution>) {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            exportRepo.exportContributionsToPdf(group, member, contributions)
                .onSuccess { file ->
                    _state.update { it.copy(isExporting = false, exportFile = file) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isExporting = false, error = error.toUserMessage()) }
                }
        }
    }

    fun clearExportFile() {
        _state.update { it.copy(exportFile = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
    }
}
