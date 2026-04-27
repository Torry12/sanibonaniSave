package com.sanibonani.save.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Interface for YoCo Payouts API.
 * Distributes funds from the platform account to group bank accounts.
 * Note: Requires YoCo secret key (not public key).
 */
interface YoCoPayoutService {
    @POST("v1/payouts")
    suspend fun createPayout(
        @Header("Authorization") bearerToken: String,
        @Body request: YoCoPayoutRequest
    ): YoCoPayoutResponse
}

@Serializable
data class YoCoPayoutRequest(
    val amount: Long, // in cents (e.g. R100 = 10000)
    val currency: String = "ZAR",
    val description: String,
    val metadata: Map<String, String>,
    val bankAccount: YoCoBankAccount
)

@Serializable
data class YoCoBankAccount(
    val bankName: String,
    val accountNumber: String,
    val branchCode: String,
    val accountHolderName: String,
    val accountType: String = "SAVINGS"
)

@Serializable
data class YoCoPayoutResponse(
    val id: String,
    val status: String,
    val amount: Long,
    val createdAt: String
)
