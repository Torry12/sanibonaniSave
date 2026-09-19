package com.sanibonani.save.simulation

import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.random.Random
import java.time.LocalDate

/**
 * Reusable simulation engine for SanibonaniSave.
 * Ported from legacy JVM test runners to a dedicated module.
 */
class LiveSimulationRunner(
    private val supabaseUrl: String,
    private val anonKey: String
) {

    private val memorySessionManager = object : SessionManager {
        private var stored: UserSession? = null
        override suspend fun saveSession(session: UserSession) { stored = session }
        override suspend fun loadSession(): UserSession = stored ?: error("No session stored")
        override suspend fun deleteSession() { stored = null }
    }

    private val noOpCodeVerifierCache = object : CodeVerifierCache {
        override suspend fun saveCodeVerifier(codeVerifier: String) {}
        override suspend fun loadCodeVerifier(): String? = null
        override suspend fun deleteCodeVerifier() {}
    }

    private val client = createSupabaseClient(supabaseUrl, anonKey) {
        install(Auth) {
            sessionManager = memorySessionManager
            codeVerifierCache = noOpCodeVerifierCache
        }
        install(Postgrest)
        httpConfig {
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 60000L
                connectTimeoutMillis = 30000L
                socketTimeoutMillis = 30000L
            }
        }
    }

    fun close() {
        client.close()
    }

    /**
     * Runs a 6-month historical activity simulation for a new Stokvel group.
     */
    suspend fun runDeepHistoricalActivitySimulation(
        adminEmail: String,
        adminPass: String
    ): Result<String> = runCatching {
        println("SIM_TRACE: Starting Deep Historical Simulation")

        // 1. Sign In
        client.auth.signInWith(Email) {
            email = adminEmail
            password = adminPass
        }
        val adminUserId = client.auth.currentUserOrNull()?.id ?: error("Sign in failed")
        println("SIM_TRACE: Authenticated as $adminEmail (UID: $adminUserId)")

        val groupId = UUID.randomUUID().toString()
        val monthlyAmount = 500.0

        // 2. Create Group
        println("SIM_TRACE: Creating group $groupId")
        client.postgrest["groups"].insert(buildJsonObject {
            put("id", groupId)
            put("name", "Sim-Stokvel-${System.currentTimeMillis().toString().takeLast(4)}")
            put("type", "stokvel")
            put("admin_user_id", adminUserId)
            put("province", "Gauteng")
            put("city", "Johannesburg")
            put("joining_fee", 150.0)
            put("monthly_contribution", monthlyAmount)
            put("registration_paid", true)
            put("fee_status", "paid")
        })

        // 3. Register Members
        val members = mutableListOf<String>()
        val joinedDate = LocalDate.now().minusMonths(6).withDayOfMonth(1)
        val joinedStr = joinedDate.toString() + "T00:00:00Z"

        // Fetch auto-created admin member
        val adminMemberRes = client.postgrest["members"].select(Columns.raw("*")) {
            filter {
                eq("group_id", groupId)
                eq("user_id", adminUserId)
            }
        }.decodeSingleOrNull<JsonObject>()

        if (adminMemberRes != null) {
            members.add(adminMemberRes["id"]!!.jsonPrimitive.content)
            println("SIM_TRACE: Found auto-created admin member.")
        } else {
            val adminMemberId = UUID.randomUUID().toString()
            client.postgrest["members"].insert(buildJsonObject {
                put("id", adminMemberId)
                put("group_id", groupId)
                put("user_id", adminUserId)
                put("full_name", "Platform Admin (Sim)")
                put("status", "active")
                put("joined_at", joinedStr)
            })
            members.add(adminMemberId)
        }

        val firstNames = listOf("Sizwe", "Thabo", "Lindiwe", "Zanele", "Musa", "Bongani", "Naledi", "Palesa")
        val lastNames = listOf("Dlamini", "Khumalo", "Mbeki", "Zuma", "Buthelezi", "Ndlovu")

        for (i in 2..10) {
            val memberId = UUID.randomUUID().toString()
            client.postgrest["members"].insert(buildJsonObject {
                put("id", memberId)
                put("group_id", groupId)
                put("user_id", UUID.randomUUID().toString())
                put("full_name", "${firstNames.random()} ${lastNames.random()} $i")
                put("phone", "082${Random.nextInt(1000000, 9999999)}")
                put("status", "active")
                put("joined_at", joinedStr)
            })
            members.add(memberId)
        }
        println("SIM_TRACE: Seeded 10 members.")

        // 4. Simulate 6 months of history
        for (monthOffset in 5 downTo 0) {
            val simDate = LocalDate.now().minusMonths(monthOffset.toLong())
            val dueDateStr = simDate.withDayOfMonth(28).toString()
            val paidAtStr = simDate.withDayOfMonth(28).toString() + "T12:00:00Z"

            println("SIM_TRACE: Simulating month -$monthOffset ($dueDateStr)")
            for (memberId in members) {
                val behavior = Random.nextDouble()
                val paymentAmount = when {
                    behavior < 0.70 -> monthlyAmount
                    behavior < 0.85 -> monthlyAmount / 2.0
                    else -> 0.0
                }

                if (paymentAmount > 0) {
                    try {
                        client.postgrest.rpc("record_contribution_v1", buildJsonObject {
                            put("p_member_id", memberId)
                            put("p_group_id", groupId)
                            put("p_amount", paymentAmount)
                            put("p_due_date", dueDateStr)
                            put("p_paid_at", paidAtStr)
                            put("p_status", if (paymentAmount >= monthlyAmount) "paid" else "partial")
                        })
                    } catch (e: Exception) {
                        println("Failed to record contribution for $memberId: ${e.message}")
                    }
                }
            }

            // Occasional loans
            if (monthOffset == 4 || monthOffset == 2) {
                val borrower = members.random()
                val loanId = UUID.randomUUID().toString()
                val loanAmount = 2000.0

                try {
                    client.postgrest["loans"].insert(buildJsonObject {
                        put("id", loanId)
                        put("group_id", groupId)
                        put("member_id", borrower)
                        put("amount", loanAmount)
                        put("interest_rate", 5.0)
                        put("total_to_repay", loanAmount * 1.05)
                        put("total_repaid", 0.0)
                        put("monthly_repayment", (loanAmount * 1.05) / 3)
                        put("start_date", simDate.withDayOfMonth(5).toString())
                        put("end_date", simDate.plusMonths(3).withDayOfMonth(5).toString())
                        put("status", if (monthOffset == 4) "overdue" else "active")
                        put("purpose", "Emergency simulation")
                    })
                    println("SIM_TRACE: Simulated loan creation for $borrower")
                } catch (e: Exception) {
                    println("Failed to insert loan: ${e.message}")
                }
            }
        }

        println("SIM_TRACE: Deep Simulation SUCCESS for group $groupId")
        groupId
    }
}
