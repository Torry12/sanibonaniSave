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
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import kotlin.math.floor
import kotlin.random.Random
import java.time.LocalDate
import org.junit.Ignore
import java.time.format.DateTimeFormatter

/**
 * PURE SEEDER: Generates a master data set using Service Role bypass.
 */
@Ignore("Manual tool")
@RunWith(AndroidJUnit4::class)
class MasterDataGenerator {

    private val supabaseUrl = BuildConfig.SUPABASE_URL_LOCAL
    private val serviceRoleKey = BuildConfig.SUPABASE_SERVICE_ROLE_KEY

    private fun buildServiceClient() = createSupabaseClient(supabaseUrl, serviceRoleKey) {
        install(Auth)
        install(Postgrest)
    }

    @Test
    fun generateMasterDataSet(): Unit = runBlocking {
        val client = buildServiceClient()
        try {
            // 1. Identify a Platform Admin
            val adminUserId = (client.postgrest["profiles"].select {
                filter { eq("role", "platform_admin") }
                limit(1)
            }.decodeSingleOrNull<JsonObject>()?.get("id")?.jsonPrimitive?.content) ?: "00000000-0000-0000-0000-000000000000"

            Log.i("MasterDataGen", "SEED: Starting Master Seeding (Admin: $adminUserId)")

            // 1. SEED BURIAL SOCIETY
            seedGroupWithHistory(client, adminUserId, GroupType.BURIAL_SOCIETY, "Eternal Peace", 40, 12, 250.0)

            // 2. SEED STOKVEL
            seedGroupWithHistory(client, adminUserId, GroupType.STOKVEL, "Unity Savings", 15, 6, 1000.0)

            // 3. SEED INVESTMENT CLUB
            seedGroupWithHistory(client, adminUserId, GroupType.INVESTMENT_CLUB, "Blue Chip", 8, 24, 5000.0)

            Log.i("MasterDataGen", "SEED: MASTER DATA GENERATION SUCCESSFUL")
        } catch (e: Exception) {
            Log.e("MasterDataGen", "SEED: FAILED - ${e.message}")
            throw e
        } finally {
            client.close()
        }
    }

    private suspend fun seedGroupWithHistory(
        client: io.github.jan.supabase.SupabaseClient,
        adminUserId: String,
        type: GroupType,
        namePrefix: String,
        memberCount: Int,
        historyMonths: Int,
        monthlyAmount: Double
    ) {
        val groupId = UUID.randomUUID().toString()
        val groupName = "SIM-$namePrefix-${System.currentTimeMillis().toString().takeLast(4)}"

        Log.i("MasterDataGen", "SEEDING: $groupName")

        client.postgrest["groups"].insert(buildJsonObject {
            put("id", groupId)
            put("name", groupName)
            put("type", type.name.lowercase())
            put("province", "Gauteng")
            put("city", "Johannesburg")
            put("joining_fee", 200.0)
            put("monthly_contribution", monthlyAmount)
            put("admin_user_id", adminUserId)
            put("registration_paid", true)
            put("created_at", LocalDate.now().minusMonths(historyMonths.toLong()).atStartOfDay().toString() + "Z")
        })

        val members = mutableListOf<String>()
        for (i in 1..memberCount) {
            val mId = UUID.randomUUID().toString()
            client.postgrest["members"].insert(buildJsonObject {
                put("id", mId)
                put("group_id", groupId)
                if (i == 1) put("user_id", adminUserId)
                put("full_name", "Seed Member $i")
                put("status", "active")
                put("joined_at", LocalDate.now().minusMonths(historyMonths.toLong()).atStartOfDay().toString() + "Z")
            })
            members.add(mId)
        }

        // Simulate history... (keeping it lightweight for reliability)
        for (mOffset in (historyMonths - 1) downTo 0) {
            val date = LocalDate.now().minusMonths(mOffset.toLong()).withDayOfMonth(28).format(DateTimeFormatter.ISO_LOCAL_DATE)
            for (mId in members) {
                if (Random.nextDouble() < 0.9) {
                    client.postgrest.rpc("record_contribution_v2", buildJsonObject {
                        put("p_member_id", mId)
                        put("p_group_id", groupId)
                        put("p_amount", monthlyAmount)
                        put("p_due_date", date)
                        put("p_status", "paid")
                    }).decodeAs<JsonObject>()
                }
            }
        }
    }
}
