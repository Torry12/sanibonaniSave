package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Restore precision-safe contribution Money columns and align nullable timestamps with Room. */
val MIGRATION_80_81 = object : Migration(80, 81) {
    override fun migrate(db: SupportSQLiteDatabase) {
        runCatching { db.execSQL("ALTER TABLE `groups` ADD COLUMN `burial_claim_amount` TEXT NOT NULL DEFAULT '10000.00'") }
        runCatching { db.execSQL("ALTER TABLE `groups` ADD COLUMN `min_members_for_claims` INTEGER NOT NULL DEFAULT 0") }
        runCatching { db.execSQL("ALTER TABLE `groups` ADD COLUMN `min_reserve_percentage` REAL NOT NULL DEFAULT 0.0") }

        val sourceColumns = db.tableColumns("contributions")
        if (sourceColumns.isEmpty()) return

        db.execSQL("ALTER TABLE `contributions` RENAME TO `contributions_v80`")
        db.execSQL("""
            CREATE TABLE `contributions` (
                `id` TEXT NOT NULL, `member_id` TEXT NOT NULL, `group_id` TEXT NOT NULL,
                `policy_id` TEXT, `amount` TEXT NOT NULL, `created_at` TEXT,
                `due_date` TEXT NOT NULL, `paid_at` TEXT, `status` TEXT NOT NULL,
                `type` TEXT NOT NULL DEFAULT 'contribution',
                `payment_method` TEXT NOT NULL DEFAULT 'bank',
                `late_fees_applied` INTEGER NOT NULL DEFAULT 0,
                `late_fee_amount` TEXT NOT NULL, `principal_amount` TEXT NOT NULL,
                `transaction_id` TEXT, `updated_at` TEXT, PRIMARY KEY(`id`)
            )
        """.trimIndent())

        fun source(name: String, fallback: String = "NULL"): String =
            if (name in sourceColumns) "`$name`" else fallback
        fun money(name: String, fallback: String): String =
            if (name in sourceColumns) "CAST(`$name` AS TEXT)" else fallback
        val fields = listOf(
            "id" to source("id", "''"),
            "member_id" to source("member_id", "''"),
            "group_id" to source("group_id", "''"),
            "policy_id" to source("policy_id"),
            "amount" to money("amount", "'0.00'"),
            "created_at" to source("created_at"),
            "due_date" to source("due_date", "''"),
            "paid_at" to source("paid_at"),
            "status" to source("status", "'PENDING'"),
            "type" to source("type", "'contribution'"),
            "payment_method" to source("payment_method", "'bank'"),
            "late_fees_applied" to source("late_fees_applied", "0"),
            "late_fee_amount" to money("late_fee_amount", "'0.00'"),
            "principal_amount" to money("principal_amount", "'0.00'"),
            "transaction_id" to source("transaction_id"),
            "updated_at" to source("updated_at")
        )
        db.execSQL(
            "INSERT INTO `contributions` (${fields.joinToString { "`${it.first}`" }}) " +
                "SELECT ${fields.joinToString { it.second }} FROM `contributions_v80`"
        )
        db.execSQL("DROP TABLE `contributions_v80`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contributions_member_id` ON `contributions` (`member_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contributions_group_id` ON `contributions` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contributions_status` ON `contributions` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_contributions_due_date` ON `contributions` (`due_date`)")
    }
}
