package com.sanibonani.save

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanibonani.save.data.local.ALL_MIGRATIONS
import com.sanibonani.save.data.local.SanibonaniDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"
    private val FINANCE_DB = "migration-finance-test"

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUpHilt() {
        hiltRule.inject()
    }

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
        helper.createDatabase(TEST_DB, 31).apply {
            execSQL(
                """
                INSERT INTO `groups` (
                    `id`, `name`, `type`, `province`, `city`, `township`, `description`, `logo_emoji`,
                    `joining_fee`, `monthly_contribution`, `late_fee`, `late_fee_grace_days`, `probation_months`,
                    `payment_due_day`, `max_members`, `current_members`, `is_public`, `allow_partial_payment`,
                    `auto_suspend_after`, `bank_name`, `account_number`, `branch_code`, `account_type`,
                    `gateway_public_key`, `balance`, `admin_user_id`, `fee_status`, `registration_paid`,
                    `is_platform_suspended`, `goal_amount`, `period_months`, `constitution_status`,
                    `max_beneficiaries`, `beneficiary_increase_pct`, `created_at`, `latitude`, `longitude`, `geohash`, `updated_at`
                ) VALUES (
                    'group-legacy', 'Legacy Group', 'OTHER', 'Gauteng', 'Johannesburg', 'Soweto', 'Legacy row', '🤝',
                    120.0, 250.0, 15.0, 5, 3, 28, 50, 1, 1, 0,
                    2, 'Legacy Bank', '123456789', '250655', 'Savings',
                    NULL, 500.0, 'admin-legacy', 'DUE', 1,
                    0, 50000.0, 12, 'PENDING',
                    5, 10.0, '2025-01-01T00:00:00Z', -26.2041, 28.0473, 'kekjd', 123456789
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `members` (
                    `id`, `group_id`, `user_id`, `member_key`, `full_name`, `id_number`, `phone`, `email`,
                    `street`, `suburb`, `city`, `province`,
                    `status`, `joined_at`, `probation_end_at`, `profile_photo_url`,
                    `document_1_url`, `document_1_type`, `document_1_status`,
                    `document_2_url`, `document_2_type`, `document_2_status`,
                    `document_3_status`, `document_4_status`, `document_5_status`,
                    `total_contributions`, `total_paid`, `fcm_token`, `notification_pref`, `created_at`, `updated_at`
                ) VALUES (
                    'member-legacy', 'group-legacy', 'user-legacy', 'member-key-legacy', 'Legacy Member', '9001015000081', '0810000001', 'legacy@example.com',
                    '123 Main St', 'Soweto', 'Johannesburg', 'Gauteng',
                    'ACTIVE', '2025-01-01T00:00:00Z', '2025-04-01T00:00:00Z', NULL,
                    NULL, NULL, 'PENDING',
                    NULL, NULL, 'PENDING',
                    'PENDING', 'PENDING', 'PENDING',
                    7, 1750.0, NULL, 'BOTH', '2025-01-01T00:00:00Z', 123456789
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `payments` (
                    `id`, `member_id`, `group_id`, `amount`, `payment_type`, `payment_method`,
                    `transaction_id`, `status`, `processed_at`, `created_at`, `updated_at`
                ) VALUES (
                    'payment-legacy', 'member-legacy', 'group-legacy', 1750.5, 'CONTRIBUTION', 'BANK',
                    'tx-legacy', 'COMPLETED', '2025-01-02T00:00:00Z', '2025-01-01T00:00:00Z', 123456789
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `payouts` (
                    `id`, `group_id`, `amount`, `bank_name`, `account_no`, `branch_code`,
                    `status`, `processed_by`, `processed_at`, `yoco_payout_id`, `created_at`, `updated_at`
                ) VALUES (
                    'payout-legacy', 'group-legacy', 2500.75, 'Legacy Bank', '987654321', '250655',
                    'COMPLETED', 'admin-legacy', '2025-01-03T00:00:00Z', NULL, '2025-01-01T00:00:00Z', 123456789
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 82, true, *ALL_MIGRATIONS)

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
        assertEquals(50000.0, groupCursor.getDouble(1), 0.0)
        assertEquals(12, groupCursor.getInt(2))
        assertEquals("PENDING", groupCursor.getString(3))
        assertEquals(5, groupCursor.getInt(4))
        assertEquals(10.0, groupCursor.getDouble(5), 0.0)
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
        assertEquals(1750.0, memberCursor.getDouble(5), 0.0)
        assertEquals("123 Main St", memberCursor.getString(6))
        assertEquals("Soweto", memberCursor.getString(7))
        assertEquals("Johannesburg", memberCursor.getString(8))
        assertEquals("Gauteng", memberCursor.getString(9))
        memberCursor.close()

        val paymentCursor = db.query(
            """
            SELECT `amount`, typeof(`amount`), `payment_type`, `status`
            FROM `payments`
            WHERE `id` = 'payment-legacy'
            """.trimIndent()
        )
        assertTrue("Legacy payment row should survive migration", paymentCursor.moveToFirst())
        assertEquals("Legacy REAL amount should become a 2-dp Money string", "1750.50", paymentCursor.getString(0))
        assertEquals("Payments amount should be stored as TEXT", "text", paymentCursor.getString(1))
        assertEquals("CONTRIBUTION", paymentCursor.getString(2))
        assertEquals("COMPLETED", paymentCursor.getString(3))
        paymentCursor.close()

        val payoutCursor = db.query(
            """
            SELECT `amount`, typeof(`amount`), `payout_type`, `supporting_document_status`,
                   `supporting_document_url`, `updated_at`, typeof(`updated_at`)
            FROM `payouts`
            WHERE `id` = 'payout-legacy'
            """.trimIndent()
        )
        assertTrue("Legacy payout row should survive migration", payoutCursor.moveToFirst())
        assertEquals("2500.75", payoutCursor.getString(0))
        assertEquals("text", payoutCursor.getString(1))
        assertEquals("GENERAL", payoutCursor.getString(2))
        assertEquals("PENDING", payoutCursor.getString(3))
        assertEquals(null, payoutCursor.getString(4))
        assertEquals("123456789", payoutCursor.getString(5))
        assertEquals("text", payoutCursor.getString(6))
        payoutCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrateFinanceTables() {
        helper.createDatabase(FINANCE_DB, 41).apply {
            execSQL(
                """
                INSERT INTO `loans` (
                    `id`, `member_id`, `group_id`, `amount`, `interest_rate`, `total_to_repay`,
                    `total_repaid`, `monthly_repayment`, `start_date`, `end_date`, `next_payment_date`,
                    `status`, `purpose`, `created_at`, `updated_at`
                ) VALUES (
                    'loan-legacy', 'member-legacy', 'group-legacy', 5000.25, 10.0, 5500.5,
                    0.0, 1833.5, '2025-01-01', '2025-04-01', NULL,
                    'ACTIVE', 'Legacy loan', '2025-01-01T00:00:00Z', 111
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `loan_repayments` (
                    `id`, `loan_id`, `member_id`, `group_id`, `amount`, `paid_at`,
                    `payment_method`, `transaction_id`, `created_at`, `updated_at`
                ) VALUES (
                    'repayment-legacy', 'loan-legacy', 'member-legacy', 'group-legacy', 550.75,
                    '2025-02-01T00:00:00Z', 'BANK', 'tx-repay', '2025-02-01T00:00:00Z', 222
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `payouts` (
                    `id`, `group_id`, `amount`, `bank_name`, `account_no`, `branch_code`,
                    `status`, `processed_by`, `processed_at`, `payout_reference`, `created_at`, `updated_at`
                ) VALUES (
                    'payout-finance', 'group-legacy', 1200.5, 'Legacy Bank', '987654321', '250655',
                    'COMPLETED', 'admin-legacy', '2025-01-03T00:00:00Z', 'REF-1', '2025-01-01T00:00:00Z', 333
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `group_ledger` (
                    `id`, `groupId`, `transactionId`, `amount`, `balanceAfter`,
                    `description`, `category`, `createdAt`, `updatedAt`
                ) VALUES (
                    'ledger-legacy', 'group-legacy', 'tx-ledger', 100.0, 900.0,
                    'Legacy entry', 'CONTRIBUTION', '2025-01-01T00:00:00Z', 444
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(FINANCE_DB, 82, true, *ALL_MIGRATIONS)

        val loanCursor = db.query(
            "SELECT `amount`, typeof(`amount`), `total_to_repay`, `monthly_repayment`, `start_date`, `updated_at`, typeof(`updated_at`) FROM `loans` WHERE `id` = 'loan-legacy'"
        )
        assertTrue("Legacy loan row should survive migration", loanCursor.moveToFirst())
        assertEquals("5000.25", loanCursor.getString(0))
        assertEquals("text", loanCursor.getString(1))
        assertEquals("5500.50", loanCursor.getString(2))
        assertEquals("1833.50", loanCursor.getString(3))
        assertEquals("2025-01-01", loanCursor.getString(4))
        assertEquals("111", loanCursor.getString(5))
        assertEquals("text", loanCursor.getString(6))
        loanCursor.close()

        val repaymentCursor = db.query(
            "SELECT `amount`, typeof(`amount`) FROM `loan_repayments` WHERE `id` = 'repayment-legacy'"
        )
        assertTrue("Legacy loan repayment row should survive migration", repaymentCursor.moveToFirst())
        assertEquals("550.75", repaymentCursor.getString(0))
        assertEquals("text", repaymentCursor.getString(1))
        repaymentCursor.close()

        val payoutCursor = db.query(
            "SELECT `amount`, typeof(`amount`) FROM `payouts` WHERE `id` = 'payout-finance'"
        )
        assertTrue("Legacy finance payout should survive migration", payoutCursor.moveToFirst())
        assertEquals("1200.50", payoutCursor.getString(0))
        assertEquals("text", payoutCursor.getString(1))
        payoutCursor.close()

        val ledgerCursor = db.query(
            "SELECT `group_id`, `amount`, `balance_after`, `is_platform` FROM `group_ledger` WHERE `id` = 'ledger-legacy'"
        )
        assertTrue("Legacy ledger row should survive migration", ledgerCursor.moveToFirst())
        assertEquals("group-legacy", ledgerCursor.getString(0))
        assertEquals("100.00", ledgerCursor.getString(1))
        assertEquals("900.00", ledgerCursor.getString(2))
        assertEquals(0, ledgerCursor.getInt(3))
        ledgerCursor.close()
    }
}
