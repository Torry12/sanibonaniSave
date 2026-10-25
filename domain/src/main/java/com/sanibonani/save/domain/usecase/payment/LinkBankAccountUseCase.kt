package com.sanibonani.save.domain.usecase.payment
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching
import java.util.UUID

/**
 * Orchestrates the process of linking a physical bank account to a savings group.
 * This typically involves redirecting the user to a financial provider's authorization page.
 */
class LinkBankAccountUseCase @Inject constructor(
    private val financialProviderRepo: FinancialProviderRepository,
    private val bankRepo: BankRepository
) {
    suspend operator fun invoke(
        groupId: String,
        providerId: String,
        metadata: Map<String, String> = emptyMap()
    ): Result<LinkAccountResult> = safeRunCatching {
        val request = FinancialOperationRequest(
            operation = FinancialOperationType.LINK_ACCOUNT,
            providerId = providerId,
            groupId = groupId,
            metadata = metadata
        )

        val result = financialProviderRepo.executeOperation(request).getOrThrow()

        if (!result.success) {
            throw Exception(result.message ?: "Failed to initiate bank account linking")
        }

        val authUrl = result.data["auth_url"] ?: throw Exception("Missing authorization URL from provider")
        val connectionId = result.data["connection_id"] ?: "pending_${UUID.randomUUID()}"

        // Create a pending connection record
        val pendingConnection = BankConnection(
            id = connectionId,
            groupId = groupId,
            providerId = providerId,
            status = BankConnectionStatus.PENDING,
            linkedAt = java.time.Instant.now().toString()
        )

        bankRepo.saveBankConnection(pendingConnection).getOrThrow()

        LinkAccountResult(
            authUrl = authUrl,
            connectionId = connectionId
        )
    }
}

data class LinkAccountResult(
    val authUrl: String,
    val connectionId: String
)
