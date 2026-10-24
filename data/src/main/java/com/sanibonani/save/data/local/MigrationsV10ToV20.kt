package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_10_11 = object : Migration(10, 11) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_11_12 = object : Migration(11, 12) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_12_13 = object : Migration(12, 13) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_13_14 = object : Migration(13, 14) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_14_15 = object : Migration(14, 15) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_15_16 = object : Migration(15, 16) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_16_17 = object : Migration(16, 17) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_17_18 = object : Migration(17, 18) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Groups table updates
        db.safeExec("ALTER TABLE groups ADD COLUMN beneficiary_increase_pct REAL NOT NULL DEFAULT 0.0")
        db.safeExec("ALTER TABLE groups ADD COLUMN max_beneficiaries INTEGER NOT NULL DEFAULT 0")

        // Members table updates
        db.safeExec("ALTER TABLE members ADD COLUMN document_3_url TEXT")
        db.safeExec("ALTER TABLE members ADD COLUMN document_3_type TEXT")
        db.safeExec("ALTER TABLE members ADD COLUMN document_3_status TEXT NOT NULL DEFAULT 'PENDING'")
        db.safeExec("ALTER TABLE members ADD COLUMN beneficiary_count INTEGER NOT NULL DEFAULT 0")
        db.safeExec("ALTER TABLE members ADD COLUMN beneficiary_over_65_count INTEGER NOT NULL DEFAULT 0")
        db.safeExec("ALTER TABLE members ADD COLUMN monthly_contribution_override REAL")

        // Create missing tables (handling previous migration gaps)
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
        """)

        db.safeExec("CREATE INDEX IF NOT EXISTS `index_beneficiaries_group_id` ON `beneficiaries` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_beneficiaries_member_id` ON `beneficiaries` (`member_id`)")

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
        """)

        db.safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_member_id` ON `contributions` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_group_id` ON `contributions` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_status` ON `contributions` (`status`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_due_date` ON `contributions` (`due_date`)")

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
        """)

        db.safeExec("CREATE INDEX IF NOT EXISTS `index_payments_member_id` ON `payments` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_payments_group_id` ON `payments` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_payments_status` ON `payments` (`status`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_payments_payment_type` ON `payments` (`payment_type`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_payments_created_at` ON `payments` (`created_at`)")

        // Create notifications table
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
        """)

        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_group_id` ON `notifications` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_member_id` ON `notifications` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_trigger_event` ON `notifications` (`trigger_event`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)")
    }
}


val MIGRATION_19_20 = object : Migration(19, 20) { override fun migrate(db: SupportSQLiteDatabase) {} }
