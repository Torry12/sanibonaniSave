package com.sanibonani.save

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import kotlin.random.Random
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Ignore("Manual live seeding utility; excluded from automated instrumentation runs.")
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LiveSeeder {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var memberRepo: MemberRepository

    @Inject
    lateinit var groupRepo: GroupRepository

    @Inject
    lateinit var supabaseRepo: SupabaseRepository

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun seedLargeTestGroup() = runBlocking {
        // Sign up a new user first to ensure we have a valid session and bypassing RLS
        val testEmail = "testuser_${System.currentTimeMillis()}@example.com"
        val testPassword = "Password123!"

        supabaseRepo.adminSignUp(testEmail, testPassword, mapOf("role" to "group_admin"), confirm = true).getOrThrow()
        supabaseRepo.signIn(testEmail, testPassword).getOrThrow()
        val userId = supabaseRepo.currentUserId ?: "00000000-0000-0000-0000-000000000000"

        val testGroupId = UUID.randomUUID().toString()

        // 1. Create a group with 12 months history
        val currentUserId = userId
        val historyMonths = 12
        val baseDate = LocalDate.now().minusMonths(historyMonths.toLong())

        val group = Group(
            id = testGroupId,
            name = "Soweto Community Fund ${System.currentTimeMillis().toString().takeLast(4)}",
            monthlyContribution = Money(250.0),
            lateFee = Money(50.0),
            paymentDueDay = 28,
            type = GroupType.BURIAL_SOCIETY,
            beneficiaryIncreasePct = 10.0,
            adminUserId = currentUserId,
            isPublic = true,
            registrationPaid = true,
            latitude = -26.2678 + (Random.nextDouble() - 0.5) * 0.1,
            longitude = 27.8585 + (Random.nextDouble() - 0.5) * 0.1,
            province = "Gauteng",
            city = "Johannesburg",
            township = "Soweto",
            description = "A community-focused burial society serving Soweto residents with 12-month financial history."
        )

        println("Seeding Group: ${group.id} at ${group.latitude}, ${group.longitude}")
        supabaseClient.postgrest["groups"].insert(group)

        // 2. Create 100 members with full 12-month transaction history
        val firstNames = listOf("Sizwe", "Thabo", "Lindiwe", "Zanele", "Musa", "Bongani", "Naledi", "Palesa")
        val lastNames = listOf("Dlamini", "Khumalo", "Mbeki", "Zuma", "Buthelezi", "Ndlovu")

        for (i in 1..100) {
            val memberId = UUID.randomUUID().toString()
            val member = Member(
                id = memberId,
                groupId = testGroupId,
                fullName = "${firstNames.random()} ${lastNames.random()} $i",
                phone = "0${Random.nextInt(100000000, 999999999)}",
                joinedAt = baseDate.atStartOfDay().toString() + "Z",
                status = MemberStatus.ACTIVE,
                beneficiaryOver65Count = Random.nextInt(0, 3)
            )

            supabaseClient.postgrest["members"].insert(member)

            // 3. Add 12 months of transaction history for each member
            for (monthIndex in 0 until historyMonths) {
                val txDate = baseDate.plusMonths(monthIndex.toLong()).withDayOfMonth(28)
                val dueDateStr = txDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val paidAtStr = "${dueDateStr}T12:00:00Z"
                val amount = if (Random.nextDouble() > 0.1) 250.0 else 125.0
                val status = if (amount >= 250.0) "paid" else "partial"

                try {
                    supabaseClient.postgrest.rpc("record_contribution_v1", mapOf(
                        "p_member_id" to memberId,
                        "p_group_id" to testGroupId,
                        "p_amount" to amount,
                        "p_due_date" to dueDateStr,
                        "p_paid_at" to paidAtStr,
                        "p_status" to status
                    ))
                } catch (_: Exception) {
                    // Fallback to direct insert if RPC fails during seeding
                    val contribution = Contribution(
                        memberId = memberId,
                        groupId = testGroupId,
                        amount = Money(amount),
                        status = if (status == "paid") ContributionStatus.PAID else ContributionStatus.PARTIAL,
                        paidAt = paidAtStr,
                        dueDate = dueDateStr,
                        type = "contribution"
                    )
                    supabaseClient.postgrest["contributions"].insert(contribution)
                }
            }

            if (i % 10 == 0) println("Seeded $i members with 12-month transaction history...")
        }

        println("Seeding complete with 12-month member transaction histories. Group ID: $testGroupId")
    }
}
