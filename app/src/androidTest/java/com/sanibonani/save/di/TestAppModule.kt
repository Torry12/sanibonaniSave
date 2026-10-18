package com.sanibonani.save.di

import android.content.Context
import androidx.room.Room
import com.sanibonani.save.data.local.AuditDao
import com.sanibonani.save.data.local.BeneficiaryClaimDao
import com.sanibonani.save.data.local.BeneficiaryDao
import com.sanibonani.save.data.local.ContributionDao
import com.sanibonani.save.data.local.GroupDao
import com.sanibonani.save.data.local.LedgerDao
import com.sanibonani.save.data.local.LoanDao
import com.sanibonani.save.data.local.MemberDao
import com.sanibonani.save.data.local.MemberDocumentDao
import com.sanibonani.save.data.local.NotificationDao
import com.sanibonani.save.data.local.PaymentDao
import com.sanibonani.save.data.local.PayoutDao
import com.sanibonani.save.data.local.PendingOperationDao
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.Money
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.util.UUID
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object TestAppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SanibonaniDatabase {
        return TestDataStore.dbInstance ?: Room.inMemoryDatabaseBuilder(
            context,
            SanibonaniDatabase::class.java
        )
        .allowMainThreadQueries()
        .build().also { TestDataStore.dbInstance = it }
    }

    @Provides
    @javax.inject.Named("geoapify_api_key")
    fun provideGeoapifyApiKey(): String = "mock-key"

    @Provides @Singleton fun provideGroupDao(db: SanibonaniDatabase): GroupDao = db.groupDao()
    @Provides @Singleton fun provideMemberDao(db: SanibonaniDatabase): MemberDao = db.memberDao()
    @Provides @Singleton fun provideContributionDao(db: SanibonaniDatabase): ContributionDao = db.contributionDao()
    @Provides @Singleton fun providePaymentDao(db: SanibonaniDatabase): PaymentDao = db.paymentDao()
    @Provides @Singleton fun provideBeneficiaryDao(db: SanibonaniDatabase): BeneficiaryDao = db.beneficiaryDao()
    @Provides @Singleton fun provideNotificationDao(db: SanibonaniDatabase): NotificationDao = db.notificationDao()
    @Provides @Singleton fun providePayoutDao(db: SanibonaniDatabase): PayoutDao = db.payoutDao()
    @Provides @Singleton fun provideMemberDocumentDao(db: SanibonaniDatabase): MemberDocumentDao = db.memberDocumentDao()
    @Provides @Singleton fun provideAuditDao(db: SanibonaniDatabase): AuditDao = db.auditDao()
    @Provides @Singleton fun provideLoanDao(db: SanibonaniDatabase): LoanDao = db.loanDao()
    @Provides @Singleton fun provideLedgerDao(db: SanibonaniDatabase): LedgerDao = db.ledgerDao()
    @Provides @Singleton fun provideBeneficiaryClaimDao(db: SanibonaniDatabase): BeneficiaryClaimDao = db.beneficiaryClaimDao()
    @Provides @Singleton fun providePendingOperationDao(db: SanibonaniDatabase): PendingOperationDao = db.pendingOperationDao()

    @Provides
    @Singleton
    fun provideMockEngine(): MockEngine = MockEngine { request ->
        val path = request.url.encodedPath
        val method = request.method.value
        val params = request.url.parameters
        val bodyText = (request.body as? TextContent)?.text ?: "{}"
        val bodyJson = try { Json.parseToJsonElement(bodyText).jsonObject } catch (_: Exception) { buildJsonObject { } }

        when {
            path.contains("/rest/v1/rpc/record_contribution_") -> {
                handleRecordContribution(bodyJson)
            }

            path.contains("/rest/v1/rpc/process_complex_payment_v4") -> {
                handleComplexPayment(bodyJson)
            }

            path.contains("/rest/v1/rpc/increment_group_balance") -> {
                handleIncrementGroupBalance(bodyJson)
            }

            path.contains("/rest/v1/rpc/complete_payout_v") -> {
                handleCompletePayout(bodyJson)
            }

            path.contains("/rest/v1/rpc/approve_and_disburse_loan_v") -> {
                handleApproveLoan(bodyJson)
            }

            path.contains("/rest/v1/rpc/record_loan_repayment_v") -> {
                handleLoanRepayment(bodyJson)
            }

            path.contains("/rest/v1/rpc/audit_and_fix_group_balance_v") -> {
                handleAuditBalance(bodyJson)
            }

            path.contains("/rest/v1/rpc/pay_burial_claim_v") -> {
                handlePayBurialClaim(bodyJson)
            }

            path.contains("/rest/v1/") -> {
                handleRestV1(path, method, params, bodyText)
            }

            else -> respond("{}", HttpStatusCode.OK)
        }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handleRecordContribution(bodyJson: JsonObject) = respondJson {
        val memberId = bodyJson["p_member_id"]?.jsonPrimitive?.content ?: ""
        val groupId = bodyJson["p_group_id"]?.jsonPrimitive?.content ?: ""
        val amount = Money(bodyJson["p_amount"]?.jsonPrimitive?.content ?: "0.00")
        val id = UUID.randomUUID().toString()
        val contribution = buildJsonObject {
            put("id", id)
            put("member_id", memberId)
            put("group_id", groupId)
            put("amount", TestDataStore.buildMoney(amount))
            put("status", "paid")
            put("created_at", "2026-08-20T00:00:00Z")
        }
        TestDataStore.mockContributions[id] = contribution
        updateGroupBalance(groupId, amount)
        updateMemberBalance(memberId, amount)
        buildJsonArray { add(contribution) }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handleComplexPayment(bodyJson: JsonObject) = respondJson {
        val memberId = bodyJson["p_member_id"]?.jsonPrimitive?.content ?: ""
        val groupId = bodyJson["p_group_id"]?.jsonPrimitive?.content ?: ""
        val amount = Money(bodyJson["p_total_amount"]?.jsonPrimitive?.content ?: "0.00")
        updateGroupBalance(groupId, amount)
        updateMemberBalance(memberId, amount)
        val newBalance = TestDataStore.getMoney(TestDataStore.mockGroups[groupId]?.get("balance"))
        buildJsonArray {
            add(buildJsonObject {
            put("new_balance", TestDataStore.buildMoney(newBalance))
            put("balance", TestDataStore.buildMoney(newBalance))
            })
        }
    }

    private fun updateMemberBalance(memberId: String, amount: Money) {
        TestDataStore.mockMembers[memberId]?.let { m ->
            val cur = TestDataStore.getMoney(m["total_paid"])
            val curContribCount = m["total_contributions"]?.jsonPrimitive?.intOrNull ?: 0
            TestDataStore.mockMembers[memberId] = buildJsonObject {
                m.forEach { (k, v) -> put(k, v) }
                put("total_paid", TestDataStore.buildMoney(cur + amount))
                put("total_contributions", curContribCount + 1)
            }
        }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handleIncrementGroupBalance(bodyJson: JsonObject) = respondJson {
        val groupId = bodyJson["p_group_id"]?.jsonPrimitive?.content ?: ""
        val amount = Money(bodyJson["p_amount"]?.jsonPrimitive?.content ?: "0.00")
        updateGroupBalance(groupId, amount)
        val newBalance = TestDataStore.getMoney(TestDataStore.mockGroups[groupId]?.get("balance"))
        JsonPrimitive(newBalance.amount.toPlainString())
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handleCompletePayout(bodyJson: JsonObject) = respondJson {
        val payoutId = bodyJson["p_payout_id"]?.jsonPrimitive?.content ?: ""
        TestDataStore.mockPayouts[payoutId]?.let { p ->
            val groupId = p["group_id"]?.jsonPrimitive?.content ?: ""
            val amount = TestDataStore.getMoney(p["amount"])
            updateGroupBalance(groupId, -amount)
            TestDataStore.mockPayouts[payoutId] = buildJsonObject { p.forEach { (k, v) -> put(k, v) }; put("status", "completed") }
        }
        buildJsonObject { }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handleApproveLoan(bodyJson: JsonObject) = respondJson {
        val loanId = bodyJson["p_loan_id"]?.jsonPrimitive?.content ?: ""
        TestDataStore.mockLoans[loanId]?.let { l ->
            val groupId = l["group_id"]?.jsonPrimitive?.content ?: ""
            val amount = TestDataStore.getMoney(l["amount"])
            updateGroupBalance(groupId, -amount)
            TestDataStore.mockLoans[loanId] = buildJsonObject { l.forEach { (k, v) -> put(k, v) }; put("status", "active") }
        }
        buildJsonObject { }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handleLoanRepayment(bodyJson: JsonObject) = respondJson {
        val loanId = bodyJson["p_loan_id"]?.jsonPrimitive?.content ?: ""
        val amount = Money(bodyJson["p_amount"]?.jsonPrimitive?.content ?: "0.00")
        val repaymentId = UUID.randomUUID().toString()
        var newBalance = Money.ZERO
        val repaymentObj = TestDataStore.mockLoans[loanId]?.let { l ->
            val groupId = l["group_id"]?.jsonPrimitive?.content ?: ""
            val totalRepaid = TestDataStore.getMoney(l["total_repaid"]) + amount
            val totalToRepay = TestDataStore.getMoney(l["total_to_repay"])
            TestDataStore.mockLoans[loanId] = buildJsonObject {
                l.forEach { (k, v) -> put(k, v) }
                put("total_repaid", TestDataStore.buildMoney(totalRepaid))
                if (totalRepaid >= totalToRepay) put("status", "completed")
            }
            updateGroupBalance(groupId, amount)
            newBalance = TestDataStore.getMoney(TestDataStore.mockGroups[groupId]?.get("balance"))

            buildJsonObject {
                put("id", repaymentId)
                put("loan_id", loanId)
                put("amount", TestDataStore.buildMoney(amount))
                put("paid_at", "2026-08-20T00:00:00Z")
                put("member_id", l["member_id"] ?: JsonNull)
                put("group_id", groupId)
            }
        } ?: buildJsonObject { }

        buildJsonObject {
            put("repayment", repaymentObj)
            put("new_balance", TestDataStore.buildMoney(newBalance))
        }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handleAuditBalance(bodyJson: JsonObject) = respondJson {
        val groupId = bodyJson["p_group_id"]?.jsonPrimitive?.content ?: ""
        val group = TestDataStore.mockGroups[groupId]
        val balance = TestDataStore.getMoney(group?.get("balance"))

        // Either return a string (Money) or a JsonObject with ledger_sum
        TestDataStore.buildMoney(balance)
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handlePayBurialClaim(bodyJson: JsonObject) = respondJson {
        val claimId = bodyJson["p_claim_id"]?.jsonPrimitive?.content ?: ""
        TestDataStore.mockClaims[claimId]?.let { c ->
            val groupId = c["group_id"]?.jsonPrimitive?.content ?: ""
            val amount = TestDataStore.getMoney(c["claim_amount"])
            updateGroupBalance(groupId, -amount)
            TestDataStore.mockClaims[claimId] = buildJsonObject { c.forEach { (k, v) -> put(k, v) }; put("status", "paid") }
        }
        buildJsonObject { }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handleRestV1(path: String, method: String, params: io.ktor.http.Parameters, bodyText: String) = respondJson {
        val parts = path.split("/")
        val tableName = parts.getOrNull(parts.indexOf("v1") + 1)?.substringBefore("?") ?: ""
        val storage = getStorageForTable(tableName)

        when (method) {
            "GET" -> {
                var filtered = storage.values.toList()
                params.entries().forEach { entry ->
                    if (entry.key in listOf("select", "order", "limit", "offset")) return@forEach
                    val value = entry.value.firstOrNull()?.replace("eq.", "")?.replace("\"", "") ?: return@forEach
                    filtered = filtered.filter {
                        val fieldStr = when (val field = it[entry.key]) {
                            is JsonPrimitive -> field.content
                            else -> field?.toString()?.replace("\"", "")
                        }
                        fieldStr == value
                    }
                }
                if (filtered.isEmpty() && params.contains("id")) {
                    val id = params["id"]?.replace("eq.", "")?.replace("\"", "")
                    storage[id]?.let { filtered = listOf(it) }
                }
                JsonArray(filtered)
            }
            "POST", "PATCH" -> {
                val bodyElem = Json.parseToJsonElement(bodyText)
                val resultList = mutableListOf<JsonObject>()
                fun proc(obj: JsonObject): JsonObject {
                    val payloadId = obj["id"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val id = payloadId
                             ?: (if (method == "PATCH") params["id"]?.replace("eq.", "")?.replace("\"", "") else null)
                             ?: storage.keys.find { storage[it] == obj }
                             ?: UUID.randomUUID().toString()
                    val existing = storage[id]
                    val newObj = buildJsonObject {
                        existing?.forEach { (k, v) -> put(k, v) }
                        obj.forEach { (k, v) -> if (v !is JsonNull) put(k, v) }

                        val currentId = (obj["id"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                                        ?: existing?.get("id")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull })
                        if (currentId == null) put("id", id)

                        val createdAt = (obj["created_at"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                                        ?: existing?.get("created_at")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull })
                        if (createdAt == null) put("created_at", "2026-08-20T00:00:00Z")

                        if (tableName == "groups") {
                            if (obj["registration_paid"] == null && existing?.get("registration_paid") == null) put("registration_paid", false)
                            if (obj["fee_status"] == null && existing?.get("fee_status") == null) put("fee_status", "DUE")
                            if (obj["current_members"] == null && existing?.get("current_members") == null) put("current_members", 0)
                        }
                    }
                    storage[id] = newObj
                    return newObj
                }
                if (bodyElem is JsonArray) bodyElem.forEach { resultList.add(proc(it.jsonObject)) }
                else if (bodyElem is JsonObject) resultList.add(proc(bodyElem))

                if (method == "POST") JsonArray(resultList) else buildJsonObject { }
            }
            else -> buildJsonObject { }
        }
    }

    private fun getStorageForTable(tableName: String) = when (tableName) {
        "groups" -> TestDataStore.mockGroups
        "members" -> TestDataStore.mockMembers
        "contributions" -> TestDataStore.mockContributions
        "beneficiaries" -> TestDataStore.mockBeneficiaries
        "beneficiary_payout_claims" -> TestDataStore.mockClaims
        "payouts" -> TestDataStore.mockPayouts
        "loans" -> TestDataStore.mockLoans
        "loan_repayments" -> TestDataStore.mockLoanRepayments
        "profiles" -> TestDataStore.mockProfiles
        "group_polls" -> TestDataStore.mockPolls
        "group_poll_options" -> TestDataStore.mockPollOptions
        "group_poll_votes" -> TestDataStore.mockPollVotes
        else -> mutableMapOf()
    }

    private fun updateGroupBalance(groupId: String, delta: Money) {
        TestDataStore.mockGroups[groupId]?.let { g ->
            val cur = TestDataStore.getMoney(g["balance"])
            TestDataStore.mockGroups[groupId] = buildJsonObject {
                g.forEach { (k, v) -> put(k, v) }
                put("balance", TestDataStore.buildMoney(cur + delta))
            }
        }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(block: () -> JsonElement): io.ktor.client.request.HttpResponseData {
        return respond(
            content = block().toString(),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    @Provides
    @Singleton
    @OptIn(SupabaseInternal::class)
    fun provideSupabaseClient(json: Json, mockEngine: MockEngine): SupabaseClient {
        return createSupabaseClient("https://127.0.0.1", "mock-key") {
            httpEngine = mockEngine
            defaultSerializer = KotlinXSerializer(json)
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        decodeEnumsCaseInsensitive = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun providePolicyRouter(): com.sanibonani.save.domain.architecture.policy.PolicyRouter {
        return com.sanibonani.save.domain.architecture.policy.InMemoryPolicyRouter(
            rules = listOf(
                com.sanibonani.save.domain.architecture.policy.LargeAmountDualApprovalRule(threshold = Money("5000.0")),
                com.sanibonani.save.domain.architecture.policy.SuspendedGroupBlockRule()
            )
        )
    }
}
