package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_60_61 = object : Migration(60, 61) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("ALTER TABLE `group_ledger` ADD COLUMN `is_platform` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_61_62 = object : Migration(61, 62) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `audit_logs` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `actor_id` TEXT NOT NULL,
                `target_member_id` TEXT,
                `target_group_id` TEXT,
                `action` TEXT NOT NULL,
                `details` TEXT,
                `created_at` TEXT
            )
        """.trimIndent())
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_audit_logs_actor_id` ON `audit_logs` (`actor_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_audit_logs_target_member_id` ON `audit_logs` (`target_member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_audit_logs_target_group_id` ON `audit_logs` (`target_group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_audit_logs_action` ON `audit_logs` (`action`)")
    }
}

val MIGRATION_62_63 = object : Migration(62, 63) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `audit_reports` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `type` TEXT NOT NULL,
                `targetId` TEXT NOT NULL,
                `contentJson` TEXT NOT NULL,
                `generatedAt` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_63_64 = object : Migration(63, 64) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("ALTER TABLE `platform_fee_config` ADD COLUMN `shortfallRecoveryFee` REAL NOT NULL DEFAULT 5.0")
    }
}

val MIGRATION_64_65 = object : Migration(64, 65) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Healing migration: Ensure all essential indices for financial performance are present.
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_audit_reports_type_targetId` ON `audit_reports` (`type`, `targetId`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_member_behavior_tracks_group_id` ON `member_behavior_tracks` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_group_ledger_is_platform` ON `group_ledger` (`is_platform`)")
    }
}
