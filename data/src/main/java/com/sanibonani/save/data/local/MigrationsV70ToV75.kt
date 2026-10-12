package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_70_71 = object : Migration(70, 71) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Structural refactor migration (no schema changes, just internal cleanup)
    }
}

val MIGRATION_71_72 = object : Migration(71, 72) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Alignment migration for Behavior Tracking and Consolidated Ledger View
        db.execSQL("DROP TABLE IF EXISTS `member_behavior_tracks`")
        db.execSQL("DROP TABLE IF EXISTS `behavior_analytics_summaries`")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `member_behavior_track` (
                `member_id` TEXT NOT NULL PRIMARY KEY,
                `member_id_number` TEXT,
                `group_id` TEXT NOT NULL,
                `total_contributions` INTEGER NOT NULL,
                `on_time_contributions` INTEGER NOT NULL,
                `late_contributions` INTEGER NOT NULL,
                `overdue_count` INTEGER NOT NULL,
                `missed_contributions` INTEGER NOT NULL,
                `payment_consistency_score` REAL NOT NULL,
                `average_days_late` REAL NOT NULL,
                `current_payment_streak` INTEGER NOT NULL,
                `longest_payment_streak` INTEGER NOT NULL,
                `has_broken_streak_recently` INTEGER NOT NULL,
                `total_amount_contributed` REAL NOT NULL,
                `total_late_fees_paid` REAL NOT NULL,
                `pending_late_fees` REAL NOT NULL,
                `total_outstanding_amount` REAL NOT NULL,
                `total_loans_requested` INTEGER NOT NULL,
                `total_loans_approved` INTEGER NOT NULL,
                `total_loans_completed` INTEGER NOT NULL,
                `active_loans` INTEGER NOT NULL,
                `overdue_loans` INTEGER NOT NULL,
                `loan_default_count` INTEGER NOT NULL,
                `loan_completion_rate` REAL NOT NULL,
                `duplicate_transaction_count` INTEGER NOT NULL,
                `suspicious_activity_count` INTEGER NOT NULL,
                `unusual_payment_patterns` INTEGER NOT NULL,
                `multiple_accounts_detected` INTEGER NOT NULL,
                `velocity_check_failed` INTEGER NOT NULL,
                `rapid_disbursement_attempts` INTEGER NOT NULL,
                `member_status` TEXT NOT NULL,
                `fraud_risk_level` TEXT NOT NULL,
                `fraud_score` REAL NOT NULL,
                `behavior_score` REAL NOT NULL,
                `is_flagged_for_review` INTEGER NOT NULL,
                `is_suspended` INTEGER NOT NULL,
                `suspension_reason` TEXT,
                `review_notes` TEXT,
                `months_in_group` INTEGER NOT NULL,
                `joined_at` TEXT,
                `last_activity_at` TEXT,
                `last_contribution_at` TEXT,
                `admin_notes` TEXT,
                `last_reviewed_at` TEXT,
                `reviewed_by` TEXT,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `behavior_analytics_summary` (
                `group_id` TEXT NOT NULL PRIMARY KEY,
                `total_members_tracked` INTEGER NOT NULL,
                `excellent_members` INTEGER NOT NULL,
                `good_members` INTEGER NOT NULL,
                `fair_members` INTEGER NOT NULL,
                `poor_members` INTEGER NOT NULL,
                `suspended_members` INTEGER NOT NULL,
                `high_fraud_risk_count` INTEGER NOT NULL,
                `flagged_members_count` INTEGER NOT NULL,
                `average_behavior_score` REAL NOT NULL,
                `average_fraud_score` REAL NOT NULL,
                `on_time_payment_rate` REAL NOT NULL,
                `loan_default_rate` REAL NOT NULL,
                `calculated_at` TEXT NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_72_73 = object : Migration(72, 73) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create the new beneficiary_payout_claims table without the non-existent URL columns
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `beneficiary_payout_claims_new` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `group_id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL,
                `beneficiary_id` TEXT NOT NULL,
                `beneficiary_name` TEXT NOT NULL,
                `face_photo_url` TEXT,
                `beneficiary_id_url` TEXT,
                `death_certificate_url` TEXT,
                `member_id_url` TEXT,
                `cause_of_death` TEXT NOT NULL,
                `date_of_death` TEXT NOT NULL,
                `claim_amount` REAL NOT NULL,
                `bank_name` TEXT NOT NULL,
                `account_no` TEXT NOT NULL,
                `branch_code` TEXT NOT NULL,
                `account_holder` TEXT NOT NULL,
                `notes` TEXT,
                `status` TEXT NOT NULL,
                `reviewed_by` TEXT,
                `reviewed_at` TEXT,
                `admin_notes` TEXT,
                `rejection_reason` TEXT,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())

        // 2. Transfer data if the old table exists
        if (db.tableExists("burial_claims")) {
            db.execSQL("""
                INSERT INTO `beneficiary_payout_claims_new` (
                    `id`, `group_id`, `member_id`, `beneficiary_id`, `beneficiary_name`,
                    `face_photo_url`, `cause_of_death`, `date_of_death`, `claim_amount`,
                    `bank_name`, `account_no`, `branch_code`, `account_holder`, `notes`,
                    `status`, `reviewed_by`, `reviewed_at`, `admin_notes`, `rejection_reason`,
                    `created_at`, `updated_at`
                )
                SELECT
                    `id`, `group_id`, `member_id`, `beneficiary_id`, `beneficiary_name`,
                    `face_photo_url`, `cause_of_death`, `date_of_death`, `claim_amount`,
                    `bank_name`, `account_no`, `branch_code`, `account_holder`, `notes`,
                    `status`, `reviewed_by`, `reviewed_at`, `admin_notes`, `rejection_reason`,
                    `created_at`, `updated_at`
                FROM `burial_claims`
            """.trimIndent())
            db.execSQL("DROP TABLE `burial_claims`")
        }

        // 3. Finalize table and indices
        db.execSQL("ALTER TABLE `beneficiary_payout_claims_new` RENAME TO `beneficiary_payout_claims`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_beneficiary_payout_claims_group_id` ON `beneficiary_payout_claims` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_beneficiary_payout_claims_member_id` ON `beneficiary_payout_claims` (`member_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_beneficiary_payout_claims_beneficiary_id` ON `beneficiary_payout_claims` (`beneficiary_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_beneficiary_payout_claims_status` ON `beneficiary_payout_claims` (`status`)")
    }
}

val MIGRATION_73_74 = object : Migration(73, 74) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pending_operations` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `type` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `group_id` TEXT,
                `created_at` INTEGER NOT NULL,
                `retry_count` INTEGER NOT NULL DEFAULT 0,
                `last_error` TEXT
            )
        """.trimIndent())
    }
}

val MIGRATION_74_75 = object : Migration(74, 75) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("ALTER TABLE `audit_logs` ADD COLUMN `related_id` TEXT")
    }
}
