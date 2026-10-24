package com.sanibonani.save.data.repository.payment
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.payment.PaymentInitiationResult
import com.sanibonani.save.domain.repository.payment.PaymentStatusResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import java.util.UUID
import com.sanibonani.save.domain.utils.safeRunCatching

class PayFastPaymentProvider(
    private val httpClient: HttpClient
) : PaymentGatewayProvider {

    private val merchantId = com.sanibonani.save.data.BuildConfig.PAYFAST_MERCHANT_ID
    private val merchantKey = com.sanibonani.save.data.BuildConfig.PAYFAST_MERCHANT_KEY
    private val baseUrl = com.sanibonani.save.data.BuildConfig.PAYFAST_URL
    private val queryUrl = com.sanibonani.save.data.BuildConfig.PAYFAST_QUERY_URL

    override suspend fun initiate(
        method: PaymentMethod,
        type: PaymentType,
        amount: Money,
        groupId: String,
        memberId: String?,
        description: String?
    ): Result<PaymentInitiationResult> = safeRunCatching {
        val txId = "pf_${UUID.randomUUID().toString().take(8)}"

        // PayFast usually redirects via form post, so here we return a web intent URL
        // that our app's WebView can intercept and POST.
        val checkoutUrl = buildString {
            append(baseUrl)
            append("?merchant_id=").append(merchantId)
            append("&merchant_key=").append(merchantKey)
            append("&return_url=sanibonani://payment/success")
            append("&cancel_url=sanibonani://payment/cancel")
            append("&notify_url=https://api.sanibonani.com/webhooks/payfast")
            append("&m_payment_id=").append(txId)
            append("&amount=").append(amount.toPlainString())
            append("&item_name=").append(java.net.URLEncoder.encode(description ?: "Group $groupId Payment", "UTF-8"))
        }

        PaymentInitiationResult(
            checkoutUrl = checkoutUrl,
            transactionId = txId,
            method = method
        )
    }

    override suspend fun verify(transactionId: String, method: PaymentMethod): Result<PaymentStatusResult> = safeRunCatching {
        // Query PayFast API to verify the transaction
        val response = httpClient.get(queryUrl) {
            url {
                parameters.append("merchant_id", merchantId)
                parameters.append("merchant_key", merchantKey)
                parameters.append("m_payment_id", transactionId)
            }
        }

        val responseText = response.bodyAsText()
        val isSuccessful = response.status.value in 200..299 && responseText.contains("COMPLETE")

        // Parse amount_gross if available in response
        val parsedAmount = Regex("amount_gross=([0-9.]+)")
            .find(responseText)?.groupValues?.get(1)
            ?.let { safeRunCatching { Money(it) }.getOrNull() } ?: Money.ZERO

        PaymentStatusResult(
            transactionId = transactionId,
            isSuccessful = isSuccessful,
            amount = parsedAmount,
            rawStatus = if (!isSuccessful) "Payment was not completed" else null
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
        // PayFast API implementation for Payouts would go here
        "PAYFAST-OUT-${UUID.randomUUID().toString().take(6).uppercase()}"
    }
}
