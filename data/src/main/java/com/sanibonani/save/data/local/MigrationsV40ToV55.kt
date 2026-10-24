package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Align Room 'contributions' table to ContributionEntity and document type mapping for cross-system compatibility.
        // Type mapping notes:
        // - UUID in Postgres is stored as TEXT in Room (String)
        // - NUMERIC/REAL in Postgres is Double in Room
        // - TIMESTAMPTZ/DATE in Postgres is ISO8601 String in Room
        // - BOOLEAN in Postgres is INTEGER (0/1) in Room
        // - updated_at: TIMESTAMPTZ in Postgres, epoch ms (INTEGER) in Room
        // - Room does not store receipt_url (add if needed for feature parity)
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `contributions_new` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `member_id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `policy_id` TEXT,
                `amount` REAL NOT NULL,
                `created_at` TEXT,
                `due_date` TEXT NOT NULL,
                `paid_at` TEXT,
                `status` TEXT NOT NULL,
                `type` TEXT NOT NULL DEFAULT 'contribution',
                `payment_method` TEXT NOT NULL DEFAULT 'bank',
                `late_fees_applied` INTEGER NOT NULL DEFAULT 0,
                `transaction_id` TEXT,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())
        db.safeExec("""
            INSERT INTO `contributions_new` (
                `id`, `member_id`, `group_id`, `policy_id`, `amount`, `created_at`, `due_date`, `paid_at`,
                `status`, `type`, `payment_method`, `late_fees_applied`, `transaction_id`, `updated_at`
            )
            SELECT
                COALESCE(`id`, ''),
                COALESCE(`member_id`, ''),
                COALESCE(`group_id`, ''),
                `policy_id`,
                COALESCE(`amount`, 0.0),
                `created_at`,
                COALESCE(`due_date`, ''),
                `paid_at`,
                COALESCE(`status`, ''),
                COALESCE(`type`, 'contribution'),
                COALESCE(`payment_method`, 'bank'),
                COALESCE(CAST(`late_fees_applied` AS INTEGER), 0),
                COALESCE(`transaction_id`, `yoco_transaction_id`, NULL),
                COALESCE(`updated_at`, 0)
            FROM `contributions`
        """.trimIndent())
        db.safeExec("DROP TABLE `contributions`")
        db.safeExec("ALTER TABLE `contributions_new` RENAME TO `contributions`")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_member_id` ON `contributions` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_group_id` ON `contributions` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_status` ON `contributions` (`status`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_due_date` ON `contributions` (`due_date`)")
    }
}

val MIGRATION_41_42 = object : Migration(41, 42) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_42_43 = object : Migration(42, 43) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_43_44 = object : Migration(43, 44) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_44_45 = object : Migration(44, 45) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_45_46 = object : Migration(45, 46) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_46_47 = object : Migration(46, 47) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("ALTER TABLE `members` ADD COLUMN `rosca_position` INTEGER")
    }
}

val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("ALTER TABLE `members` ADD COLUMN `bank_name` TEXT")
        db.safeExec("ALTER TABLE `members` ADD COLUMN `account_no` TEXT")
        db.safeExec("ALTER TABLE `members` ADD COLUMN `branch_code` TEXT")
        db.safeExec("ALTER TABLE `payouts` ADD COLUMN `member_id` TEXT")
    }
}

val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("ALTER TABLE `groups` ADD COLUMN `investment_payout_date` TEXT")
    }
}

val MIGRATION_50_51 = object : Migration(50, 51) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("CREATE TABLE IF NOT EXISTS platform_fee_config (groupType TEXT NOT NULL, roscaCycle INTEGER NOT NULL, registrationFee REAL NOT NULL, monthlyFee REAL NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(groupType, roscaCycle))")
        db.safeExec("ALTER TABLE members ADD COLUMN contract_url TEXT")
        db.safeExec("ALTER TABLE members ADD COLUMN contract_status TEXT NOT NULL DEFAULT 'pending'")
    }
}

val MIGRATION_51_52 = object : Migration(51, 52) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Heal group_ledger (convert camelCase to snake_case if needed)
        val columns = db.tableColumns("group_ledger")
        if ("groupId" in columns) {
            db.execSQL("DROP TABLE IF EXISTS `group_ledger_new`")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `group_ledger_new` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `group_id` TEXT NOT NULL,
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
                INSERT INTO `group_ledger_new` (`id`, `group_id`, `transaction_id`, `amount`, `balance_after`, `description`, `category`, `created_at`)
                SELECT `id`, `groupId`, `transactionId`, `amount`, `balanceAfter`, `description`, `category`, `createdAt`
                FROM `group_ledger`
            """.trimIndent())

            db.execSQL("DROP TABLE `group_ledger`")
            db.execSQL("ALTER TABLE `group_ledger_new` RENAME TO `group_ledger`")
        } else {
            db.safeExec("""
                CREATE TABLE IF NOT EXISTS `group_ledger` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `group_id` TEXT NOT NULL,
                    `member_id` TEXT,
                    `transaction_id` TEXT,
                    `amount` REAL NOT NULL,
                    `balance_after` REAL NOT NULL,
                    `description` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `created_at` TEXT
                )
            """.trimIndent())
        }

        // Ensure settlements and reconciliations exist
        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `settlements` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `transaction_id` TEXT NOT NULL,
                `provider_reference` TEXT,
                `status` TEXT NOT NULL,
                `settled_at` TEXT,
                `fee_deducted` REAL NOT NULL
            )
        """.trimIndent())
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_settlements_transaction_id` ON `settlements` (`transaction_id`)")

        db.safeExec("""
            CREATE TABLE IF NOT EXISTS `reconciliations` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `group_id` TEXT NOT NULL,
                `period_start` TEXT NOT NULL,
                `period_end` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `ledger_balance` REAL NOT NULL,
                `actual_balance` REAL NOT NULL,
                `discrepancy` REAL NOT NULL,
                `notes` TEXT,
                `performed_by` TEXT,
                `created_at` TEXT
            )
        """.trimIndent())
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_reconciliations_group_id` ON `reconciliations` (`group_id`)")
    }
}

val MIGRATION_52_53 = object : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val columns = db.tableColumns("group_ledger")
        if ("member_id" !in columns) {
            db.safeExec("ALTER TABLE `group_ledger` ADD COLUMN `member_id` TEXT")
        }
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_group_ledger_group_id` ON `group_ledger` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_group_ledger_member_id` ON `group_ledger` (`member_id`)")
    }
}

val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val columns = db.tableColumns("beneficiaries")
        if ("face_photo_url" !in columns) {
            db.safeExec("ALTER TABLE `beneficiaries` ADD COLUMN `face_photo_url` TEXT")
        }
    }
}

val MIGRATION_54_55 = object : Migration(54, 55) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // burial_claims
        val claimCols = db.tableColumns("burial_claims")
        if ("face_photo_url" !in claimCols) {
            db.safeExec("ALTER TABLE `burial_claims` ADD COLUMN `face_photo_url` TEXT")
        }

        // loans
        val loanCols = db.tableColumns("loans")
        if ("contract_url" !in loanCols) {
            db.safeExec("ALTER TABLE `loans` ADD COLUMN `contract_url` TEXT")
        }
        if ("surety_amount" !in loanCols) {
            db.safeExec("ALTER TABLE `loans` ADD COLUMN `surety_amount` REAL")
        }
    }
}
