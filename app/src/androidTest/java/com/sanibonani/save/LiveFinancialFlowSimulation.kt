package com.sanibonani.save

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.*
import kotlin.math.abs

import org.junit.Ignore

/**
 * PURE SIMULATION: Verifies the backend financial integrity by bypassing RLS via Service Role.
 */
@Ignore("Local Supabase mock server utility")
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LiveFinancialFlowSimulation {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    // Using 127.0.0.1 for physical device with adb reverse
    private val supabaseUrl = "http://127.0.0.1:45421"
    private val serviceRoleKey = "sb_REDACTED_FROM_HISTORY"

    private fun buildServiceClient() = createSupabaseClient(supabaseUrl, serviceRoleKey) {
        install(Auth)
        install(Postgrest)
    }

    companion object {
        private const val TAG = "LiveFinSim"
    }

    @Test
    fun runLiveFinancialFlowSimulation() {
        hiltRule.inject()
        runBlocking {
            val activeClient = buildServiceClient()
            try {
                Log.i(TAG, "SIM_TRACE: Step 0 - Setup (Service Role Bypass)")
                var adminUserId: String = UUID.randomUUID().toString()
                val groupId = UUID.randomUUID().toString()
                val groupName = "SIM-FLOWS-${System.currentTimeMillis().toString().takeLast(6)}"

                // 1. Get a valid user ID for FK constraints
                try {
                    val profiles = activeClient.postgrest["profiles"].select { limit(1) }.decodeList<JsonObject>()
                    if (profiles.isNotEmpty()) {
                        adminUserId = profiles[0]["id"]?.jsonPrimitive?.content ?: adminUserId
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SIM_TRACE: No profiles found: ${e.message}")
                }

                // 2. Create group
                activeClient.postgrest["groups"].insert(buildJsonObject {
                    put("id", groupId)
                    put("name", groupName)
                    put("type", "stokvel")
                    put("admin_user_id", adminUserId)
                    put("registration_paid", true)
                })

                // 3. Inject Liquidity
                activeClient.postgrest.rpc("increment_group_balance", buildJsonObject {
                    put("p_group_id", groupId)
                    put("p_amount", 10000.0)
                    put("p_description", "Sim Liquidity")
                }).decodeAs<JsonElement>()

                // 4. Register Member
                val memberId = UUID.randomUUID().toString()
                activeClient.postgrest["members"].insert(buildJsonObject {
                    put("id", memberId)
                    put("group_id", groupId)
                    put("full_name", "Sim Member")
                    put("status", "active")
                })

                // 5. Payout (R2,000)
                val payoutId = UUID.randomUUID().toString()
                activeClient.postgrest["payouts"].insert(buildJsonObject {
                    put("id", payoutId)
                    put("group_id", groupId)
                    put("amount", 2000.0)
                    put("status", "group_approved")
                    put("bank_name", "Sim Bank")
                    put("account_no", "123456789")
                    put("branch_code", "123456")
                })

                activeClient.postgrest.rpc("complete_payout_v2", buildJsonObject {
                    put("p_payout_id", payoutId)
                    put("p_admin_id", adminUserId)
                })

                // 6. Loan Flow (R1,000)
                val loanId = UUID.randomUUID().toString()
                activeClient.postgrest["loans"].insert(buildJsonObject {
                    put("id", loanId)
                    put("group_id", groupId)
                    put("member_id", memberId)
                    put("amount", 1000.0)
                    put("interest_rate", 10.0)
                    put("total_to_repay", 1100.0)
                    put("monthly_repayment", 1100.0)
                    put("status", "pending")
                    put("start_date", "2026-07-29")
                })

                activeClient.postgrest.rpc("approve_and_disburse_loan_v2", buildJsonObject {
                    put("p_loan_id", loanId)
                    put("p_admin_id", adminUserId)
                    put("p_payment_method", "bank")
                })

                // 7. Repayment (R500)
                activeClient.postgrest.rpc("record_loan_repayment_v1", buildJsonObject {
                    put("p_loan_id", loanId)
                    put("p_member_id", memberId)
                    put("p_group_id", groupId)
                    put("p_amount", 500.0)
                }).decodeAs<JsonElement>()

                // 8. Integrity Verification
                val auditReport = activeClient.postgrest.rpc("audit_and_fix_group_balance_v1", buildJsonObject {
                    put("p_group_id", groupId)
                    put("p_fix", false)
                }).decodeAs<JsonObject>()

                val currentBalance = auditReport["current_balance"]?.jsonPrimitive?.double ?: 0.0
                val expectedBalance = 10000.0 - 2000.0 - 1000.0 + 500.0

                if (abs(currentBalance - expectedBalance) < 0.01) {
                    Log.i(TAG, "SIM_TRACE: SUCCESS - Financial Flow Integrity Verified.")
                } else {
                    Log.e(TAG, "SIM_TRACE: FAILURE - Balance Discrepancy! Expected $expectedBalance, got $currentBalance")
                    error("Balance mismatch")
                }

            } finally {
                activeClient.close()
            }
        }
    }
}
