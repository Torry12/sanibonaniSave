package com.sanibonani.save.domain.model

import kotlinx.serialization.*

/**
 * Represents a financial entity (Bank, Payment Gateway, Insurer)
 * that SanibonaniSave can interact with through a unified interface.
 */
@Serializable
data class FinancialProvider(
    val id: String,
    val name: String,
    val type: FinancialProviderType,
    val logoUrl: String? = null,
    val supportedOperations: List<FinancialOperationType> = emptyList(),
    val isActive: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class FinancialProviderType {
    @SerialName("bank")            BANK,
    @SerialName("payment_gateway") PAYMENT_GATEWAY,
    @SerialName("insurer")         INSURER,
    @SerialName("lender")          LENDER,
    @SerialName("investment")      INVESTMENT_MANAGER
}

@Serializable
enum class FinancialOperationType {
    @SerialName("get_balance")         GET_BALANCE,
    @SerialName("get_transactions")    GET_TRANSACTIONS,
    @SerialName("make_payment")        MAKE_PAYMENT,
    @SerialName("verify_account")      VERIFY_ACCOUNT,
    @SerialName("create_beneficiary")  CREATE_BENEFICIARY,
    @SerialName("request_statement")   REQUEST_STATEMENT,
    @SerialName("initiate_checkout")   INITIATE_CHECKOUT,
    @SerialName("process_payout")      PROCESS_PAYOUT,
    @SerialName("link_account")       LINK_ACCOUNT,
    @SerialName("unlink_account")     UNLINK_ACCOUNT
}

/**
 * Request payload for a financial operation.
 * Generic enough to support various provider-specific requirements via metadata.
 */
@Serializable
data class FinancialOperationRequest(
    val operation: FinancialOperationType,
    val providerId: String,
    val amount: Money? = null,
    val recipientId: String? = null,
    val groupId: String? = null,
    val reference: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Response from a unified financial operation.
 */
@Serializable
data class FinancialOperationResult(
    val success: Boolean,
    val transactionId: String? = null,
    val message: String? = null,
    val data: Map<String, String> = emptyMap(), // For balance, URL, etc.
    val errorCode: String? = null
)
