package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_75_76 = object : Migration(75, 76) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Table burial_claims renamed to beneficiary_payout_claims to align with domain naming
        if (db.tableExists("burial_claims")) {
            db.execSQL("ALTER TABLE `burial_claims` RENAME TO `beneficiary_payout_claims`")
        }
    }
}

val MIGRATION_76_77 = object : Migration(76, 77) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("ALTER TABLE `groups` ADD COLUMN `max_beneficiaries` INTEGER NOT NULL DEFAULT 5")
    }
}

val MIGRATION_77_78 = object : Migration(77, 78) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("ALTER TABLE `groups` ADD COLUMN `beneficiary_increase_pct` REAL NOT NULL DEFAULT 10.0")
    }
}

/** Rebuild groups using the current Room schema and preserve money values as decimal text. */
val MIGRATION_78_79 = object : Migration(78, 79) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `groups` RENAME TO `groups_v78`")
        val sourceColumns = db.tableColumns("groups_v78")
        fun source(name: String, fallback: String = "NULL"): String =
            if (name in sourceColumns) "`$name`" else fallback
        fun money(name: String): String = "CAST(${source(name, "0")} AS TEXT)"
        db.execSQL("""
            CREATE TABLE `groups` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL,
                `province` TEXT NOT NULL, `city` TEXT NOT NULL, `township` TEXT NOT NULL,
                `description` TEXT NOT NULL, `logo_emoji` TEXT,
                `late_fee_grace_days` INTEGER NOT NULL, `probation_months` INTEGER NOT NULL,
                `payment_due_day` INTEGER NOT NULL, `max_members` INTEGER NOT NULL,
                `current_members` INTEGER NOT NULL, `is_public` INTEGER NOT NULL,
                `allow_partial_payment` INTEGER NOT NULL, `auto_suspend_after` INTEGER NOT NULL,
                `bank_name` TEXT, `account_number` TEXT, `branch_code` TEXT,
                `account_type` TEXT NOT NULL, `admin_user_id` TEXT, `fee_status` TEXT NOT NULL,
                `registration_paid` INTEGER NOT NULL, `is_platform_suspended` INTEGER NOT NULL,
                `period_months` INTEGER NOT NULL, `constitution_url` TEXT,
                `constitution_status` TEXT NOT NULL, `max_beneficiaries` INTEGER NOT NULL,
                `beneficiary_increase_pct` REAL NOT NULL, `created_at` TEXT,
                `investment_payout_date` TEXT, `latitude` REAL, `longitude` REAL, `geohash` TEXT,
                `rosca_rotation_method` TEXT NOT NULL, `loan_interest_rate` REAL,
                `loan_max_months` INTEGER, `financial_provider_id` TEXT,
                `joining_fee` TEXT NOT NULL, `monthly_contribution` TEXT NOT NULL,
                `late_fee` TEXT NOT NULL, `balance` TEXT NOT NULL, `goal_amount` TEXT NOT NULL,
                `loan_max_amount` TEXT, `updated_at` TEXT, PRIMARY KEY(`id`)
            )
        """.trimIndent())
        val columns = listOf(
            "id" to source("id", "lower(hex(randomblob(16)))"),
            "name" to source("name", "''"),
            "type" to source("type", "'OTHER'"),
            "province" to source("province", "''"),
            "city" to source("city", "''"),
            "township" to source("township", "''"),
            "description" to source("description", "''"),
            "logo_emoji" to source("logo_emoji"),
            "late_fee_grace_days" to source("late_fee_grace_days", "0"),
            "probation_months" to source("probation_months", "0"),
            "payment_due_day" to source("payment_due_day", "1"),
            "max_members" to source("max_members", "0"),
            "current_members" to source("current_members", "0"),
            "is_public" to source("is_public", "0"),
            "allow_partial_payment" to source("allow_partial_payment", "0"),
            "auto_suspend_after" to source("auto_suspend_after", "0"),
            "bank_name" to source("bank_name"),
            "account_number" to source("account_number"),
            "branch_code" to source("branch_code"),
            "account_type" to source("account_type", "''"),
            "admin_user_id" to source("admin_user_id"),
            "fee_status" to source("fee_status", "'DUE'"),
            "registration_paid" to source("registration_paid", "0"),
            "is_platform_suspended" to source("is_platform_suspended", "0"),
            "period_months" to source("period_months", "12"),
            "constitution_url" to source("constitution_url"),
            "constitution_status" to source("constitution_status", "'PENDING'"),
            "max_beneficiaries" to source("max_beneficiaries", "0"),
            "beneficiary_increase_pct" to source("beneficiary_increase_pct", "0.0"),
            "created_at" to source("created_at"),
            "investment_payout_date" to source("investment_payout_date"),
            "latitude" to source("latitude"),
            "longitude" to source("longitude"),
            "geohash" to source("geohash"),
            "rosca_rotation_method" to source("rosca_rotation_method", "'FIXED'"),
            "loan_interest_rate" to source("loan_interest_rate"),
            "loan_max_months" to source("loan_max_months"),
            "financial_provider_id" to source("financial_provider_id"),
            "joining_fee" to money("joining_fee"),
            "monthly_contribution" to money("monthly_contribution"),
            "late_fee" to money("late_fee"),
            "balance" to money("balance"),
            "goal_amount" to money("goal_amount"),
            "loan_max_amount" to if ("loan_max_amount" in sourceColumns) money("loan_max_amount") else "NULL",
            "updated_at" to source("updated_at")
        )
        db.execSQL(
            "INSERT INTO `groups` (${columns.joinToString { "`${it.first}`" }}) " +
                "SELECT ${columns.joinToString { it.second }} FROM `groups_v78`"
        )
        db.execSQL("DROP TABLE `groups_v78`")
        db.execSQL("CREATE INDEX `index_groups_is_public` ON `groups` (`is_public`)")
        db.execSQL("CREATE INDEX `index_groups_admin_user_id` ON `groups` (`admin_user_id`)")
        db.execSQL("CREATE INDEX `index_groups_fee_status` ON `groups` (`fee_status`)")
    }
}

/** Align legacy member rows with the precision-safe Room member schema. */
val MIGRATION_79_80 = object : Migration(79, 80) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val sourceColumns = db.tableColumns("members")
        if (sourceColumns.isEmpty()) return

        // Rebuild because previous versions stored Money as REAL and required
        // nullable fields that Room now models as optional.
        db.execSQL("ALTER TABLE `members` RENAME TO `members_v79`")
        db.execSQL("""
            CREATE TABLE `members` (
                `id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `user_id` TEXT,
                `member_key` TEXT, `full_name` TEXT NOT NULL, `id_number` TEXT,
                `phone` TEXT, `email` TEXT, `street` TEXT, `suburb` TEXT,
                `city` TEXT, `province` TEXT, `status` TEXT NOT NULL,
                `joined_at` TEXT, `probation_end_at` TEXT, `profile_photo_url` TEXT,
                `document_1_url` TEXT, `document_1_type` TEXT,
                `document_1_status` TEXT NOT NULL, `document_2_url` TEXT,
                `document_2_type` TEXT, `document_2_status` TEXT NOT NULL,
                `document_3_url` TEXT, `document_3_type` TEXT,
                `document_3_status` TEXT NOT NULL, `document_4_url` TEXT,
                `document_4_type` TEXT, `document_4_status` TEXT NOT NULL,
                `document_5_url` TEXT, `document_5_type` TEXT,
                `document_5_status` TEXT NOT NULL, `contract_url` TEXT,
                `contract_status` TEXT NOT NULL, `bank_name` TEXT, `account_no` TEXT,
                `branch_code` TEXT, `beneficiary_count` INTEGER,
                `beneficiary_over_65_count` INTEGER,
                `monthly_contribution_override` TEXT, `total_contributions` INTEGER,
                `total_paid` TEXT NOT NULL DEFAULT '0.00', `fcm_token` TEXT,
                `notification_pref` TEXT NOT NULL, `rosca_position` INTEGER,
                `created_at` TEXT, `updated_at` TEXT, PRIMARY KEY(`id`)
            )
        """.trimIndent())

        fun source(name: String, fallback: String = "NULL"): String =
            if (name in sourceColumns) "`$name`" else fallback
        fun money(name: String, fallback: String): String =
            if (name in sourceColumns) "CAST(`$name` AS TEXT)" else fallback
        val fields = listOf(
            "id" to source("id", "''"), "group_id" to source("group_id", "''"),
            "user_id" to source("user_id"), "member_key" to source("member_key"),
            "full_name" to source("full_name", "''"), "id_number" to source("id_number"),
            "phone" to source("phone"), "email" to source("email"),
            "street" to source("street", "''"), "suburb" to source("suburb", "''"),
            "city" to source("city", "''"), "province" to source("province", "''"),
            "status" to source("status", "'PROBATION'"), "joined_at" to source("joined_at"),
            "probation_end_at" to source("probation_end_at"), "profile_photo_url" to source("profile_photo_url"),
            "document_1_url" to source("document_1_url"), "document_1_type" to source("document_1_type"),
            "document_1_status" to source("document_1_status", "'PENDING'"),
            "document_2_url" to source("document_2_url"), "document_2_type" to source("document_2_type"),
            "document_2_status" to source("document_2_status", "'PENDING'"),
            "document_3_url" to source("document_3_url"), "document_3_type" to source("document_3_type"),
            "document_3_status" to source("document_3_status", "'PENDING'"),
            "document_4_url" to source("document_4_url"), "document_4_type" to source("document_4_type"),
            "document_4_status" to source("document_4_status", "'PENDING'"),
            "document_5_url" to source("document_5_url"), "document_5_type" to source("document_5_type"),
            "document_5_status" to source("document_5_status", "'PENDING'"),
            "contract_url" to source("contract_url"), "contract_status" to source("contract_status", "'PENDING'"),
            "bank_name" to source("bank_name"), "account_no" to source("account_no"),
            "branch_code" to source("branch_code"), "beneficiary_count" to source("beneficiary_count"),
            "beneficiary_over_65_count" to source("beneficiary_over_65_count"),
            "monthly_contribution_override" to money("monthly_contribution_override", "NULL"),
            "total_contributions" to source("total_contributions"),
            "total_paid" to money("total_paid", "'0.00'"), "fcm_token" to source("fcm_token"),
            "notification_pref" to source("notification_pref", "'BOTH'"),
            "rosca_position" to source("rosca_position"), "created_at" to source("created_at"),
            "updated_at" to source("updated_at")
        )
        db.execSQL(
            "INSERT INTO `members` (${fields.joinToString { "`${it.first}`" }}) " +
                "SELECT ${fields.joinToString { it.second }} FROM `members_v79`"
        )
        db.execSQL("DROP TABLE `members_v79`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_group_id` ON `members` (`group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_user_id` ON `members` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_members_status` ON `members` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_members_member_key` ON `members` (`member_key`)")
    }
}
