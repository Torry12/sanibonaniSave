package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create burial_claims table for beneficiary payout claims
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `burial_claims` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL,
                `beneficiary_id` TEXT NOT NULL,
                `beneficiary_name` TEXT NOT NULL,
                `cause_of_death` TEXT NOT NULL,
                `date_of_death` TEXT NOT NULL,
                `claim_amount` REAL NOT NULL,
                `bank_name` TEXT NOT NULL,
                `account_no` TEXT NOT NULL,
                `branch_code` TEXT NOT NULL,
                `account_holder` TEXT NOT NULL,
                `notes` TEXT,
                `status` TEXT NOT NULL DEFAULT 'SUBMITTED',
                `reviewed_by` TEXT,
                `reviewed_at` TEXT,
                `admin_notes` TEXT,
                `rejection_reason` TEXT,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_burial_claims_group_id` ON `burial_claims` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_burial_claims_member_id` ON `burial_claims` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_burial_claims_beneficiary_id` ON `burial_claims` (`beneficiary_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_burial_claims_status` ON `burial_claims` (`status`)")
    }
}

val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Reserved bridge migration for environments already at v36.
    }
}

val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("ALTER TABLE `groups` ADD COLUMN `rosca_rotation_method` TEXT NOT NULL DEFAULT 'FIXED'")
    }
}

val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create member_behavior_tracks table
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `member_behavior_tracks` (
                `id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL,
                `member_id_number` TEXT NOT NULL,
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
                `joined_at` TEXT NOT NULL,
                `last_activity_at` TEXT NOT NULL,
                `last_contribution_at` TEXT,
                `admin_notes` TEXT,
                `last_reviewed_at` TEXT,
                `reviewed_by` TEXT,
                `created_at` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.safeExec("CREATE UNIQUE INDEX IF NOT EXISTS `index_member_behavior_tracks_member_id` ON `member_behavior_tracks` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_member_behavior_tracks_member_id_number` ON `member_behavior_tracks` (`member_id_number`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_member_behavior_tracks_group_id` ON `member_behavior_tracks` (`group_id`)")

        // Create fraud_detection_events table
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `fraud_detection_events` (
                `id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `event_type` TEXT NOT NULL,
                `severity` TEXT NOT NULL,
                `details_json` TEXT NOT NULL,
                `action_taken` TEXT,
                `resolved` INTEGER NOT NULL,
                `created_at` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.safeExec("CREATE INDEX IF NOT EXISTS `index_fraud_detection_events_member_id` ON `fraud_detection_events` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_fraud_detection_events_group_id` ON `fraud_detection_events` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_fraud_detection_events_severity` ON `fraud_detection_events` (`severity`)")

        // Create behavior_analytics_summaries table
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `behavior_analytics_summaries` (
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

        db.safeExec("CREATE UNIQUE INDEX IF NOT EXISTS `index_behavior_analytics_summaries_group_id` ON `behavior_analytics_summaries` (`group_id`)")
    }
}

val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `payouts_new` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `bank_name` TEXT NOT NULL,
                `account_no` TEXT NOT NULL,
                `branch_code` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `processed_by` TEXT,
                `processed_at` TEXT,
                `payout_reference` TEXT,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.safeExec("""
            INSERT INTO `payouts_new` (
                `id`, `group_id`, `amount`, `bank_name`, `account_no`, `branch_code`,
                `status`, `processed_by`, `processed_at`, `payout_reference`, `created_at`, `updated_at`
            )
            SELECT
                `id`, `group_id`, `amount`, `bank_name`, `account_no`, `branch_code`,
                `status`, `processed_by`, `processed_at`, `yoco_payout_id`, `created_at`, `updated_at`
            FROM `payouts`
        """.trimIndent())
        db.safeExec("DROP TABLE `payouts`")
        db.safeExec("ALTER TABLE `payouts_new` RENAME TO `payouts`")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_payouts_group_id` ON `payouts` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_payouts_status` ON `payouts` (`status`)")
    }
}
