package com.sanibonani.save.di

import android.content.Context
import androidx.room.Room
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.utils.AdminClient
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object TestAppModule {

    private val mockGroups = mutableMapOf<String, JsonObject>()
    private val mockMembers = mutableMapOf<String, JsonObject>()
    private val mockContributions = mutableMapOf<String, JsonObject>()
    private val mockBeneficiaries = mutableMapOf<String, JsonObject>()
    private val mockMemberDocuments = mutableMapOf<String, JsonObject>()
    private val mockPayouts = mutableMapOf<String, JsonObject>()

    @Provides
    @Singleton
    fun provideInMemoryDatabase(@ApplicationContext context: Context): SanibonaniDatabase =
        Room.inMemoryDatabaseBuilder(
            context,
            SanibonaniDatabase::class.java
        )
        .allowMainThreadQueries()
        .build()

    @Provides
    @Singleton
    fun provideGroupDao(db: SanibonaniDatabase) = db.groupDao()

    @Provides
    @Singleton
    fun provideMemberDao(db: SanibonaniDatabase) = db.memberDao()

    @Provides
    @Singleton
    fun provideContributionDao(db: SanibonaniDatabase) = db.contributionDao()

    @Provides
    @Singleton
    fun providePaymentDao(db: SanibonaniDatabase) = db.paymentDao()

    @Provides
    @Singleton
    fun provideBeneficiaryDao(db: SanibonaniDatabase) = db.beneficiaryDao()

    @Provides
    @Singleton
    fun provideNotificationDao(db: SanibonaniDatabase) = db.notificationDao()

    @Provides
    @Singleton
    fun providePayoutDao(db: SanibonaniDatabase) = db.payoutDao()

    @Provides
    @Singleton
    fun provideMemberDocumentDao(db: SanibonaniDatabase) = db.memberDocumentDao()

    @Provides
    @Singleton
    fun provideMockEngine(): MockEngine = MockEngine { request ->
        val url = request.url.toString()
        val path = request.url.encodedPath
        val method = request.method.value
        val queryParams = request.url.parameters

        when {
            // PostgREST: rpc record_contribution_v1
            path.contains("/rest/v1/rpc/record_contribution_v1") && method == "POST" -> {
                val bodyString = (request.body as? TextContent)?.text ?: ""
                val bodyJson = Json.parseToJsonElement(bodyString).jsonObject
                
                val memberId = bodyJson["p_member_id"]?.jsonPrimitive?.content ?: ""
                val groupId = bodyJson["p_group_id"]?.jsonPrimitive?.content ?: ""
                val amount = bodyJson["p_amount"]?.jsonPrimitive?.double ?: 0.0
                val type = bodyJson["p_type"]?.jsonPrimitive?.content ?: "contribution"
                val dueDate = bodyJson["p_due_date"]?.jsonPrimitive?.content
                val paidAt = bodyJson["p_paid_at"]?.jsonPrimitive?.content
                val txId = bodyJson["p_yoco_tx_id"]?.jsonPrimitive?.content

                val id = UUID.randomUUID().toString()
                val contribution = buildJsonObject {
                    put("id", id)
                    put("member_id", memberId)
                    put("group_id", groupId)
                    put("amount", amount)
                    put("status", "paid")
                    put("type", type)
                    put("due_date", dueDate ?: "")
                    put("paid_at", paidAt ?: "")
                    put("yoco_transaction_id", txId)
                    put("created_at", java.time.Instant.now().toString())
                }
                mockContributions[id] = contribution
                
                // Update member total_contributions count
                mockMembers[memberId]?.let { member ->
                    val currentCount = member["total_contributions"]?.jsonPrimitive?.int ?: 0
                    mockMembers[memberId] = buildJsonObject {
                        member.forEach { (k, v) -> put(k, v) }
                        put("total_contributions", currentCount + 1)
                    }
                }
                
                // Update group balance
                mockGroups[groupId]?.let { group ->
                    val currentBalance = group["balance"]?.jsonPrimitive?.double ?: 0.0
                    mockGroups[groupId] = buildJsonObject {
                        group.forEach { (k, v) -> put(k, v) }
                        put("balance", currentBalance + amount)
                    }
                }

                respond(
                    content = buildJsonArray { add(contribution) }.toString(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            path.contains("/rest/v1/") -> {
                val parts = path.split("/")
                val restIdx = parts.indexOf("v1")
                val tableName = if (restIdx != -1 && restIdx + 1 < parts.size) parts[restIdx + 1] else ""
                
                val storage: MutableMap<String, JsonObject> = when (tableName) {
                    "groups" -> mockGroups
                    "members" -> mockMembers
                    "contributions" -> mockContributions
                    "beneficiaries" -> mockBeneficiaries
                    "member_documents" -> mockMemberDocuments
                    "payouts" -> mockPayouts
                    "platform_fees" -> mutableMapOf() // Not tracked for now
                    else -> mutableMapOf()
                }

                when (method) {
                    "GET" -> {
                        var filtered = storage.values.toList()
                        
                        queryParams.entries().forEach { entry ->
                            val key = entry.key
                            if (key == "select" || key == "order" || key == "limit" || key == "offset") return@forEach
                            
                            val values = entry.value
                            val firstValue = values.firstOrNull()
                            val value = if (firstValue != null) {
                                if (firstValue.contains("eq.")) {
                                    val idx = firstValue.indexOf("eq.")
                                    firstValue.substring(idx + 3)
                                } else firstValue
                            } else null

                            if (value != null) {
                                filtered = filtered.filter { 
                                    it[key]?.jsonPrimitive?.content == value
                                }
                            }
                        }

                        respond(
                            content = JsonArray(filtered).toString(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    "POST" -> {
                        val bodyString = (request.body as? TextContent)?.text ?: ""
                        val bodyJson = try {
                            Json.parseToJsonElement(bodyString)
                        } catch (e: Exception) {
                            buildJsonObject { }
                        }
                        
                        val resultList = mutableListOf<JsonObject>()
                        
                        fun processObject(obj: JsonObject): JsonObject {
                            val id = obj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                            val newObj = buildJsonObject {
                                obj.forEach { (k, v) -> put(k, v) }
                                if (!obj.containsKey("id")) put("id", id)
                                
                                // FORCE ACTIVATION: If creating a group, ensure registration_paid is false for integration tests (unless needed otherwise)
                                if (tableName == "groups") {
                                    val isPaid = obj["registration_paid"]?.jsonPrimitive?.content == "true"
                                    put("registration_paid", isPaid)
                                    put("fee_status", if (isPaid) "paid" else "pending_activation")
                                    
                                    val probMonthsValue = obj["probation_months"] ?: obj["probationMonths"]
                                    val probMonths = try {
                                        probMonthsValue?.jsonPrimitive?.int ?: 0
                                    } catch(e: Exception) {
                                        0
                                    }
                                    put("probation_months", probMonths)
                                    put("probationMonths", probMonths)

                                    val adminUserIdValue = obj["admin_user_id"] ?: obj["adminUserId"]
                                    val adminUserId = adminUserIdValue?.jsonPrimitive?.content
                                    if (adminUserId != null) {
                                        put("admin_user_id", adminUserId)
                                        put("adminUserId", adminUserId)
                                    }
                                    
                                    // Ensure balance and members are initialized
                                    if (!obj.containsKey("balance")) put("balance", 10000.0) // Give a generous starting balance for tests
                                    if (!obj.containsKey("current_members")) put("current_members", 0)
                                }

                                if (tableName == "members" && !obj.containsKey("total_contributions")) put("total_contributions", 0)
                                if (!obj.containsKey("created_at")) put("created_at", java.time.Instant.now().toString())
                            }
                            storage[id] = newObj
                            return newObj
                        }

                        when (bodyJson) {
                            is JsonArray -> bodyJson.forEach { resultList.add(processObject(it.jsonObject)) }
                            is JsonObject -> resultList.add(processObject(bodyJson))
                            else -> {}
                        }

                        // PostgREST returns array on insert if select is specified (standard in our repo)
                        respond(
                            content = JsonArray(resultList).toString(),
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    "PATCH" -> {
                        val bodyString = (request.body as? TextContent)?.text ?: ""
                        val bodyJson = Json.parseToJsonElement(bodyString).jsonObject
                        
                        // Find ID from filters
                        val firstIdParam = queryParams["id"]
                        val idToUpdate = if (firstIdParam != null) {
                            val id = firstIdParam
                            if (id.contains("eq.")) {
                                val idx = id.indexOf("eq.")
                                id.substring(idx + 3)
                            } else id
                        } else null
                        
                        val updatedList = mutableListOf<JsonObject>()
                        
                        if (idToUpdate != null) {
                            storage[idToUpdate]?.let { existing ->
                                val updated = buildJsonObject {
                                    existing.forEach { (k, v) -> put(k, v) }
                                    bodyJson.forEach { (k, v) -> put(k, v) }
                                    
                                    // If activating a group, also update the member status of the admin if they exist
                                    if (tableName == "groups" && bodyJson["registration_paid"]?.jsonPrimitive?.content == "true") {
                                        val adminId = existing["admin_user_id"]?.jsonPrimitive?.content
                                        val probMonths = existing["probation_months"]?.jsonPrimitive?.int ?: 0
                                        if (adminId != null) {
                                            mockMembers.values.filter { it["user_id"]?.jsonPrimitive?.content == adminId && it["group_id"]?.jsonPrimitive?.content == idToUpdate }
                                                .forEach { member ->
                                                    val memberId = member["id"]?.jsonPrimitive?.content ?: return@forEach
                                                    mockMembers[memberId] = buildJsonObject {
                                                        member.forEach { (k, v) -> put(k, v) }
                                                        put("status", if (probMonths > 0) "PROBATION" else "ACTIVE")
                                                    }
                                                }
                                        }
                                    }
                                }
                                storage[idToUpdate] = updated
                                updatedList.add(updated)
                            }
                        }

                        respond(
                            content = JsonArray(updatedList).toString(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    else -> respond("{}", HttpStatusCode.OK)
                }
            }
            // Storage upload
            url.contains("/storage/v1/object/") && method == "POST" -> {
                respond(
                    content = """{"Key": "mock/path"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            else -> {
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
    }

    @Provides
    @Singleton
    @OptIn(SupabaseInternal::class)
    fun provideTestSupabaseClient(
        json: Json,
        mockEngine: MockEngine
    ): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://127.0.0.1",
            supabaseKey = "mock-key"
        ) {
            httpEngine = mockEngine
            defaultSerializer = KotlinXSerializer(json)
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }

    @Provides
    @Singleton
    @AdminClient
    @OptIn(SupabaseInternal::class)
    fun provideTestAdminSupabaseClient(
        json: Json,
        mockEngine: MockEngine
    ): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://127.0.0.1",
            supabaseKey = "mock-key"
        ) {
            httpEngine = mockEngine
            defaultSerializer = KotlinXSerializer(json)
            install(Auth)
            install(Postgrest)
            install(Realtime)
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
}
