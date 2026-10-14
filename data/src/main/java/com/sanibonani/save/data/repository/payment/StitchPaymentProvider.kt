package com.sanibonani.save.data.repository.payment
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.payment.PaymentInitiationResult
import com.sanibonani.save.domain.repository.payment.PaymentStatusResult
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.sanibonani.save.domain.utils.safeRunCatching

class StitchPaymentProvider(
    private val httpClient: HttpClient
) : PaymentGatewayProvider {

    private val stitchApiUrl = com.sanibonani.save.data.BuildConfig.STITCH_API_URL

    override suspend fun initiate(
        method: PaymentMethod,
        type: PaymentType,
        amount: Money,
        groupId: String,
        memberId: String?,
        description: String?
    ): Result<PaymentInitiationResult> = safeRunCatching {
        // Prepare a GraphQL mutation to Stitch API to create a payment request
        val mutation = """
            mutation CreatePaymentRequest(${'$'}amount: MoneyInput!, ${'$'}payerReference: String!, ${'$'}beneficiaryReference: String!) {
              clientPaymentAuthorizationRequestCreate(input: {
                amount: ${'$'}amount,
                payerReference: ${'$'}payerReference,
                beneficiaryReference: ${'$'}beneficiaryReference
              }) {
                authorizationRequestUrl
                id
              }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("amount", buildJsonObject {
                put("quantity", amount.amount.toPlainString())
                put("currency", "ZAR")
            })
            put("payerReference", memberId ?: "Group-$groupId")
            put("beneficiaryReference", description ?: "Sanibonani-$groupId")
        }

        val payload = buildJsonObject {
            put("query", mutation)
            put("variables", variables)
        }

        val response = httpClient.post(stitchApiUrl) {
            header("Content-Type", "application/json")
            // Authorization header would be attached via an interceptor
            setBody(payload.toString())
        }

        if (response.status.value !in 200..299) {
            throw Exception("Failed to initiate Stitch payment: ${response.status.description}")
        }

        val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = responseJson["data"]?.jsonObject?.get("clientPaymentAuthorizationRequestCreate")?.jsonObject
            ?: throw Exception("Invalid response from Stitch API")

        val authUrl = data["authorizationRequestUrl"]?.jsonPrimitive?.content ?: throw Exception("Missing auth URL")
        val txId = data["id"]?.jsonPrimitive?.content ?: throw Exception("Missing transaction ID")

        PaymentInitiationResult(
            checkoutUrl = authUrl,
            transactionId = txId,
            method = method
        )
    }

    override suspend fun verify(transactionId: String, method: PaymentMethod): Result<PaymentStatusResult> = safeRunCatching {
        val query = """
            query GetPaymentStatus(${'$'}id: ID!) {
              node(id: ${'$'}id) {
                ... on PaymentAuthorizationRequest {
                  status
                  amount {
                    quantity
                  }
                }
              }
            }
        """.trimIndent()

        val payload = buildJsonObject {
            put("query", query)
            put("variables", buildJsonObject { put("id", transactionId) })
        }

        val response = httpClient.post(stitchApiUrl) {
            header("Content-Type", "application/json")
            setBody(payload.toString())
        }

        val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val node = responseJson["data"]?.jsonObject?.get("node")?.jsonObject
            ?: throw Exception("Transaction not found on Stitch")

        val status = node["status"]?.jsonPrimitive?.content
        val quantityStr = node["amount"]?.jsonObject?.get("quantity")?.jsonPrimitive?.content ?: "0.00"

        PaymentStatusResult(
            transactionId = transactionId,
            isSuccessful = status == "Completed",
            amount = Money(quantityStr),
            rawStatus = if (status == "Failed") "Payment failed or cancelled by user" else status
        )
    }

    override suspend fun disburse(
        payoutId: String,
        amount: Money,
        bankName: String,
        accountNo: String,
        branchCode: String,
        reference: String
    ): Result<String> = safeRunCatching {
        // Stitch Payout/Disbursement GraphQL implementation would go here
        val mutation = """
            mutation CreateDisbursement(...) {
              clientDisbursementCreate(...) { ... }
            }
        """.trimIndent()
        // Simulate network call
        "STITCH-DISB-${UUID.randomUUID().toString().take(6).uppercase()}"
    }
}
