package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.MockBankDirection
import com.sanibonani.save.domain.model.MockBankTransaction
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentStatus
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.MockBankRepository
import com.sanibonani.save.domain.repository.PaymentSandboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentSandboxUiState(
    val amount: String = "10.00",
    val selectedMethod: PaymentMethod = PaymentMethod.STITCH,
    val selectedType: PaymentType = PaymentType.CONTRIBUTION,
    val groupId: String = "test-group-id",
    val memberId: String = "test-member-id",
    val generatedUrl: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val verificationResult: Boolean? = null,
    val bankTransactions: List<MockBankTransaction> = emptyList(),
    val selectedDirection: MockBankDirection = MockBankDirection.INBOUND,
    val latestBankTransaction: MockBankTransaction? = null
)

@HiltViewModel
class PaymentSandboxViewModel @Inject constructor(
    private val sandboxRepository: PaymentSandboxRepository,
    private val mockBankRepository: MockBankRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentSandboxUiState())
    val state: StateFlow<PaymentSandboxUiState> = combine(
        _state.asStateFlow(),
        mockBankRepository.observeTransactions()
    ) { state, transactions ->
        state.copy(bankTransactions = transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentSandboxUiState())

    fun onAmountChange(amount: String) {
        _state.update { it.copy(amount = amount) }
    }

    fun onMethodChange(method: PaymentMethod) {
        _state.update { it.copy(selectedMethod = method) }
    }

    fun onDirectionChange(direction: MockBankDirection) {
        _state.update { it.copy(selectedDirection = direction) }
    }

    fun generateUrl() {
        val currentState = _state.value
        val amountDouble = currentState.amount.toDoubleOrNull() ?: 0.0
        
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, generatedUrl = null) }
            sandboxRepository.generateSandboxUrl(
                method = currentState.selectedMethod,
                type = currentState.selectedType,
                amount = amountDouble,
                groupId = currentState.groupId,
                memberId = currentState.memberId
            ).onSuccess { url ->
                _state.update { it.copy(generatedUrl = url, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun verifyPayment(transactionId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            sandboxRepository.verifySandboxPayment(transactionId, _state.value.selectedMethod)
                .onSuccess { result ->
                    _state.update { it.copy(verificationResult = result, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun createMockBankTransaction() {
        val currentState = _state.value
        val amountDouble = currentState.amount.toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, latestBankTransaction = null) }
            mockBankRepository.createTransaction(
                amount = amountDouble,
                type = currentState.selectedType,
                groupId = currentState.groupId,
                memberId = currentState.memberId.takeIf(String::isNotBlank),
                direction = currentState.selectedDirection
            ).onSuccess { transaction ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        latestBankTransaction = transaction,
                        selectedMethod = PaymentMethod.BANK,
                        generatedUrl = "mockbank://transaction/${transaction.reference}"
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun markBankTransactionCompleted(transactionId: String) {
        updateBankTransaction(transactionId, PaymentStatus.COMPLETED)
    }

    fun markBankTransactionFailed(transactionId: String) {
        updateBankTransaction(transactionId, PaymentStatus.FAILED, "Mock bank decline")
    }

    fun markBankTransactionProcessing(transactionId: String) {
        updateBankTransaction(transactionId, PaymentStatus.PROCESSING)
    }

    fun clearBankTransactions() {
        viewModelScope.launch {
            mockBankRepository.clearTransactions()
            _state.update { it.copy(latestBankTransaction = null, error = null) }
        }
    }

    private fun updateBankTransaction(
        transactionId: String,
        status: PaymentStatus,
        failureReason: String? = null
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            mockBankRepository.updateTransactionStatus(transactionId, status, failureReason)
                .onSuccess { transaction ->
                    _state.update { it.copy(isLoading = false, latestBankTransaction = transaction) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}
