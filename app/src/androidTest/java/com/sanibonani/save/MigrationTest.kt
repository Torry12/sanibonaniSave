package com.sanibonani.save

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanibonani.save.data.local.ALL_MIGRATIONS
import com.sanibonani.save.data.local.SanibonaniDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SanibonaniDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                """
                INSERT INTO `groups` (
                    `id`, `name`, `type`, `province`, `city`, `township`, `description`, `logo_emoji`,
                    `joining_fee`, `monthly_contribution`, `late_fee`, `late_fee_grace_days`, `probation_months`,
                    `payment_due_day`, `max_members`, `current_members`, `is_public`, `allow_partial_payment`,
                    `auto_suspend_after`, `bank_name`, `account_number`, `branch_code`, `account_type`,
                    `yoco_public_key`, `balance`, `admin_user_id`, `fee_status`, `registration_paid`,
                    `is_platform_suspended`, `created_at`, `latitude`, `longitude`, `geohash`, `updated_at`
                ) VALUES (
                    'group-legacy', 'Legacy Group', 'OTHER', 'Gauteng', 'Johannesburg', 'Soweto', 'Legacy row', '🤝',
                    120.0, 250.0, 15.0, 5, 3, 28, 50, 1, 1, 0,
                    2, 'Legacy Bank', '123456789', '250655', 'Savings',
                    NULL, 500.0, 'admin-legacy', 'DUE', 1,
                    0, '2025-01-01T00:00:00Z', -26.2041, 28.0473, 'kekjd', 123456789
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `members` (
                    `id`, `group_id`, `user_id`, `member_key`, `full_name`, `id_number`, `phone`, `email`,
                    `status`, `joined_at`, `probation_end_at`, `profile_photo_url`,
                    `document_1_url`, `document_1_type`, `document_1_status`,
                    `document_2_url`, `document_2_type`, `document_2_status`,
                    `total_contributions`, `fcm_token`, `notification_pref`, `created_at`, `updated_at`
                ) VALUES (
                    'member-legacy', 'group-legacy', 'user-legacy', 'member-key-legacy', 'Legacy Member', '9001015000081', '0810000001', 'legacy@example.com',
                    'ACTIVE', '2025-01-01T00:00:00Z', '2025-04-01T00:00:00Z', NULL,
                    NULL, NULL, 'PENDING',
                    NULL, NULL, 'PENDING',
                    7, NULL, 'BOTH', '2025-01-01T00:00:00Z', 123456789
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 35, true, *ALL_MIGRATIONS)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='payouts'")
        assertTrue("Payouts table should exist in version 34", cursor.moveToFirst())
        cursor.close()

        val contribCursor = db.query("PRAGMA table_info(contributions)")
        var hasTypeColumn = false
        var hasPaymentMethodColumn = false
        while (contribCursor.moveToNext()) {
            when (contribCursor.getString(contribCursor.getColumnIndexOrThrow("name"))) {
                "type" -> hasTypeColumn = true
                "payment_method" -> hasPaymentMethodColumn = true
            }
        }
        contribCursor.close()
        assertTrue("Contributions table should have 'type' column in version 34", hasTypeColumn)
        assertTrue("Contributions table should have 'payment_method' column in version 34", hasPaymentMethodColumn)

        val loansCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='loans'")
        assertTrue("Loans table should exist in version 34", loansCursor.moveToFirst())
        loansCursor.close()

        val repaymentsCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='loan_repayments'")
        assertTrue("Loan repayments table should exist in version 34", repaymentsCursor.moveToFirst())
        repaymentsCursor.close()

        val healthScoreCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='group_health_scores'")
        assertTrue("Group health scores table should exist in version 35", healthScoreCursor.moveToFirst())
        healthScoreCursor.close()

        val groupCursor = db.query(
            """
            SELECT `name`, `goal_amount`, `period_months`, `constitution_status`, `max_beneficiaries`, `beneficiary_increase_pct`
            FROM `groups`
            WHERE `id` = 'group-legacy'
            """.trimIndent()
        )
        assertTrue("Legacy group row should survive migration", groupCursor.moveToFirst())
        assertEquals("Legacy Group", groupCursor.getString(0))
        assertEquals(0.0, groupCursor.getDouble(1), 0.0)
        assertEquals(12, groupCursor.getInt(2))
        assertEquals("PENDING", groupCursor.getString(3))
        assertEquals(0, groupCursor.getInt(4))
        assertEquals(0.0, groupCursor.getDouble(5), 0.0)
        groupCursor.close()

        val memberCursor = db.query(
            """
            SELECT `full_name`, `member_key`, `document_3_status`, `document_4_status`, `document_5_status`,
                   `total_paid`, `street`, `suburb`, `city`, `province`
            FROM `members`
            WHERE `id` = 'member-legacy'
            """.trimIndent()
        )
        assertTrue("Legacy member row should survive migration", memberCursor.moveToFirst())
        assertEquals("Legacy Member", memberCursor.getString(0))
        assertEquals("member-key-legacy", memberCursor.getString(1))
        assertEquals("PENDING", memberCursor.getString(2))
        assertEquals("PENDING", memberCursor.getString(3))
        assertEquals("PENDING", memberCursor.getString(4))
        assertEquals(0.0, memberCursor.getDouble(5), 0.0)
        assertEquals("", memberCursor.getString(6))
        assertEquals("", memberCursor.getString(7))
        assertEquals("", memberCursor.getString(8))
        assertEquals("", memberCursor.getString(9))
        memberCursor.close()
    }
}
