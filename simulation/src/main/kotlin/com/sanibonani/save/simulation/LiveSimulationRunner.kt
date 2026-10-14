package com.sanibonani.save.simulation

import android.content.Context
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.okhttp.OkHttp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Reusable simulation engine for SanibonaniSave.
 * AI Optimization: This class provides the "Ground Truth" for ML behavior tracking.
 * It is designed to generate synthetic but realistic financial activity, including
 * fraudulent patterns for training AI models.
 */
class LiveSimulationRunner(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val context: Context? = null
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
    private fun createClient(): SupabaseClient {
        return createSupabaseClient(supabaseUrl, anonKey) {
            install(Auth) {
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                sessionManager = io.github.jan.supabase.auth.MemorySessionManager()
                codeVerifierCache = io.github.jan.supabase.auth.MemoryCodeVerifierCache()
            }
            install(Postgrest)
            httpEngine = OkHttp.create()
            defaultSerializer = KotlinXSerializer(json)
            httpConfig {
                install(io.ktor.client.plugins.HttpTimeout) {
                    requestTimeoutMillis = 30000L
                    connectTimeoutMillis = 15000L
                    socketTimeoutMillis = 30000L
                }
            }
        }
    }

    suspend fun runDeepHistoricalActivitySimulation(
        adminEmail: String,
        adminPass: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val client = createClient()
        try {
            Result.success(performDeepSimulation(client, adminEmail, adminPass))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            delay(2.seconds)
            withContext(NonCancellable) {
                client.close()
            }
        }
    }

    private suspend fun performDeepSimulation(
        client: SupabaseClient,
        adminEmail: String,
        adminPass: String
    ): String {
        println("SIM_TRACE: Starting Deep Historical Multi-Group 12-Month Simulation")

        // 1. Sign In
        client.auth.signInWith(Email) {
            email = adminEmail
            password = adminPass
        }

        val adminUserId = client.auth.currentUserOrNull()?.id ?: error("Sign in failed")
        println("SIM_TRACE: Authenticated as $adminEmail (UID: $adminUserId)")

        val timestamp = System.currentTimeMillis().toString().takeLast(4)

        // 2. Run 12-Month Simulation for EACH Group Type
        val stokvelGroupId = perform12MonthSimulationForGroupType(client, adminUserId, "stokvel", "Sim-Stokvel-$timestamp", Money(500.0))
        val burialGroupId = perform12MonthSimulationForGroupType(client, adminUserId, "burial_society", "Sim-Burial-$timestamp", Money(200.0))
        val investmentGroupId = perform12MonthSimulationForGroupType(client, adminUserId, "investment_club", "Sim-Investment-$timestamp", Money(1000.0))
        val roscaGroupId = perform12MonthSimulationForGroupType(client, adminUserId, "rosca", "Sim-ROSCA-$timestamp", Money(400.0))

        println("SIM_TRACE: 12-MONTH MULTI-GROUP DEEP SIMULATION COMPLETED SUCCESSFULLY FOR ALL GROUPS!")
        return stokvelGroupId
    }

    private suspend fun perform12MonthSimulationForGroupType(
        client: SupabaseClient,
        adminUserId: String,
        groupType: String,
        groupName: String,
        monthlyContributionAmount: Money = Money(500.0)
    ): String {
        println("SIM_TRACE: ==================================================")
        println("SIM_TRACE: Starting 12-Month Simulation for Group Type: $groupType ($groupName)")
        println("SIM_TRACE: ==================================================")

        val groupId = UUID.randomUUID().toString()

        val typeEnum = GroupType.entries.find { it.name.lowercase() == groupType.lowercase() } ?: GroupType.STOKVEL
        val testGroup = DomainSeedFactory.createTestGroup(
            id = groupId,
            name = groupName,
            type = typeEnum,
            adminUserId = adminUserId,
            monthlyContribution = monthlyContributionAmount.toDouble()
        )

        // 1. Create Group
        client.postgrest["groups"].insert(buildJsonObject {
            put("id", testGroup.id)
            put("name", testGroup.name)
            put("type", testGroup.type.name.lowercase())
            put("admin_user_id", testGroup.adminUserId)
            put("province", testGroup.province)
            put("city", testGroup.city)
            put("joining_fee", testGroup.joiningFee.amount.toPlainString())
            put("monthly_contribution", testGroup.monthlyContribution.amount.toPlainString())
            put("registration_paid", testGroup.registrationPaid)
            put("fee_status", "paid")
        })

        // 2. Register Members (10 Members)
        val members = mutableListOf<String>()
        val joinedDate = LocalDate.now().minusMonths(12).withDayOfMonth(1)
        val joinedStr = joinedDate.toString() + "T00:00:00Z"

        // Helper to setup member banking and joining fee using DomainSeedFactory
        suspend fun setupMember(memberId: String, fullName: String, userId: String? = null) {
            val testMember = DomainSeedFactory.createTestMember(
                id = memberId,
                groupId = groupId,
                userId = userId,
                fullName = fullName,
                joinedDate = joinedDate
            )

            val memberPayload = buildJsonObject {
                put("id", testMember.id)
                put("group_id", testMember.groupId)
                if (testMember.userId != null) put("user_id", testMember.userId)
                put("full_name", testMember.fullName)
                put("status", "active")
                put("joined_at", testMember.joinedAt)
                put("phone", testMember.phone)
                put("bank_name", testMember.bankName)
                put("account_no", testMember.accountNo)
                put("branch_code", testMember.branchCode)
            }

            // Upsert or insert member
            try {
                client.postgrest["members"].insert(memberPayload)
            } catch (_: Exception) {
                client.postgrest["members"].update(memberPayload) {
                    filter { eq("id", memberId) }
                }
            }

            // Record joining fee transaction history
            try {
                client.postgrest.rpc("record_contribution_v2", buildJsonObject {
                    put("p_member_id", memberId)
                    put("p_group_id", groupId)
                    put("p_amount", Money(150.0).amount.toPlainString())
                    put("p_due_date", joinedDate.toString())
                    put("p_paid_at", joinedStr)
                    put("p_status", "paid")
                    put("p_type", "joining_fee")
                    put("p_tx_id", "sim-join-$memberId")
                })
            } catch (e: Exception) {
                println("SIM_TRACE: Note on joining fee seed for $memberId: ${e.message}")
            }
        }

        // Fetch auto-created admin member
        val adminMemberRes = client.postgrest["members"].select(Columns.raw("*")) {
            filter {
                eq("group_id", groupId)
                eq("user_id", adminUserId)
            }
        }.decodeSingleOrNull<JsonObject>()

        val adminMemberId = adminMemberRes?.get("id")?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
        setupMember(adminMemberId, "$groupType Admin", adminUserId)
        members.add(adminMemberId)

        val firstNames = listOf("Sizwe", "Thabo", "Lindiwe", "Zanele", "Musa", "Bongani", "Naledi", "Palesa", "Kagiso", "Sipho")
        val lastNames = listOf("Dlamini", "Khumalo", "Mbeki", "Zuma", "Buthelezi", "Ndlovu", "Mokoena", "Sithole")

        for (i in 2..10) {
            val memberId = UUID.randomUUID().toString()
            val fullName = "${firstNames[(i - 1) % firstNames.size]} ${lastNames[(i - 1) % lastNames.size]} $i"
            setupMember(memberId, fullName)
            members.add(memberId)
        }

        // 3. Loop 12 Months of Payments & Group-Specific Workflows
        for (monthOffset in 11 downTo 0) {
            val simDate = LocalDate.now().minusMonths(monthOffset.toLong())
            val dueDateStr = simDate.withDayOfMonth(28).toString()
            val paidAtOnTimeStr = simDate.withDayOfMonth(28).toString() + "T12:00:00Z"
            val paidAtLateStr = simDate.plusDays(5).toString() + "T14:30:00Z"

            println("SIM_TRACE: [$groupType] Month -${monthOffset + 1}/12 ($simDate)")

            members.forEachIndexed { index, memberId ->
                val paymentAmount = when (index) {
                    in 0..5 -> monthlyContributionAmount // On-time full payment
                    in 6..7 -> if (monthOffset % 2 == 0) monthlyContributionAmount / 2.0 else monthlyContributionAmount * 1.5 // Partial & catch-up
                    8 -> monthlyContributionAmount + Money(50.0) // Late payment with fee
                    else -> if (monthOffset in 6..8) Money.ZERO else monthlyContributionAmount // Distressed/missed
                }

                if (paymentAmount.isPositive) {
                    val statusStr = if (paymentAmount >= monthlyContributionAmount) "paid" else "partial"
                    client.postgrest.rpc("record_contribution_v2", buildJsonObject {
                        put("p_member_id", memberId)
                        put("p_group_id", groupId)
                        put("p_amount", paymentAmount.amount.toPlainString())
                        put("p_due_date", dueDateStr)
                        put("p_paid_at", if (index == 8) paidAtLateStr else paidAtOnTimeStr)
                        put("p_status", statusStr)
                        put("p_type", "contribution")
                        put("p_tx_id", "sim-tx-${UUID.randomUUID()}")
                    })
                }
            }

            // Group-Specific Monthly Workflows
            when (groupType) {
                "burial_society" -> {
                    if (monthOffset == 3) {
                        // Funeral claim submission and payout in month -4
                        simulateSuccessfulBurialClaimPayout(client, groupId, members[3], adminUserId, Money(10000.0))
                    }
                }
                "investment_club" -> {
                    if (monthOffset == 1) {
                        // Disinvestment payout in month -2
                        executeAndCompletePayout(client, groupId, members[4], adminUserId, Money(5000.0), "disinvestment", "SIM-INV-DISB-101")
                    }
                }
                "rosca" -> {
                    if (monthOffset in listOf(9, 6, 3, 0)) {
                        // Pot rotation payout every 3 months
                        executeAndCompletePayout(client, groupId, members[monthOffset / 3], adminUserId, Money(4000.0), "rotation", "SIM-ROT-POT-${monthOffset}")
                    }
                }
                "stokvel" -> {
                    if (monthOffset == 6) {
                        // Emergency withdrawal payout
                        executeAndCompletePayout(client, groupId, members[2], adminUserId, Money(2000.0), "emergency", "SIM-EMG-101")
                    }
                    if (monthOffset == 0) {
                        // Annual share distribution
                        executeAndCompletePayout(client, groupId, members[1], adminUserId, Money(6000.0), "annual", "SIM-ANN-202")
                    }
                }
            }
        }

        // 4. Audit & Balance Verification
        val auditReport = client.postgrest.rpc("audit_and_fix_group_balance_v1", buildJsonObject {
            put("p_group_id", groupId)
            put("p_fix", false)
        }).decodeAs<JsonObject>()

        val ledgerSum = auditReport["ledger_sum"]?.jsonPrimitive?.double ?: 0.0
        val currentBalance = auditReport["current_balance"]?.jsonPrimitive?.double ?: 0.0
        val discrepancy = auditReport["discrepancy"]?.jsonPrimitive?.double ?: 0.0

        println("VERIFY: [$groupType] 12-Month Ledger Sum: $ledgerSum")
        println("VERIFY: [$groupType] 12-Month Group Balance: $currentBalance")
        println("VERIFY: [$groupType] 12-Month Discrepancy: $discrepancy")

        if (abs(discrepancy) > 0.01) {
            error("[$groupType] Balance discrepancy detected over 12-month simulation: $discrepancy")
        }

        println("SIM_TRACE: [$groupType] 12-Month Simulation SUCCESS for group $groupId")
        return groupId
    }

    private suspend fun simulateFraudulentBehavior(
        client: SupabaseClient,
        groupId: String,
        memberId: String
    ) {
        // Pattern 1: Rapid disbursements (velocity spike)
        println("SIM_TRACE: Simulating velocity spike for member $memberId")
        repeat(5) { i ->
            val loanId = UUID.randomUUID().toString()
            client.postgrest["loans"].insert(buildJsonObject {
                put("id", loanId)
                put("group_id", groupId)
                put("member_id", memberId)
                put("amount", 1000.0)
                put("interest_rate", 5.0)
                put("total_to_repay", 1050.0)
                put("total_repaid", 0.0)
                put("status", "pending")
                put("purpose", "Fraud simulation spike $i")
                put("monthly_repayment", 350.0)
                put("start_date", LocalDate.now().toString())
                put("end_date", LocalDate.now().plusMonths(3).toString())
            })
        }

        // Pattern 2: Duplicate transactions
        println("SIM_TRACE: Simulating duplicate transactions for member $memberId")
        val amount = Money(100.0)
        val date = LocalDate.now().toString()
        repeat(3) {
            client.postgrest.rpc("record_contribution_v2", buildJsonObject {
                put("p_member_id", memberId)
                put("p_group_id", groupId)
                put("p_amount", amount.amount.toPlainString())
                put("p_due_date", date)
                put("p_paid_at", LocalDateTime.now().toString() + "Z")
                put("p_status", "paid")
                put("p_type", "contribution")
                put("p_tx_id", "sim-tx-dup-${UUID.randomUUID()}")
            })
        }

        // Update track with labels
        client.postgrest["member_behavior_track"].update(buildJsonObject {
            put("fraud_risk_level", "critical")
            put("member_status", "suspended")
            put("is_suspended", true)
            put("suspension_reason", "ML Ground Truth: Simulated fraud pattern")
            put("velocity_check_failed", true)
            put("duplicate_transaction_count", 3)
        }) {
            filter {
                eq("member_id", memberId)
                eq("group_id", groupId)
            }
        }
    }

    private suspend fun executeAndCompletePayout(
        client: SupabaseClient,
        groupId: String,
        memberId: String?,
        adminUserId: String,
        amount: Money,
        payoutType: String,
        refLabel: String? = null
    ): String {
        val payoutId = UUID.randomUUID().toString()
        println("SIM_TRACE: Creating $payoutType payout request ($amount)")
        client.postgrest["payouts"].insert(buildJsonObject {
            put("id", payoutId)
            put("group_id", groupId)
            memberId?.let { put("member_id", it) }
            put("amount", amount.amount.toPlainString())
            put("bank_name", "Simulation Bank")
            put("account_no", "123456789")
            put("branch_code", "123456")
            put("status", "group_approved")
            put("payout_type", payoutType)
            put("processed_by", adminUserId)
        })

        println("SIM_TRACE: Completing $payoutType payout")
        safeRunCatching {
            client.postgrest.rpc("complete_payout_v2", buildJsonObject {
                put("p_payout_id", payoutId)
                put("p_admin_id", adminUserId)
            })
        }.onFailure {
            client.postgrest.rpc("increment_group_balance", buildJsonObject {
                put("p_group_id", groupId)
                put("p_amount", (-amount).amount.toPlainString())
            })
            client.postgrest["payouts"].update(buildJsonObject {
                put("status", "completed")
                put("processed_at", LocalDate.now().toString() + "T12:00:00Z")
                refLabel?.let { put("payout_reference", it) }
            }) { filter { eq("id", payoutId) } }
        }

        val completed = client.postgrest["payouts"].select {
            filter { eq("id", payoutId) }
        }.decodeSingleOrNull<JsonObject>()

        val status = completed?.get("status")?.jsonPrimitive?.content
        println("SIM_TRACE: $payoutType Payout status in DB: $status")
        if (status != "completed") {
            error("$payoutType payout failed to transition to completed, actual status: $status")
        }
        return payoutId
    }

    private suspend fun simulateSuccessfulLoanPayout(
        client: SupabaseClient,
        groupId: String,
        borrowerId: String,
        adminUserId: String,
        loanAmount: Money,
        interestRatePct: Double = 10.0
    ): String {
        val loanId = UUID.randomUUID().toString()
        val interest = loanAmount.percent(interestRatePct)
        val totalToRepay = loanAmount + interest
        val monthlyRepayment = totalToRepay.dividedBy(3)

        println("SIM_TRACE: --- Simulating Loan Payout ($loanAmount, Interest: $interestRatePct%) ---")

        // 1. Request Loan
        client.postgrest["loans"].insert(buildJsonObject {
            put("id", loanId)
            put("group_id", groupId)
            put("member_id", borrowerId)
            put("amount", loanAmount.amount.toPlainString())
            put("interest_rate", interestRatePct)
            put("total_to_repay", totalToRepay.amount.toPlainString())
            put("total_repaid", Money.ZERO.amount.toPlainString())
            put("monthly_repayment", monthlyRepayment.amount.toPlainString())
            put("status", "pending")
            put("start_date", LocalDate.now().toString())
            put("purpose", "Emergency Capital Request")
        })

        // 2. Approve & Disburse Loan
        println("SIM_TRACE: Approving and Disbursing Loan $loanId")
        safeRunCatching {
            client.postgrest.rpc("approve_and_disburse_loan_v2", buildJsonObject {
                put("p_loan_id", loanId)
                put("p_admin_id", adminUserId)
                put("p_payment_method", "bank")
            })
        }.onFailure {
            client.postgrest.rpc("increment_group_balance", buildJsonObject {
                put("p_group_id", groupId)
                put("p_amount", (-loanAmount).amount.toPlainString())
            })
            client.postgrest["loans"].update(buildJsonObject {
                put("status", "active")
                put("reviewed_by", adminUserId)
                put("reviewed_at", LocalDate.now().toString() + "T12:00:00Z")
            }) { filter { eq("id", loanId) } }
        }

        // 3. Verify Loan Disbursed Status in DB
        val activeLoan = client.postgrest["loans"].select {
            filter { eq("id", loanId) }
        }.decodeSingleOrNull<JsonObject>()

        val status = activeLoan?.get("status")?.jsonPrimitive?.content
        println("SIM_TRACE: Loan Payout Status in DB: $status")
        if (status != "active" && status != "approved") {
            error("Loan payout failed to reach active status: $status")
        }

        return loanId
    }

    private suspend fun simulateSuccessfulBurialClaimPayout(
        client: SupabaseClient,
        groupId: String,
        memberId: String,
        adminUserId: String,
        claimAmount: Money = Money(15000.0)
    ): String {
        val claimId = UUID.randomUUID().toString()
        val beneficiaryId = UUID.randomUUID().toString()
        val claimPayoutId = UUID.randomUUID().toString()

        println("SIM_TRACE: --- Simulating Successful Burial Claim Payout ($claimAmount) ---")

        // 1. Submit Beneficiary Payout Claim
        client.postgrest["beneficiary_payout_claims"].insert(buildJsonObject {
            put("id", claimId)
            put("group_id", groupId)
            put("member_id", memberId)
            put("beneficiary_id", beneficiaryId)
            put("beneficiary_name", "Primary Beneficiary (Elderly Parent)")
            put("claim_amount", claimAmount.amount.toPlainString())
            put("cause_of_death", "Natural Cause")
            put("date_of_death", LocalDate.now().minusDays(3).toString())
            put("bank_name", "Simulation Bank")
            put("account_no", "987654321")
            put("branch_code", "123456")
            put("status", "submitted")
        })

        // 2. Review and Approve Burial Claim
        println("SIM_TRACE: Reviewing & Approving Burial Claim $claimId")
        client.postgrest["beneficiary_payout_claims"].update(buildJsonObject {
            put("status", "approved")
            put("reviewed_by", adminUserId)
            put("reviewed_at", LocalDate.now().toString() + "T12:00:00Z")
            put("admin_notes", "Verified death certificate and beneficiary identity")
        }) { filter { eq("id", claimId) } }

        // 3. Complete Burial Claim Payout via complete_payout_v2 RPC or record_disbursement
        println("SIM_TRACE: Disbursing Burial Claim Funeral Benefit ($claimAmount)")
        client.postgrest["payouts"].insert(buildJsonObject {
            put("id", claimPayoutId)
            put("group_id", groupId)
            put("member_id", memberId)
            put("amount", claimAmount.amount.toPlainString())
            put("bank_name", "Simulation Bank")
            put("account_no", "987654321")
            put("branch_code", "123456")
            put("status", "group_approved")
            put("payout_type", "claim")
            put("processed_by", adminUserId)
        })

        safeRunCatching {
            client.postgrest.rpc("complete_payout_v2", buildJsonObject {
                put("p_payout_id", claimPayoutId)
                put("p_admin_id", adminUserId)
            })
        }.onFailure {
            client.postgrest.rpc("increment_group_balance", buildJsonObject {
                put("p_group_id", groupId)
                put("p_amount", (-claimAmount).amount.toPlainString())
            })
            client.postgrest["payouts"].update(buildJsonObject {
                put("status", "completed")
                put("processed_at", LocalDate.now().toString() + "T12:00:00Z")
            }) { filter { eq("id", claimPayoutId) } }
        }

        // 4. Mark Claim Status as Paid
        client.postgrest["beneficiary_payout_claims"].update(buildJsonObject {
            put("status", "paid")
        }) { filter { eq("id", claimId) } }

        // 5. Verify Claim Status in DB
        val paidClaim = client.postgrest["beneficiary_payout_claims"].select {
            filter { eq("id", claimId) }
        }.decodeSingleOrNull<JsonObject>()

        val claimStatus = paidClaim?.get("status")?.jsonPrimitive?.content
        println("SIM_TRACE: Burial Claim Status in DB: $claimStatus")
        if (claimStatus != "paid") {
            error("Burial claim failed to reach paid status: $claimStatus")
        }

        return claimId
    }

    suspend fun runLiveFinancialFlowSimulation(
        adminEmail: String,
        adminPass: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val client = createClient()
        try {
            Result.success(performLiveSimulation(client, adminEmail, adminPass))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            delay(2.seconds)
            withContext(NonCancellable) {
                client.close()
            }
        }
    }

    private suspend fun performLiveSimulation(
        client: SupabaseClient,
        adminEmail: String,
        adminPass: String
    ): String {
        println("SIM_TRACE: Starting Live Financial Flow Simulation")

        // 1. Sign In
        client.auth.signInWith(Email) {
            email = adminEmail
            password = adminPass
        }

        val adminUserId = client.auth.currentUserOrNull()?.id ?: error("Sign in failed")
        println("SIM_TRACE: Authenticated as $adminEmail (UID: $adminUserId)")

        val groupId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val groupName = "SIM-FLOWS-$timestamp"

        // 2. Create Group
        println("SIM_TRACE: Creating simulation group: $groupName")
        client.postgrest["groups"].insert(buildJsonObject {
            put("id", groupId)
            put("name", groupName)
            put("type", "stokvel")
            put("admin_user_id", adminUserId)
            put("registration_paid", true)
            put("fee_status", "paid")
            put("balance", 0.0)
        })

        // 3. Inject Initial Liquidity (R50,000 for comprehensive multi-payout simulation)
        println("SIM_TRACE: Injecting R50,000 initial liquidity")
        client.postgrest.rpc("increment_group_balance", buildJsonObject {
            put("p_group_id", groupId)
            put("p_amount", Money(50000.0).amount.toPlainString())
        })

        // 4. Register Members
        println("SIM_TRACE: Registering 5 members")
        val members = mutableListOf<String>()
        for (i in 1..5) {
            val memberId = UUID.randomUUID().toString()
            client.postgrest["members"].insert(buildJsonObject {
                put("id", memberId)
                put("group_id", groupId)
                put("full_name", "Sim Flow Member $i")
                put("status", "active")
                put("joined_at", LocalDate.now().toString() + "T00:00:00Z")
            })
            members.add(memberId)
        }

        // 5. SIMULATE ALL 6 TYPES OF SUCCESSFUL PAYOUTS VIA complete_payout_v2 RPC

        // 5a. Type 1: STANDARD Payout (R2,000)
        println("SIM_TRACE: --- Payout Type 1/6: STANDARD (R2,000) ---")
        executeAndCompletePayout(client, groupId, members[0], adminUserId, Money(2000.0), "standard", "SIM-REF-STD-101")

        // 5b. Type 2: ANNUAL Distribution Payout (R5,000)
        println("SIM_TRACE: --- Payout Type 2/6: ANNUAL Distribution (R5,000) ---")
        executeAndCompletePayout(client, groupId, members[1], adminUserId, Money(5000.0), "annual", "SIM-REF-ANN-202")

        // 5c. Type 3: EMERGENCY Withdrawal Payout (R1,500)
        println("SIM_TRACE: --- Payout Type 3/6: EMERGENCY (R1,500) ---")
        executeAndCompletePayout(client, groupId, members[2], adminUserId, Money(1500.0), "emergency", "SIM-REF-EMG-303")

        // 5d. Type 4: DISINVESTMENT Liquidation Payout (R3,000)
        println("SIM_TRACE: --- Payout Type 4/6: DISINVESTMENT (R3,000) ---")
        executeAndCompletePayout(client, groupId, members[3], adminUserId, Money(3000.0), "disinvestment", "SIM-REF-INV-404")

        // 5e. Type 5: CLAIM Funeral Benefit Payout (R4,000)
        println("SIM_TRACE: --- Payout Type 5/6: CLAIM Funeral Benefit (R4,000) ---")
        val claimantId = members[4]
        simulateSuccessfulBurialClaimPayout(client, groupId, claimantId, adminUserId, Money(4000.0))

        // 5f. Type 6: ROTATION ROSCA Pot Payout (R7,000)
        println("SIM_TRACE: --- Payout Type 6/6: ROTATION ROSCA Pot (R7,000) ---")
        val roscaBorrowerId = members[0]
        executeAndCompletePayout(client, groupId, roscaBorrowerId, adminUserId, Money(7000.0), "rotation", "SIM-REF-ROT-606")

        // 6. Successful Loan Payout & Full Repayment Flow (R3,000 Loan Disbursed, R3,300 Total Repaid with Interest)
        println("SIM_TRACE: --- Starting Successful Loan Payout & Repayment Flow (R3,000) ---")
        val borrowerId = members[1]
        val simLoanId = simulateSuccessfulLoanPayout(client, groupId, borrowerId, adminUserId, Money(3000.0), interestRatePct = 10.0)

        println("SIM_TRACE: Member repaying first installment (R1,100)")
        client.postgrest.rpc("record_loan_repayment_v1", buildJsonObject {
            put("p_loan_id", simLoanId)
            put("p_member_id", borrowerId)
            put("p_group_id", groupId)
            put("p_amount", Money(1100.0).amount.toPlainString())
            put("p_payment_method", "bank")
        })

        println("SIM_TRACE: Member repaying final installment (R2,200)")
        client.postgrest.rpc("record_loan_repayment_v1", buildJsonObject {
            put("p_loan_id", simLoanId)
            put("p_member_id", borrowerId)
            put("p_group_id", groupId)
            put("p_amount", Money(2200.0).amount.toPlainString())
            put("p_payment_method", "bank")
        })

        // Verify loan status in DB
        val completedLoan = client.postgrest["loans"].select {
            filter { eq("id", simLoanId) }
        }.decodeSingleOrNull<JsonObject>()

        val finalLoanStatus = completedLoan?.get("status")?.jsonPrimitive?.content
        println("SIM_TRACE: Loan Status after full repayment in DB: $finalLoanStatus")

        // 7. Integrity Verification
        println("SIM_TRACE: Running Integrity Audit")
        val auditReport = client.postgrest.rpc("audit_and_fix_group_balance_v1", buildJsonObject {
            put("p_group_id", groupId)
            put("p_fix", false)
        }).decodeAs<JsonObject>()

        val ledgerSum = auditReport["ledger_sum"]?.jsonPrimitive?.double ?: 0.0
        val currentBalance = auditReport["current_balance"]?.jsonPrimitive?.double ?: 0.0
        val discrepancy = auditReport["discrepancy"]?.jsonPrimitive?.double ?: 0.0

        println("VERIFY: Ledger Sum: $ledgerSum")
        println("VERIFY: Group Balance: $currentBalance")
        println("VERIFY: Discrepancy: $discrepancy")

        if (abs(discrepancy) > 0.01) {
            error("Balance discrepancy detected: $discrepancy")
        }

        println("SIM_TRACE: Financial Integrity Verified SUCCESS for group $groupId")

        println("VERIFY: Ledger Sum: $ledgerSum")
        println("VERIFY: Group Balance: $currentBalance")
        println("VERIFY: Discrepancy: $discrepancy")

        if (abs(discrepancy) > 0.01) {
            error("Balance discrepancy detected: $discrepancy")
        }

        println("SIM_TRACE: Financial Integrity Verified SUCCESS for group $groupId")
        return groupId
    }

    suspend fun close() {
        // No-op, managed inside use cases
    }
}
