package com.sanibonani.save.domain.usecase.payment
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Finalizes a bank connection after the user returns from the provider's authorization flow.
 */
class FinalizeBankConnectionUseCase @Inject constructor(
    private val bankRepo: BankRepository,
    private val financialProviderRepo: FinancialProviderRepository
) {
    suspend operator fun invoke(
        groupId: String,
        connectionId: String,
        status: String,
        metadata: Map<String, String> = emptyMap()
    ): Result<Unit> = safeRunCatching {
        if (status != "success") {
            bankRepo.deleteBankConnection(groupId).getOrThrow()
            throw Exception("Bank linking was not successful (Status: $status)")
        }

        // Verify the connection with the provider
        val request = FinancialOperationRequest(
            operation = FinancialOperationType.VERIFY_ACCOUNT,
            providerId = metadata["provider_id"] ?: "",
            groupId = groupId,
            metadata = metadata + mapOf("connection_id" to connectionId)
        )

        val result = financialProviderRepo.executeOperation(request).getOrThrow()

        if (!result.success) {
            throw Exception(result.message ?: "Failed to verify bank connection with provider")
        }

        // Fetch current connection to update it
        val connection = bankRepo.observeBankConnection(groupId).first().getOrNull()
            ?: throw Exception("Pending connection not found for group")

        val updatedConnection = connection.copy(
            status = BankConnectionStatus.CONNECTED,
            accountName = result.data["account_name"],
            accountNumberHidden = result.data["account_number_hidden"]
        )

        bankRepo.saveBankConnection(updatedConnection).getOrThrow()
    }
}
