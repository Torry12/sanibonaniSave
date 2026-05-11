package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private fun SupportSQLiteDatabase.safeExec(sql: String) {
    try {
        execSQL(sql)
    } catch (_: Exception) {
    }
}

private fun SupportSQLiteDatabase.tableExists(tableName: String): Boolean {
    query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'").use { cursor ->
        return cursor.moveToFirst()
    }
}

private fun SupportSQLiteDatabase.tableColumns(tableName: String): Set<String> {
    if (!tableExists(tableName)) return emptySet()
    query("PRAGMA table_info(`$tableName`)").use { cursor ->
        val result = linkedSetOf<String>()
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIndex >= 0) {
                result += cursor.getString(nameIndex)
            }
        }
        return result
    }
}

private fun SupportSQLiteDatabase.memberSelectExpr(
    columns: Set<String>,
    columnName: String,
    defaultSql: String,
    coalesceWhenPresent: Boolean = false,
): String {
    return when {
        columnName !in columns -> "$defaultSql AS `$columnName`"
        coalesceWhenPresent -> "COALESCE(`$columnName`, $defaultSql)"
        else -> "`$columnName`"
    }
}

private fun SupportSQLiteDatabase.recreateMembersTablePreservingData() {
    val sourceColumns = tableColumns("members")

    execSQL("DROP TABLE IF EXISTS `members_new`")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS `members_new` (
            `id` TEXT NOT NULL,
            `group_id` TEXT NOT NULL,
            `user_id` TEXT,
            `member_key` TEXT,
            `full_name` TEXT NOT NULL,
            `id_number` TEXT NOT NULL,
            `phone` TEXT NOT NULL,
            `email` TEXT NOT NULL,
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
            `beneficiary_count` INTEGER,
            `beneficiary_over_65_count` INTEGER,
            `monthly_contribution_override` REAL,
            `total_contributions` INTEGER,
            `total_paid` REAL NOT NULL DEFAULT 0.0,
            `fcm_token` TEXT,
            `notification_pref` TEXT NOT NULL,
            `created_at` TEXT,
            `updated_at` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )

    if (sourceColumns.isNotEmpty()) {
        val selectSql = listOf(
            memberSelectExpr(sourceColumns, "id", "''"),
            memberSelectExpr(sourceColumns, "group_id", "''"),
            memberSelectExpr(sourceColumns, "user_id", "NULL"),
            memberSelectExpr(sourceColumns, "member_key", "NULL"),
            memberSelectExpr(sourceColumns, "full_name", "''"),
            memberSelectExpr(sourceColumns, "id_number", "''"),
            memberSelectExpr(sourceColumns, "phone", "''"),
            memberSelectExpr(sourceColumns, "email", "''"),
            memberSelectExpr(sourceColumns, "status", "'PROBATION'", coalesceWhenPresent = true),
            memberSelectExpr(sourceColumns, "joined_at", "''"),
            memberSelectExpr(sourceColumns, "probation_end_at", "''"),
            memberSelectExpr(sourceColumns, "profile_photo_url", "NULL"),
            memberSelectExpr(sourceColumns, "document_1_url", "NULL"),
            memberSelectExpr(sourceColumns, "document_1_type", "NULL"),
            memberSelectExpr(sourceColumns, "document_1_status", "'PENDING'", coalesceWhenPresent = true),
            memberSelectExpr(sourceColumns, "document_2_url", "NULL"),
            memberSelectExpr(sourceColumns, "document_2_type", "NULL"),
            memberSelectExpr(sourceColumns, "document_2_status", "'PENDING'", coalesceWhenPresent = true),
            memberSelectExpr(sourceColumns, "document_3_url", "NULL"),
            memberSelectExpr(sourceColumns, "document_3_type", "NULL"),
            memberSelectExpr(sourceColumns, "document_3_status", "'PENDING'", coalesceWhenPresent = true),
            memberSelectExpr(sourceColumns, "document_4_url", "NULL"),
            memberSelectExpr(sourceColumns, "document_4_type", "NULL"),
            memberSelectExpr(sourceColumns, "document_4_status", "'PENDING'", coalesceWhenPresent = true),
            memberSelectExpr(sourceColumns, "document_5_url", "NULL"),
            memberSelectExpr(sourceColumns, "document_5_type", "NULL"),
            memberSelectExpr(sourceColumns, "document_5_status", "'PENDING'", coalesceWhenPresent = true),
            memberSelectExpr(sourceColumns, "beneficiary_count", "NULL"),
            memberSelectExpr(sourceColumns, "beneficiary_over_65_count", "NULL"),
            memberSelectExpr(sourceColumns, "monthly_contribution_override", "NULL"),
            memberSelectExpr(sourceColumns, "total_contributions", "NULL"),
            memberSelectExpr(sourceColumns, "total_paid", "0.0", coalesceWhenPresent = true),
            memberSelectExpr(sourceColumns, "fcm_token", "NULL"),
            memberSelectExpr(sourceColumns, "notification_pref", "'BOTH'", coalesceWhenPresent = true),
            memberSelectExpr(sourceColumns, "created_at", "NULL"),
            memberSelectExpr(sourceColumns, "updated_at", "0", coalesceWhenPresent = true),
        ).joinToString(",\n                ")

        execSQL(
            """
            INSERT INTO `members_new` (
                `id`, `group_id`, `user_id`, `member_key`, `full_name`, `id_number`, `phone`, `email`,
                `status`, `joined_at`, `probation_end_at`, `profile_photo_url`,
                `document_1_url`, `document_1_type`, `document_1_status`,
                `document_2_url`, `document_2_type`, `document_2_status`,
                `document_3_url`, `document_3_type`, `document_3_status`,
                `document_4_url`, `document_4_type`, `document_4_status`,
                `document_5_url`, `document_5_type`, `document_5_status`,
                `beneficiary_count`, `beneficiary_over_65_count`, `monthly_contribution_override`,
                `total_contributions`, `total_paid`, `fcm_token`, `notification_pref`, `created_at`, `updated_at`
            )
            SELECT
                $selectSql
            FROM `members`
            """.trimIndent()
        )
    }

    execSQL("DROP TABLE IF EXISTS `members`")
    execSQL("ALTER TABLE `members_new` RENAME TO `members`")
    execSQL("CREATE INDEX IF NOT EXISTS `index_members_group_id` ON `members` (`group_id`)")
    execSQL("CREATE INDEX IF NOT EXISTS `index_members_user_id` ON `members` (`user_id`)")
    execSQL("CREATE INDEX IF NOT EXISTS `index_members_status` ON `members` (`status`)")
    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_members_member_key` ON `members` (`member_key`)")
}

val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) {} }
val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Adding geohash column to groups table
        db.execSQL("ALTER TABLE groups ADD COLUMN geohash TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_7_8 = object : Migration(7, 8) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Adding new fields to contributions table
        try {
            db.execSQL("ALTER TABLE contributions ADD COLUMN created_at TEXT")
        } catch (e: Exception) { /* column may already exist */ }
        try {
            db.execSQL("ALTER TABLE contributions ADD COLUMN late_fees_applied BOOLEAN")
        } catch (e: Exception) { /* column may already exist */ }
    }
}

// Placeholder migrations for versions 9-15 (safe no-ops)
val MIGRATION_9_10 = object : Migration(9, 10) { override fun migrate(db: SupportSQLiteDatabase) {} }
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
        db.safeExec("ALTER TABLE groups ADD COLUMN yoco_public_key TEXT")
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

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Members table: Add address fields
        db.execSQL("ALTER TABLE members ADD COLUMN street TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE members ADD COLUMN suburb TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE members ADD COLUMN city TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE members ADD COLUMN province TEXT NOT NULL DEFAULT ''")

        // Groups table: Add constitution fields
        db.execSQL("ALTER TABLE groups ADD COLUMN constitution_url TEXT")
        db.execSQL("ALTER TABLE groups ADD COLUMN constitution_status TEXT NOT NULL DEFAULT 'PENDING'")
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fun safeExec(sql: String) {
            try { db.execSQL(sql) } catch (e: Exception) {}
        }
        // Beneficiaries table: Add document fields for beneficiary document uploads
        safeExec("ALTER TABLE beneficiaries ADD COLUMN document_url TEXT")
        safeExec("ALTER TABLE beneficiaries ADD COLUMN document_status TEXT NOT NULL DEFAULT 'pending'")
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fun safeExec(sql: String) {
            try { db.execSQL(sql) } catch (e: Exception) {}
        }
        // Contributions table: Add payment_method column to match Supabase schema
        safeExec("ALTER TABLE contributions ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'yoco'")
    }
}

// ⚠️ DEV-ERA DESTRUCTIVE MIGRATIONS (v24→25, v25→26):
// These dropped + recreated the members table to fix schema drift.
// They are IRREVERSIBLE. DO NOT replicate this pattern for any future version bump.
// Production apps MUST always copy data: CREATE TABLE members_new … INSERT INTO … DROP … RENAME.

val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fun safeExec(sql: String) {
            try { db.execSQL(sql) } catch (e: Exception) {}
        }
        // Add loans table (introduced for Smart Loan feature)
        safeExec("""
            CREATE TABLE IF NOT EXISTS `loans` (
                `id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `interest_rate` REAL NOT NULL,
                `total_to_repay` REAL NOT NULL,
                `total_repaid` REAL NOT NULL,
                `monthly_repayment` REAL NOT NULL,
                `start_date` TEXT NOT NULL,
                `end_date` TEXT NOT NULL,
                `next_payment_date` TEXT,
                `status` TEXT NOT NULL,
                `purpose` TEXT,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        safeExec("CREATE INDEX IF NOT EXISTS `index_loans_member_id` ON `loans` (`member_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_loans_group_id` ON `loans` (`group_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_loans_status` ON `loans` (`status`)")

        // Add loan_repayments table
        safeExec("""
            CREATE TABLE IF NOT EXISTS `loan_repayments` (
                `id` TEXT NOT NULL,
                `loan_id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `paid_at` TEXT,
                `payment_method` TEXT NOT NULL,
                `transaction_id` TEXT,
                `created_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        safeExec("CREATE INDEX IF NOT EXISTS `index_loan_repayments_loan_id` ON `loan_repayments` (`loan_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_loan_repayments_member_id` ON `loan_repayments` (`member_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_loan_repayments_group_id` ON `loan_repayments` (`group_id`)")
    }
}

val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec(
            """
            CREATE TABLE IF NOT EXISTS `group_health_scores` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `overall_score` INTEGER NOT NULL,
                `zone` TEXT NOT NULL,
                `components_json` TEXT NOT NULL,
                `recommendations_json` TEXT NOT NULL,
                `generated_at` TEXT NOT NULL,
                `expires_at` TEXT,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.safeExec("CREATE UNIQUE INDEX IF NOT EXISTS `index_group_health_scores_group_id` ON `group_health_scores` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_group_health_scores_generated_at` ON `group_health_scores` (`generated_at`)")
    }
}

val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fun safeExec(sql: String) {
            try { db.execSQL(sql) } catch (e: Exception) {}
        }
        // Create burial_claims table for beneficiary payout claims
        safeExec("""
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
        safeExec("CREATE INDEX IF NOT EXISTS `index_burial_claims_group_id` ON `burial_claims` (`group_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_burial_claims_member_id` ON `burial_claims` (`member_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_burial_claims_beneficiary_id` ON `burial_claims` (`beneficiary_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_burial_claims_status` ON `burial_claims` (`status`)")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
    MIGRATION_24_25,
    MIGRATION_25_26,
    MIGRATION_26_27,
    MIGRATION_27_28,
    MIGRATION_28_29,
    MIGRATION_29_30,
    MIGRATION_30_31,
    MIGRATION_31_32,
    MIGRATION_32_33,
    MIGRATION_33_34,
    MIGRATION_34_35,
    MIGRATION_35_36
)

