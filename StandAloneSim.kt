
import kotlinx.coroutines.runBlocking
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.*
import java.util.UUID
import java.time.LocalDate
import kotlin.random.Random

fun main() = runBlocking {
    val supabaseUrl = "https://aszmodkqpafwtnfufmow.supabase.co"
    val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFzem1vZGtxcGFmd3RuZnVmbW93Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc2NDA2MzksImV4cCI6MjA5MzIxNjYzOX0.xmjdSHDGO8sm1cq_574yUxADfCWljtBKf94U-ReTxy8"

    val client = createSupabaseClient(supabaseUrl, anonKey) {
        httpEngine = OkHttp.create()
        install(Auth)
        install(Postgrest)
    }

    try {
        val PLATFORM_ADMIN = "torrymsimango@gmail.com"
        val PLATFORM_PASS = "torry123M"

        println("SIM_TRACE: Step 1 - Signing In")
        client.auth.signInWith(Email) {
            email = PLATFORM_ADMIN
            password = PLATFORM_PASS
        }
        val adminUserId = client.auth.currentUserOrNull()?.id ?: error("Sign in failed")
        println("SIM_TRACE: Sign in successful. UID: $adminUserId")

        val groupId = UUID.randomUUID().toString()
        val monthlyAmount = 500.0

        println("SIM_TRACE: Step 2 - Creating group $groupId")
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

        println("SIM_TRACE: Step 3 - Registering members")
        val members = mutableListOf<String>()
        val joinedDate = LocalDate.now().minusMonths(6).withDayOfMonth(1)
        val joinedStr = joinedDate.toString() + "T00:00:00Z"

        // Register 15 members
        val firstNames = listOf("Sizwe", "Thabo", "Lindiwe", "Zanele", "Musa", "Bongani", "Naledi", "Palesa")
        val lastNames = listOf("Dlamini", "Khumalo", "Mbeki", "Zuma", "Buthelezi", "Ndlovu")

        for (i in 1..15) {
            val memberId = UUID.randomUUID().toString()
            client.postgrest["members"].insert(buildJsonObject {
                put("id", memberId)
                put("group_id", groupId)
                put("user_id", UUID.randomUUID().toString())
                put("full_name", "${firstNames.random()} ${lastNames.random()} $i")
                put("status", "active")
                put("joined_at", joinedStr)
            })
            members.add(memberId)
        }
        println("SIM_TRACE: Seeded 15 members.")

        println("SIM_TRACE: Step 4 - Simulating 6 months of history")

        for (monthOffset in 5 downTo 0) {
            val simDate = LocalDate.now().minusMonths(monthOffset.toLong())
            val dueDateStr = simDate.withDayOfMonth(28).toString()
            val paidAtStr = simDate.withDayOfMonth(28).toString() + "T12:00:00Z"

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
        }

        println("SIM_TRACE: Deep Simulation SUCCESS")
    } finally {
        client.close()
    }
}
