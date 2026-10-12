package com.sanibonani.save.data.local

import android.util.Log
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.NotificationPref
import com.sanibonani.save.domain.model.UserProfile
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking

/**
 * Offline-first demo prepopulator for the local Room cache.
 *
 * Rationale: Room is normally populated by Supabase sync, so a fresh offline install starts
 * empty. This seeder mirrors the SQL seed philosophy (a single representative demo group,
 * Money-precise amounts) but sources data from the domain models via the existing
 * `toEntity()` mappers, keeping the local demo aligned with the domain contracts and the
 * `GroupType`/`AdminFeeState`/`MemberStatus` wire values used by the backend seeds.
 *
 * Wiring/guarding is the caller's responsibility (see LocalDatabaseProvider): it runs only
 * for the offline local database and only in debuggable builds. It is idempotent (skips when
 * the demo group already exists) and never throws — a seed failure must not break DB open.
 */
object DemoDataSeeder {
    private const val TAG = "DemoDataSeeder"

    // Stable ids so re-runs are idempotent and the group's admin link stays coherent.
    const val DEMO_USER_ID = "offline-demo-admin"
    private const val DEMO_GROUP_ID = "offline-demo-group-stokvel"
    private const val DEMO_MEMBER_ID = "offline-demo-member-admin"

    fun seedIfEmpty(db: SanibonaniDatabase) {
        try {
            runBlocking {
                val groupDao = db.groupDao()
                if (groupDao.getGroupById(DEMO_GROUP_ID) != null) return@runBlocking

                val now = Instant.now().toString()
                val joinedAt = Instant.now().minus(365, ChronoUnit.DAYS).toString()

                db.userProfileDao().upsertProfile(
                    UserProfile(
                        id = DEMO_USER_ID,
                        fullName = "Demo Admin",
                        email = "demo.admin@offline.local",
                        role = "platform_admin"
                    ).toEntity()
                )

                groupDao.upsertGroup(
                    Group(
                        id = DEMO_GROUP_ID,
                        name = "OFFLINE-Demo Stokvel",
                        type = GroupType.STOKVEL,
                        province = "Gauteng",
                        city = "Johannesburg",
                        township = "Soweto",
                        description = "Offline-first demo group mirroring the SQL seed groups.",
                        joiningFee = Money(150.0),
                        monthlyContribution = Money(500.0),
                        lateFee = Money(50.0),
                        balance = Money(36000.0),
                        adminUserId = DEMO_USER_ID,
                        registrationPaid = true,
                        feeStatus = AdminFeeState.PAID,
                        isPublic = true,
                        currentMembers = 1,
                        createdAt = now,
                        latitude = -26.2041,
                        longitude = 28.0473
                    ).toEntity()
                )

                db.memberDao().upsertMember(
                    Member(
                        id = DEMO_MEMBER_ID,
                        groupId = DEMO_GROUP_ID,
                        userId = DEMO_USER_ID,
                        memberKey = "OFFLINE-ADMIN-1",
                        fullName = "Demo Admin",
                        phone = "0820000000",
                        email = "demo.admin@offline.local",
                        status = MemberStatus.ACTIVE,
                        joinedAt = joinedAt,
                        notificationPref = NotificationPref.BOTH,
                        bankName = "First National Bank",
                        accountNo = "6200000000",
                        branchCode = "250655",
                        beneficiaryOver65Count = 0,
                        createdAt = now
                    ).toEntity()
                )
            }
            Log.i(TAG, "Offline demo data seeded (group=$DEMO_GROUP_ID).")
        } catch (t: Throwable) {
            Log.w(TAG, "Offline demo seed skipped (non-fatal).", t)
        }
    }
}
