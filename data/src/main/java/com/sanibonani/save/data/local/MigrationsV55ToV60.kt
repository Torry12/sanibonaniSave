package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_55_56 = object : Migration(55, 56) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `bank_transactions` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `group_id` TEXT NOT NULL,
                `bank_reference` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `transaction_date` TEXT NOT NULL,
                `description` TEXT,
                `status` TEXT NOT NULL,
                `reconciled_with` TEXT,
                `created_at` TEXT
            )
        """.trimIndent())
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_bank_transactions_group_id` ON `bank_transactions` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_bank_transactions_status` ON `bank_transactions` (`status`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_bank_transactions_bank_reference` ON `bank_transactions` (`bank_reference`)")
    }
}

val MIGRATION_56_57 = object : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Fix group_ledger: make group_id nullable
        db.execSQL("DROP TABLE IF EXISTS `group_ledger_new`")
        db.execSQL("""
            CREATE TABLE `group_ledger_new` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `group_id` TEXT,
                `member_id` TEXT,
                `transaction_id` TEXT,
                `amount` REAL NOT NULL,
                `balance_after` REAL NOT NULL,
                `description` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `created_at` TEXT
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `group_ledger_new` (`id`, `group_id`, `member_id`, `transaction_id`, `amount`, `balance_after`, `description`, `category`, `created_at`)
            SELECT `id`, `group_id`, `member_id`, `transaction_id`, `amount`, `balance_after`, `description`, `category`, `created_at`
            FROM `group_ledger`
        """.trimIndent())

        db.execSQL("DROP TABLE `group_ledger`")
        db.execSQL("ALTER TABLE `group_ledger_new` RENAME TO `group_ledger`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_ledger_group_id` ON `group_ledger` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_ledger_member_id` ON `group_ledger` (`member_id`)")
    }
}

val MIGRATION_57_58 = object : Migration(57, 58) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Safety bump
    }
}

val MIGRATION_58_59 = object : Migration(58, 59) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Final attempt to fix group_ledger nullability for group_id
        db.execSQL("DROP TABLE IF EXISTS `group_ledger_new`")
        db.execSQL("""
            CREATE TABLE `group_ledger_new` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `group_id` TEXT,
                `member_id` TEXT,
                `transaction_id` TEXT,
                `amount` REAL NOT NULL,
                `balance_after` REAL NOT NULL,
                `description` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `created_at` TEXT
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `group_ledger_new` (`id`, `group_id`, `member_id`, `transaction_id`, `amount`, `balance_after`, `description`, `category`, `created_at`)
            SELECT `id`, `group_id`, `member_id`, `transaction_id`, `amount`, `balance_after`, `description`, `category`, `created_at`
            FROM `group_ledger`
        """.trimIndent())

        db.execSQL("DROP TABLE `group_ledger`")
        db.execSQL("ALTER TABLE `group_ledger_new` RENAME TO `group_ledger`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_ledger_group_id` ON `group_ledger` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_ledger_member_id` ON `group_ledger` (`member_id`)")
    }
}

val MIGRATION_59_60 = object : Migration(59, 60) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Fix group_ledger: make balance_after nullable
        db.execSQL("DROP TABLE IF EXISTS `group_ledger_new`")
        db.execSQL("""
            CREATE TABLE `group_ledger_new` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `group_id` TEXT,
                `member_id` TEXT,
                `transaction_id` TEXT,
                `amount` REAL NOT NULL,
                `balance_after` REAL,
                `description` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `created_at` TEXT
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `group_ledger_new` (`id`, `group_id`, `member_id`, `transaction_id`, `amount`, `balance_after`, `description`, `category`, `created_at`)
            SELECT `id`, `group_id`, `member_id`, `transaction_id`, `amount`, `balance_after`, `description`, `category`, `created_at`
            FROM `group_ledger`
        """.trimIndent())

        db.execSQL("DROP TABLE `group_ledger`")
        db.execSQL("ALTER TABLE `group_ledger_new` RENAME TO `group_ledger`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_ledger_group_id` ON `group_ledger` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_ledger_member_id` ON `group_ledger` (`member_id`)")
    }
}
