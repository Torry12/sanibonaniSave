package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        // Beneficiaries table: Add document fields for beneficiary document uploads
        db.safeExec("ALTER TABLE beneficiaries ADD COLUMN document_url TEXT")
        db.safeExec("ALTER TABLE beneficiaries ADD COLUMN document_status TEXT NOT NULL DEFAULT 'pending'")
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Contributions table: Add payment_method column to match Supabase schema
        db.safeExec("ALTER TABLE contributions ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'yoco'")
    }
}

// ⚠️ DEV-ERA DESTRUCTIVE MIGRATIONS (v24→25, v25→26):
// These dropped + recreated the members table to fix schema drift.
// They are IRREVERSIBLE. DO NOT replicate this pattern for any future version bump.
// Production apps MUST always copy data: CREATE TABLE members_new … INSERT INTO … DROP … RENAME.

val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add loans table (introduced for Smart Loan feature)
        db.safeExec("""
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
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_loans_member_id` ON `loans` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_loans_group_id` ON `loans` (`group_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_loans_status` ON `loans` (`status`)")

        // Add loan_repayments table
        db.safeExec("""
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
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_loan_repayments_loan_id` ON `loan_repayments` (`loan_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_loan_repayments_member_id` ON `loan_repayments` (`member_id`)")
        db.safeExec("CREATE INDEX IF NOT EXISTS `index_loan_repayments_group_id` ON `loan_repayments` (`group_id`)")
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
