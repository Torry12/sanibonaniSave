package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_65_66 = object : Migration(65, 66) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `financial_providers` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `logo_url` TEXT,
                `supported_ops` TEXT NOT NULL,
                `is_active` INTEGER NOT NULL,
                `metadata` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())

        db.safeExec("ALTER TABLE `groups` ADD COLUMN `financial_provider_id` TEXT")
        db.safeExec("ALTER TABLE `groups` ADD COLUMN `financial_provider_metadata` TEXT NOT NULL DEFAULT '{}'")
    }
}

val MIGRATION_66_67 = object : Migration(66, 67) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Loans table updates
        db.safeExec("ALTER TABLE `loans` ADD COLUMN `reviewed_by` TEXT")
        db.safeExec("ALTER TABLE `loans` ADD COLUMN `reviewed_at` TEXT")
        db.safeExec("ALTER TABLE `loans` ADD COLUMN `admin_notes` TEXT")
        db.safeExec("ALTER TABLE `loans` ADD COLUMN `rejection_reason` TEXT")
        db.safeExec("ALTER TABLE `loans` ADD COLUMN `amortization_type` TEXT NOT NULL DEFAULT 'FLAT'")
        db.safeExec("ALTER TABLE `loans` ADD COLUMN `effective_apr` REAL")

        // 2. Payouts table migration (renaming type and adding description)
        db.execSQL("DROP TABLE IF EXISTS `payouts_new`")
        db.execSQL("""
            CREATE TABLE `payouts_new` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `group_id` TEXT NOT NULL,
                `member_id` TEXT,
                `amount` REAL NOT NULL,
                `bank_name` TEXT NOT NULL,
                `account_no` TEXT NOT NULL,
                `branch_code` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `payout_type` TEXT NOT NULL,
                `description` TEXT,
                `processed_by` TEXT,
                `processed_at` TEXT,
                `payout_reference` TEXT,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())

        val typeExpr = when {
            db.tableColumns("payouts").contains("type") -> "`type`"
            db.tableColumns("payouts").contains("payout_type") -> "`payout_type`"
            else -> "'GENERAL'"
        }

        db.execSQL("""
            INSERT INTO `payouts_new` (
                `id`, `group_id`, `member_id`, `amount`, `bank_name`, `account_no`, `branch_code`,
                `status`, `payout_type`, `processed_by`, `processed_at`, `payout_reference`, `created_at`, `updated_at`
            )
            SELECT
                `id`, `group_id`, `member_id`, `amount`, `bank_name`, `account_no`, `branch_code`,
                `status`, $typeExpr, `processed_by`, `processed_at`, `payout_reference`, `created_at`, `updated_at`
            FROM `payouts`
        """.trimIndent())

        db.execSQL("DROP TABLE `payouts`")
        db.execSQL("ALTER TABLE `payouts_new` RENAME TO `payouts`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payouts_group_id` ON `payouts` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payouts_status` ON `payouts` (`status`)")

        // 3. Ledger Checkpoints table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ledger_checkpoints` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `group_id` TEXT NOT NULL,
                `ledger_entry_id` TEXT NOT NULL,
                `balance` REAL NOT NULL,
                `created_at` TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_checkpoints_group_id` ON `ledger_checkpoints` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_checkpoints_ledger_entry_id` ON `ledger_checkpoints` (`ledger_entry_id`)")
    }
}

val MIGRATION_67_68 = object : Migration(67, 68) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Drop existing indexes to avoid conflicts during table recreation
        db.execSQL("DROP INDEX IF EXISTS `index_members_group_id`")
        db.execSQL("DROP INDEX IF EXISTS `index_members_user_id`")
        db.execSQL("DROP INDEX IF EXISTS `index_members_status`")
        db.execSQL("DROP INDEX IF EXISTS `index_members_member_key`")

        // 2. Create the new members table with NULLABLE columns where needed
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `members_new` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `group_id` TEXT NOT NULL,
                `user_id` TEXT,
                `member_key` TEXT,
                `full_name` TEXT NOT NULL,
                `id_number` TEXT,
                `phone` TEXT,
                `email` TEXT,
                `street` TEXT,
                `suburb` TEXT,
                `city` TEXT,
                `province` TEXT,
                `status` TEXT NOT NULL,
                `joined_at` TEXT NOT NULL,
                `probation_end_at` TEXT NOT NULL,
                `profile_photo_url` TEXT,
                `document_1_url` TEXT,
                `document_1_type` TEXT,
                `document_1_status` TEXT NOT NULL,
                `document_2_url` TEXT,
                `document_2_type` TEXT,
                `document_2_status` TEXT NOT NULL,
                `document_3_url` TEXT,
                `document_3_type` TEXT,
                `document_3_status` TEXT NOT NULL,
                `document_4_url` TEXT,
                `document_4_type` TEXT,
                `document_4_status` TEXT NOT NULL,
                `document_5_url` TEXT,
                `document_5_type` TEXT,
                `document_5_status` TEXT NOT NULL,
                `contract_url` TEXT,
                `contract_status` TEXT NOT NULL,
                `bank_name` TEXT,
                `account_no` TEXT,
                `branch_code` TEXT,
                `beneficiary_count` INTEGER,
                `beneficiary_over_65_count` INTEGER,
                `monthly_contribution_override` REAL,
                `total_contributions` INTEGER,
                `total_paid` REAL NOT NULL DEFAULT 0.0,
                `fcm_token` TEXT,
                `notification_pref` TEXT NOT NULL,
                `rosca_position` INTEGER,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())

        // 3. Transfer data from old table to new table
        db.execSQL("""
            INSERT INTO `members_new` (
                `id`, `group_id`, `user_id`, `member_key`, `full_name`, `id_number`, `phone`, `email`,
                `street`, `suburb`, `city`, `province`, `status`, `joined_at`, `probation_end_at`,
                `profile_photo_url`, `document_1_url`, `document_1_type`, `document_1_status`,
                `document_2_url`, `document_2_type`, `document_2_status`, `document_3_url`,
                `document_3_type`, `document_3_status`, `document_4_url`, `document_4_type`,
                `document_4_status`, `document_5_url`, `document_5_type`, `document_5_status`,
                `contract_url`, `contract_status`, `bank_name`, `account_no`, `branch_code`,
                `beneficiary_count`, `beneficiary_over_65_count`, `monthly_contribution_override`,
                `total_contributions`, `total_paid`, `fcm_token`, `notification_pref`,
                `rosca_position`, `created_at`, `updated_at`
            )
            SELECT
                `id`, `group_id`, `user_id`, `member_key`, `full_name`, `id_number`, `phone`, `email`,
                `street`, `suburb`, `city`, `province`, `status`, `joined_at`, `probation_end_at`,
                `profile_photo_url`, `document_1_url`, `document_1_type`, `document_1_status`,
                `document_2_url`, `document_2_type`, `document_2_status`, `document_3_url`,
                `document_3_type`, `document_3_status`, `document_4_url`, `document_4_type`,
                `document_4_status`, `document_5_url`, `document_5_type`, `document_5_status`,
                `contract_url`, `contract_status`, `bank_name`, `account_no`, `branch_code`,
                `beneficiary_count`, `beneficiary_over_65_count`, `monthly_contribution_override`,
                `total_contributions`, `total_paid`, `fcm_token`, `notification_pref`,
                `rosca_position`, `created_at`, `updated_at`
            FROM `members`
        """.trimIndent())

        // 4. Swap tables and restore indexes
        db.execSQL("DROP TABLE `members`")
        db.execSQL("ALTER TABLE `members_new` RENAME TO `members`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_group_id` ON `members` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_user_id` ON `members` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_status` ON `members` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_members_member_key` ON `members` (`member_key`)")
    }
}

val MIGRATION_68_69 = object : Migration(68, 69) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Bridge migration for schema hardening
    }
}

val MIGRATION_69_70 = object : Migration(69, 70) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `user_profiles` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `full_name` TEXT,
                `email` TEXT,
                `role` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}
