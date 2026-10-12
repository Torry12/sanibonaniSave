package com.sanibonani.save.data.repository.payment
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.payment.PaymentInitiationResult
import com.sanibonani.save.domain.repository.payment.PaymentStatusResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * Payment adapter layer for future real bank API integration.
 * Currently uses Supabase Edge Functions as the integration boundary for sandbox testing.
 */
class SupabaseEdgeBankAdapter @Inject constructor(
    private val supabase: SupabaseClient,
    private val json: Json,
) : PaymentGatewayProvider {

    override suspend fun initiate(
        method: PaymentMethod,
        type: PaymentType,
        amount: Money,
        groupId: String,
        memberId: String?,
        description: String?,
    ): Result<PaymentInitiationResult> = runCatching {
        val payload = buildJsonObject {
            put("action", "initiate")
            put("amount", amount.amount.toPlainString())
            put("groupId", groupId)
            put("memberId", memberId ?: "")
            put("description", description ?: "Bank Transfer")
        }

        val responseHttp = supabase.functions.invoke(
            "sandbox-bank-api",
            body = payload,
        )
        val responseText = responseHttp.bodyAsText()
        val response = runCatching { json.parseToJsonElement(responseText) as? JsonObject }
            .getOrNull() ?: throw Exception("Invalid response from bank API: $responseText")

        val txId = response["transactionId"]?.jsonPrimitive?.content ?: throw Exception("Missing transactionId in response")
        val checkoutUrl = response["checkoutUrl"]?.jsonPrimitive?.content ?: "sanibonani://bank/pay?tx=$txId"

        PaymentInitiationResult(
            checkoutUrl = checkoutUrl,
            transactionId = txId,
            method = method,
        )
    }

    override suspend fun verify(transactionId: String, method: PaymentMethod): Result<PaymentStatusResult> = runCatching {
        val payload = buildJsonObject {
            put("action", "verify")
            put("transactionId", transactionId)
        }

        val responseHttp = supabase.functions.invoke(
            "sandbox-bank-api",
            body = payload,
        )
        val responseText = responseHttp.bodyAsText()
        val response = runCatching { json.parseToJsonElement(responseText) as? JsonObject }
            .getOrNull() ?: throw Exception("Invalid response from bank API: $responseText")

        val status = response["status"]?.jsonPrimitive?.content
        val isSuccessful = status == "completed" || status == "success"
        val amountStr = response["amount"]?.jsonPrimitive?.content ?: "0.00"

        PaymentStatusResult(
            transactionId = transactionId,
            isSuccessful = isSuccessful,
            amount = Money(amountStr),
            rawStatus = response.toString(),
        )
    }

    override suspend fun disburse(
        payoutId: String,
        amount: Money,
        bankName: String,
        accountNo: String,
        branchCode: String,
        reference: String
    ): Result<String> = runCatching {
        // Forward disbursement via Edge Function Gateway
        val payload = buildJsonObject {
            put("payout_id", payoutId)
            put("amount", amount.amount.toPlainString())
            put("bank_name", bankName)
            put("account_no", accountNo)
            put("branch_code", branchCode)
            put("reference", reference)
        }
        val responseHttp = supabase.functions.invoke(
            "sandbox-bank-api",
            body = buildJsonObject {
                put("action", "disburse")
                put("payload", payload)
            }
        )
        val response = runCatching { json.parseToJsonElement(responseHttp.bodyAsText()) as? JsonObject }
            .getOrNull() ?: throw Exception("Invalid response from bank API")

        response["transaction_id"]?.jsonPrimitive?.content ?: "EDGE-OUT-${UUID.randomUUID().toString().take(6).uppercase()}"
    }
}
