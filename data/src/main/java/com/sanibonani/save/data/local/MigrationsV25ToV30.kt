package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.recreateMembersTablePreservingData()

        // Ensure notifications table exists
        db.execSQL("""
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_group_id` ON `notifications` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_member_id` ON `notifications` (`member_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_trigger_event` ON `notifications` (`trigger_event`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create payouts table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `payouts` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `bank_name` TEXT NOT NULL,
                `account_no` TEXT NOT NULL,
                `branch_code` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `processed_by` TEXT,
                `processed_at` TEXT,
                `yoco_payout_id` TEXT,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payouts_group_id` ON `payouts` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payouts_status` ON `payouts` (`status`)")
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create member_documents table (missing in previous version)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `member_documents` (
                `id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `document_url` TEXT NOT NULL,
                `document_type` TEXT,
                `status` TEXT NOT NULL,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_member_documents_member_id` ON `member_documents` (`member_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_member_documents_group_id` ON `member_documents` (`group_id`)")
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE contributions ADD COLUMN type TEXT NOT NULL DEFAULT 'contribution'")
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema changes between 29 and 30, but version was bumped in SanibonaniDatabase
    }
}
