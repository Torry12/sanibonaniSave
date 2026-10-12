package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- Members Table Healing ---
        // Document slots (ensuring 3, 4, and 5 exist)
        db.safeExec("ALTER TABLE members ADD COLUMN document_3_url TEXT")
        db.safeExec("ALTER TABLE members ADD COLUMN document_3_type TEXT")
        db.safeExec("ALTER TABLE members ADD COLUMN document_3_status TEXT NOT NULL DEFAULT 'PENDING'")

        db.safeExec("ALTER TABLE members ADD COLUMN document_4_url TEXT")
        db.safeExec("ALTER TABLE members ADD COLUMN document_4_type TEXT")
        db.safeExec("ALTER TABLE members ADD COLUMN document_4_status TEXT NOT NULL DEFAULT 'PENDING'")

        db.safeExec("ALTER TABLE members ADD COLUMN document_5_url TEXT")
        db.safeExec("ALTER TABLE members ADD COLUMN document_5_type TEXT")
        db.safeExec("ALTER TABLE members ADD COLUMN document_5_status TEXT NOT NULL DEFAULT 'PENDING'")

        // Burial society specific fields (healing from 18-19)
        db.safeExec("ALTER TABLE members ADD COLUMN beneficiary_count INTEGER NOT NULL DEFAULT 0")
        db.safeExec("ALTER TABLE members ADD COLUMN beneficiary_over_65_count INTEGER NOT NULL DEFAULT 0")
        db.safeExec("ALTER TABLE members ADD COLUMN monthly_contribution_override REAL")

        // Legacy/Missing fields
        db.safeExec("ALTER TABLE members ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

        // --- Groups Table Healing ---
        // Burial society fields (healing from 18-19)
        db.safeExec("ALTER TABLE groups ADD COLUMN beneficiary_increase_pct REAL NOT NULL DEFAULT 0.0")
        db.safeExec("ALTER TABLE groups ADD COLUMN max_beneficiaries INTEGER NOT NULL DEFAULT 0")

        // Version 20-21 fields
        db.safeExec("ALTER TABLE groups ADD COLUMN registration_paid INTEGER NOT NULL DEFAULT 0")
        db.safeExec("ALTER TABLE groups ADD COLUMN is_platform_suspended INTEGER NOT NULL DEFAULT 0")
        db.safeExec("ALTER TABLE groups ADD COLUMN goal_amount REAL NOT NULL DEFAULT 0.0")
        db.safeExec("ALTER TABLE groups ADD COLUMN period_months INTEGER NOT NULL DEFAULT 12")

        // Legacy/Missing fields
        db.safeExec("ALTER TABLE groups ADD COLUMN gateway_public_key TEXT")
        db.safeExec("ALTER TABLE groups ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

        // --- Indices Healing ---
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_members_user_id` ON `members` (`user_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_members_status` ON `members` (`status`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_groups_is_public` ON `groups` (`is_public`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_groups_admin_user_id` ON `groups` (`admin_user_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_groups_fee_status` ON `groups` (`fee_status`)")

        // --- Ensure essential tables exist (healing for gaps) ---
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `beneficiaries` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL,
                `full_name` TEXT NOT NULL,
                `id_number` TEXT,
                `relationship` TEXT,
                `date_of_birth` TEXT,
                `is_over_65` INTEGER NOT NULL,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`, `group_id`, `member_id`)
            )
        """.trimIndent())

        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `contributions` (
                `id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `policy_id` TEXT,
                `amount` REAL NOT NULL,
                `created_at` TEXT,
                `due_date` TEXT NOT NULL,
                `paid_at` TEXT,
                `status` TEXT NOT NULL,
                `late_fees_applied` INTEGER NOT NULL,
                `yoco_transaction_id` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `payments` (
                `id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `payment_type` TEXT NOT NULL,
                `payment_method` TEXT NOT NULL,
                `transaction_id` TEXT,
                `status` TEXT NOT NULL,
                `processed_at` TEXT,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        // --- Notifications Table Healing ---
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `notifications` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `member_id` TEXT,
                `message` TEXT NOT NULL,
                `channel` TEXT NOT NULL,
                `trigger_event` TEXT NOT NULL,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_group_id` ON `notifications` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_member_id` ON `notifications` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_trigger_event` ON `notifications` (`trigger_event`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- Members Table Updates ---
        db.safeExec("ALTER TABLE members ADD COLUMN member_key TEXT")
        db.safeExec("CREATE UNIQUE INDEX IF NOT EXISTS `index_members_member_key` ON `members` (`member_key`)")

        // --- Groups Table Updates ---
        db.safeExec("ALTER TABLE groups ADD COLUMN latitude REAL")
        db.safeExec("ALTER TABLE groups ADD COLUMN longitude REAL")

        // --- Notifications Table Healing (ensure it exists for v22) ---
        // If it exists but is corrupted (0 columns), we try to heal it.
        // Room sometimes reports 0 columns if the table creation was interrupted.
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `notifications` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `member_id` TEXT,
                `message` TEXT NOT NULL,
                `channel` TEXT NOT NULL,
                `trigger_event` TEXT NOT NULL,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_group_id` ON `notifications` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_member_id` ON `notifications` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_trigger_event` ON `notifications` (`trigger_event`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)")
    }
}


val MIGRATION_22_23 = object : Migration(22, 23) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_23_24 = object : Migration(23, 24) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.recreateMembersTablePreservingData()
    }
}
