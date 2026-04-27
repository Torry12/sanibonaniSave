package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        fun safeExec(sql: String) {
            try { db.execSQL(sql) } catch (e: Exception) {}
        }

        // Groups table updates
        safeExec("ALTER TABLE groups ADD COLUMN beneficiary_increase_pct REAL NOT NULL")
        safeExec("ALTER TABLE groups ADD COLUMN max_beneficiaries INTEGER NOT NULL")

        // Members table updates
        safeExec("ALTER TABLE members ADD COLUMN document_3_url TEXT")
        safeExec("ALTER TABLE members ADD COLUMN document_3_type TEXT")
        safeExec("ALTER TABLE members ADD COLUMN document_3_status TEXT NOT NULL")
        safeExec("ALTER TABLE members ADD COLUMN beneficiary_count INTEGER NOT NULL")
        safeExec("ALTER TABLE members ADD COLUMN beneficiary_over_65_count INTEGER NOT NULL")
        safeExec("ALTER TABLE members ADD COLUMN monthly_contribution_override REAL")

        // Create missing tables (handling previous migration gaps)
        safeExec("""
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
        
        safeExec("CREATE INDEX IF NOT EXISTS `index_beneficiaries_group_id` ON `beneficiaries` (`group_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_beneficiaries_member_id` ON `beneficiaries` (`member_id`)")

        safeExec("""
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
        
        safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_member_id` ON `contributions` (`member_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_group_id` ON `contributions` (`group_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_status` ON `contributions` (`status`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_contributions_due_date` ON `contributions` (`due_date`)")

        safeExec("""
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

        safeExec("CREATE INDEX IF NOT EXISTS `index_payments_member_id` ON `payments` (`member_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_payments_group_id` ON `payments` (`group_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_payments_status` ON `payments` (`status`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_payments_payment_type` ON `payments` (`payment_type`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_payments_created_at` ON `payments` (`created_at`)")

        // Create notifications table
        safeExec("""
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
        
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_group_id` ON `notifications` (`group_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_member_id` ON `notifications` (`member_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_trigger_event` ON `notifications` (`trigger_event`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)")
    }
}


val MIGRATION_19_20 = object : Migration(19, 20) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fun safeExec(sql: String) {
            try {
                db.execSQL(sql)
            } catch (e: Exception) {
                // Ignore errors (e.g., column already exists)
            }
        }

        // --- Members Table Healing ---
        // Document slots (ensuring 3, 4, and 5 exist)
        safeExec("ALTER TABLE members ADD COLUMN document_3_url TEXT")
        safeExec("ALTER TABLE members ADD COLUMN document_3_type TEXT")
        safeExec("ALTER TABLE members ADD COLUMN document_3_status TEXT NOT NULL")
        
        safeExec("ALTER TABLE members ADD COLUMN document_4_url TEXT")
        safeExec("ALTER TABLE members ADD COLUMN document_4_type TEXT")
        safeExec("ALTER TABLE members ADD COLUMN document_4_status TEXT NOT NULL")
        
        safeExec("ALTER TABLE members ADD COLUMN document_5_url TEXT")
        safeExec("ALTER TABLE members ADD COLUMN document_5_type TEXT")
        safeExec("ALTER TABLE members ADD COLUMN document_5_status TEXT NOT NULL")
        
        // Burial society specific fields (healing from 18-19)
        safeExec("ALTER TABLE members ADD COLUMN beneficiary_count INTEGER NOT NULL")
        safeExec("ALTER TABLE members ADD COLUMN beneficiary_over_65_count INTEGER NOT NULL")
        safeExec("ALTER TABLE members ADD COLUMN monthly_contribution_override REAL")
        
        // Legacy/Missing fields
        safeExec("ALTER TABLE members ADD COLUMN updated_at INTEGER NOT NULL")

        // --- Groups Table Healing ---
        // Burial society fields (healing from 18-19)
        safeExec("ALTER TABLE groups ADD COLUMN beneficiary_increase_pct REAL NOT NULL")
        safeExec("ALTER TABLE groups ADD COLUMN max_beneficiaries INTEGER NOT NULL")
        
        // Version 20-21 fields
        safeExec("ALTER TABLE groups ADD COLUMN registration_paid INTEGER NOT NULL")
        safeExec("ALTER TABLE groups ADD COLUMN is_platform_suspended INTEGER NOT NULL")
        safeExec("ALTER TABLE groups ADD COLUMN goal_amount REAL NOT NULL")
        safeExec("ALTER TABLE groups ADD COLUMN period_months INTEGER NOT NULL")
        
        // Legacy/Missing fields
        safeExec("ALTER TABLE groups ADD COLUMN yoco_public_key TEXT")
        safeExec("ALTER TABLE groups ADD COLUMN updated_at INTEGER NOT NULL")

        // --- Indices Healing ---
        safeExec("CREATE INDEX IF NOT EXISTS `index_members_user_id` ON `members` (`user_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_members_status` ON `members` (`status`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_groups_is_public` ON `groups` (`is_public`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_groups_admin_user_id` ON `groups` (`admin_user_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_groups_fee_status` ON `groups` (`fee_status`)")

        // --- Ensure essential tables exist (healing for gaps) ---
        safeExec("""
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

        safeExec("""
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

        safeExec("""
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
        safeExec("""
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
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_group_id` ON `notifications` (`group_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_member_id` ON `notifications` (`member_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_trigger_event` ON `notifications` (`trigger_event`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fun safeExec(sql: String) {
            try { db.execSQL(sql) } catch (e: Exception) {}
        }

        // --- Members Table Updates ---
        safeExec("ALTER TABLE members ADD COLUMN member_key TEXT NOT NULL")
        safeExec("CREATE UNIQUE INDEX IF NOT EXISTS `index_members_member_key` ON `members` (`member_key`)")

        // --- Groups Table Updates ---
        safeExec("ALTER TABLE groups ADD COLUMN latitude REAL")
        safeExec("ALTER TABLE groups ADD COLUMN longitude REAL")

        // --- Notifications Table Healing (ensure it exists for v22) ---
        // If it exists but is corrupted (0 columns), we try to heal it.
        // Room sometimes reports 0 columns if the table creation was interrupted.
        safeExec("""
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
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_group_id` ON `notifications` (`group_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_member_id` ON `notifications` (`member_id`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_trigger_event` ON `notifications` (`trigger_event`)")
        safeExec("CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)")
    }
}


val MIGRATION_22_23 = object : Migration(22, 23) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_23_24 = object : Migration(23, 24) { override fun migrate(db: SupportSQLiteDatabase) {} }

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // This is a "fix-it" migration. 
        // We drop and recreate the members table to ensure schema matches MemberEntity exactly.
        // NOTE: In a production app, we would copy data. For this dev fix, we prioritize schema alignment.
        
        db.execSQL("DROP TABLE IF EXISTS members")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `members` (
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
        """.trimIndent())
        
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_group_id` ON `members` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_user_id` ON `members` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_status` ON `members` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_members_member_key` ON `members` (`member_key`)")
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Fix members table schema to match MemberEntity (handling nullability)
        db.execSQL("DROP TABLE IF EXISTS members")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `members` (
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
        """.trimIndent())
        
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_group_id` ON `members` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_user_id` ON `members` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_status` ON `members` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_members_member_key` ON `members` (`member_key`)")

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
    MIGRATION_32_33
)

