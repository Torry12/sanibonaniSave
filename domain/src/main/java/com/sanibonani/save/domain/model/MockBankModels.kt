package com.sanibonani.save.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MockBankTransaction(
    val id: String,
    val reference: String,
    val amount: Double,
    val type: PaymentType,
    val groupId: String,
    val memberId: String?,
    val direction: MockBankDirection,
    val status: PaymentStatus,
    val createdAt: String,
    val updatedAt: String,
    val failureReason: String? = null
)

@Serializable
enum class MockBankDirection {
    INBOUND,
    OUTBOUND
}
