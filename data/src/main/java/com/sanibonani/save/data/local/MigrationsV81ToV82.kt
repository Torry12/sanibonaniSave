package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Repairs every table whose legacy migration chain never caught up with the Room entity
 * schema. Money columns were created as REAL before the Money (BigDecimal) migration and
 * several tables gained columns or lost NOT NULL constraints without a rebuild, so a
 * pre-Money install would fail Room's schema validation as soon as it opened v82.
 *
 * The money-bearing tables the dashboards read — groups, members and contributions — are
 * rebuilt here too. Their column sets already matched v82, but a legacy REAL amount would
 * still surface as a coerced value rather than the money string the entity decodes, so the
 * balance a payment just moved would not reach the screen that shows it.
 *
 * Room validates against these tables one at a time, so the whole set is repaired here
 * rather than one failing release at a time. Data is preserved: REAL money is converted
 * to canonical 2-dp strings, epoch-millis columns keep their numeric value as text, and
 * columns that did not exist yet are filled with the entity defaults.
 */
val MIGRATION_81_82 = object : Migration(81, 82) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.rebuildGroups()
        db.rebuildMembers()
        db.rebuildContributions()
        db.rebuildPayments()
        db.rebuildPayouts()
        db.rebuildLoans()
        db.rebuildLoanRepayments()
        db.rebuildBeneficiaryClaims()
        db.rebuildGroupLedger()
        db.rebuildSettlements()
        db.rebuildReconciliations()
        db.rebuildMemberBehaviorTrack()
        db.rebuildFraudDetectionEvents()
        db.rebuildBehaviorAnalyticsSummary()
        db.rebuildPlatformFeeConfig()
        db.rebuildBankTransactions()
        db.rebuildFinancialProviders()
        db.rebuildLedgerCheckpoints()
        db.createBankConnections()
        db.createGroupPolls()
        db.dropStaleIndices()
    }
}

/** Indices created by earlier migrations that the current entities no longer declare. */
private fun SupportSQLiteDatabase.dropStaleIndices() {
    listOf(
        "index_audit_reports_type_targetId",
        "index_group_ledger_member_id",
        "index_group_ledger_is_platform",
        "index_ledger_checkpoints_ledger_entry_id",
    ).forEach { execSQL("DROP INDEX IF EXISTS `$it`") }
}

// ── Column expressions for legacy → v82 copies ───────────────────────────────

private fun textOf(source: Set<String>, name: String, fallback: String = "NULL"): String =
    if (name in source) "`$name`" else fallback

private fun coalescedText(source: Set<String>, name: String, fallback: String): String =
    if (name in source) "COALESCE(`$name`, $fallback)" else fallback

private fun moneyOf(source: Set<String>, name: String, fallback: String = "'0.00'"): String =
    if (name in source) "COALESCE(printf('%.2f', CAST(`$name` AS REAL)), $fallback)" else fallback

private fun nullableMoneyOf(source: Set<String>, name: String): String =
    if (name in source) "CASE WHEN `$name` IS NULL THEN NULL ELSE printf('%.2f', CAST(`$name` AS REAL)) END" else "NULL"

private fun numberOrZero(source: Set<String>, name: String): String =
    if (name in source) "COALESCE(`$name`, 0)" else "0"

private fun SupportSQLiteDatabase.rebuildV82Table(
    table: String,
    createSql: String,
    indices: List<String>,
    columns: (Set<String>) -> List<Pair<String, String>>,
) {
    val sourceColumns = tableColumns(table)
    if (sourceColumns.isEmpty()) return
    val legacyTable = "${table}_v81"
    execSQL("DROP TABLE IF EXISTS `$legacyTable`")
    execSQL("ALTER TABLE `$table` RENAME TO `$legacyTable`")
    execSQL(createSql)
    val fields = columns(sourceColumns)
    execSQL(
        "INSERT INTO `$table` (${fields.joinToString { "`${it.first}`" }}) " +
            "SELECT ${fields.joinToString { it.second }} FROM `$legacyTable`"
    )
    execSQL("DROP TABLE `$legacyTable`")
    indices.forEach { execSQL(it) }
}

// ── Rebuilds ────────────────────────────────────────────────────────────────

/**
 * groups.balance is what the group dashboard renders, so a legacy REAL column must reach v82 as
 * the same money string the entity reads, not as whatever SQLite happens to coerce it to.
 */
private fun SupportSQLiteDatabase.rebuildGroups() = rebuildV82Table(
    table = "groups",
    createSql = """
        CREATE TABLE `groups` (
            `id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL,
            `province` TEXT NOT NULL, `city` TEXT NOT NULL, `township` TEXT NOT NULL,
            `description` TEXT NOT NULL, `logo_emoji` TEXT,
            `late_fee_grace_days` INTEGER NOT NULL, `probation_months` INTEGER NOT NULL,
            `payment_due_day` INTEGER NOT NULL, `max_members` INTEGER NOT NULL,
            `current_members` INTEGER NOT NULL, `is_public` INTEGER NOT NULL,
            `allow_partial_payment` INTEGER NOT NULL, `auto_suspend_after` INTEGER NOT NULL,
            `bank_name` TEXT, `account_number` TEXT, `branch_code` TEXT, `account_type` TEXT NOT NULL,
            `admin_user_id` TEXT, `fee_status` TEXT NOT NULL, `registration_paid` INTEGER NOT NULL,
            `is_platform_suspended` INTEGER NOT NULL, `period_months` INTEGER NOT NULL,
            `constitution_url` TEXT, `constitution_status` TEXT NOT NULL,
            `max_beneficiaries` INTEGER NOT NULL, `beneficiary_increase_pct` REAL NOT NULL,
            `burial_claim_amount` TEXT NOT NULL, `min_members_for_claims` INTEGER NOT NULL,
            `min_reserve_percentage` REAL NOT NULL, `created_at` TEXT,
            `investment_payout_date` TEXT, `latitude` REAL, `longitude` REAL, `geohash` TEXT,
            `rosca_rotation_method` TEXT NOT NULL, `loan_interest_rate` REAL,
            `loan_max_months` INTEGER, `financial_provider_id` TEXT,
            `joining_fee` TEXT NOT NULL, `monthly_contribution` TEXT NOT NULL,
            `late_fee` TEXT NOT NULL, `balance` TEXT NOT NULL, `goal_amount` TEXT NOT NULL,
            `loan_max_amount` TEXT, `updated_at` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_groups_is_public` ON `groups` (`is_public`)",
        "CREATE INDEX IF NOT EXISTS `index_groups_admin_user_id` ON `groups` (`admin_user_id`)",
        "CREATE INDEX IF NOT EXISTS `index_groups_fee_status` ON `groups` (`fee_status`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "name" to textOf(source, "name", "''"),
        "type" to textOf(source, "type", "'OTHER'"),
        "province" to textOf(source, "province", "''"),
        "city" to textOf(source, "city", "''"),
        "township" to textOf(source, "township", "''"),
        "description" to textOf(source, "description", "''"),
        "logo_emoji" to textOf(source, "logo_emoji"),
        "late_fee_grace_days" to numberOrZero(source, "late_fee_grace_days"),
        "probation_months" to numberOrZero(source, "probation_months"),
        "payment_due_day" to numberOrZero(source, "payment_due_day"),
        "max_members" to numberOrZero(source, "max_members"),
        "current_members" to numberOrZero(source, "current_members"),
        "is_public" to numberOrZero(source, "is_public"),
        "allow_partial_payment" to numberOrZero(source, "allow_partial_payment"),
        "auto_suspend_after" to numberOrZero(source, "auto_suspend_after"),
        "bank_name" to textOf(source, "bank_name"),
        "account_number" to textOf(source, "account_number"),
        "branch_code" to textOf(source, "branch_code"),
        "account_type" to textOf(source, "account_type", "'SAVINGS'"),
        "admin_user_id" to textOf(source, "admin_user_id"),
        "fee_status" to textOf(source, "fee_status", "'DUE'"),
        "registration_paid" to numberOrZero(source, "registration_paid"),
        "is_platform_suspended" to numberOrZero(source, "is_platform_suspended"),
        "period_months" to numberOrZero(source, "period_months"),
        "constitution_url" to textOf(source, "constitution_url"),
        "constitution_status" to textOf(source, "constitution_status", "'PENDING'"),
        "max_beneficiaries" to numberOrZero(source, "max_beneficiaries"),
        "beneficiary_increase_pct" to numberOrZero(source, "beneficiary_increase_pct"),
        "burial_claim_amount" to moneyOf(source, "burial_claim_amount", "'10000.00'"),
        "min_members_for_claims" to numberOrZero(source, "min_members_for_claims"),
        "min_reserve_percentage" to numberOrZero(source, "min_reserve_percentage"),
        "created_at" to textOf(source, "created_at"),
        "investment_payout_date" to textOf(source, "investment_payout_date"),
        "latitude" to textOf(source, "latitude"),
        "longitude" to textOf(source, "longitude"),
        "geohash" to textOf(source, "geohash"),
        "rosca_rotation_method" to textOf(source, "rosca_rotation_method", "'FIXED'"),
        "loan_interest_rate" to textOf(source, "loan_interest_rate"),
        "loan_max_months" to textOf(source, "loan_max_months"),
        "financial_provider_id" to textOf(source, "financial_provider_id"),
        "joining_fee" to moneyOf(source, "joining_fee"),
        "monthly_contribution" to moneyOf(source, "monthly_contribution"),
        "late_fee" to moneyOf(source, "late_fee"),
        "balance" to moneyOf(source, "balance"),
        "goal_amount" to moneyOf(source, "goal_amount"),
        "loan_max_amount" to nullableMoneyOf(source, "loan_max_amount"),
        "updated_at" to textOf(source, "updated_at")
    )
}

/** members.total_paid backs the member dashboard's savings figure. */
private fun SupportSQLiteDatabase.rebuildMembers() = rebuildV82Table(
    table = "members",
    createSql = """
        CREATE TABLE `members` (
            `id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `user_id` TEXT, `member_key` TEXT,
            `full_name` TEXT NOT NULL, `id_number` TEXT, `phone` TEXT, `email` TEXT,
            `street` TEXT, `suburb` TEXT, `city` TEXT, `province` TEXT,
            `status` TEXT NOT NULL, `joined_at` TEXT, `probation_end_at` TEXT,
            `profile_photo_url` TEXT,
            `document_1_url` TEXT, `document_1_type` TEXT, `document_1_status` TEXT NOT NULL,
            `document_2_url` TEXT, `document_2_type` TEXT, `document_2_status` TEXT NOT NULL,
            `document_3_url` TEXT, `document_3_type` TEXT, `document_3_status` TEXT NOT NULL,
            `document_4_url` TEXT, `document_4_type` TEXT, `document_4_status` TEXT NOT NULL,
            `document_5_url` TEXT, `document_5_type` TEXT, `document_5_status` TEXT NOT NULL,
            `contract_url` TEXT, `contract_status` TEXT NOT NULL,
            `bank_name` TEXT, `account_no` TEXT, `branch_code` TEXT,
            `beneficiary_count` INTEGER, `beneficiary_over_65_count` INTEGER,
            `monthly_contribution_override` TEXT, `total_contributions` INTEGER,
            `total_paid` TEXT NOT NULL DEFAULT '0.00', `fcm_token` TEXT,
            `notification_pref` TEXT NOT NULL, `rosca_position` INTEGER,
            `created_at` TEXT, `updated_at` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_members_group_id` ON `members` (`group_id`)",
        "CREATE INDEX IF NOT EXISTS `index_members_user_id` ON `members` (`user_id`)",
        "CREATE INDEX IF NOT EXISTS `index_members_status` ON `members` (`status`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_members_member_key` ON `members` (`member_key`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "user_id" to textOf(source, "user_id"),
        "member_key" to textOf(source, "member_key"),
        "full_name" to textOf(source, "full_name", "''"),
        "id_number" to textOf(source, "id_number"),
        "phone" to textOf(source, "phone"),
        "email" to textOf(source, "email"),
        "street" to textOf(source, "street"),
        "suburb" to textOf(source, "suburb"),
        "city" to textOf(source, "city"),
        "province" to textOf(source, "province"),
        "status" to textOf(source, "status", "'PROBATION'"),
        "joined_at" to textOf(source, "joined_at"),
        "probation_end_at" to textOf(source, "probation_end_at"),
        "profile_photo_url" to textOf(source, "profile_photo_url"),
        "document_1_url" to textOf(source, "document_1_url"),
        "document_1_type" to textOf(source, "document_1_type"),
        "document_1_status" to textOf(source, "document_1_status", "'PENDING'"),
        "document_2_url" to textOf(source, "document_2_url"),
        "document_2_type" to textOf(source, "document_2_type"),
        "document_2_status" to textOf(source, "document_2_status", "'PENDING'"),
        "document_3_url" to textOf(source, "document_3_url"),
        "document_3_type" to textOf(source, "document_3_type"),
        "document_3_status" to textOf(source, "document_3_status", "'PENDING'"),
        "document_4_url" to textOf(source, "document_4_url"),
        "document_4_type" to textOf(source, "document_4_type"),
        "document_4_status" to textOf(source, "document_4_status", "'PENDING'"),
        "document_5_url" to textOf(source, "document_5_url"),
        "document_5_type" to textOf(source, "document_5_type"),
        "document_5_status" to textOf(source, "document_5_status", "'PENDING'"),
        "contract_url" to textOf(source, "contract_url"),
        "contract_status" to textOf(source, "contract_status", "'PENDING'"),
        "bank_name" to textOf(source, "bank_name"),
        "account_no" to textOf(source, "account_no"),
        "branch_code" to textOf(source, "branch_code"),
        "beneficiary_count" to textOf(source, "beneficiary_count"),
        "beneficiary_over_65_count" to textOf(source, "beneficiary_over_65_count"),
        "monthly_contribution_override" to nullableMoneyOf(source, "monthly_contribution_override"),
        "total_contributions" to textOf(source, "total_contributions"),
        "total_paid" to moneyOf(source, "total_paid"),
        "fcm_token" to textOf(source, "fcm_token"),
        "notification_pref" to textOf(source, "notification_pref", "'BOTH'"),
        "rosca_position" to textOf(source, "rosca_position"),
        "created_at" to textOf(source, "created_at"),
        "updated_at" to textOf(source, "updated_at")
    )
}

/**
 * Contributions carry the payment rows the dashboards total up. A REAL `amount` column left
 * over from before the Money migration is the difference between a payment that shows and one
 * that does not, so this table is rebuilt even though the column set already matched v82.
 */
private fun SupportSQLiteDatabase.rebuildContributions() = rebuildV82Table(
    table = "contributions",
    createSql = """
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
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_contributions_member_id` ON `contributions` (`member_id`)",
        "CREATE INDEX IF NOT EXISTS `index_contributions_group_id` ON `contributions` (`group_id`)",
        "CREATE INDEX IF NOT EXISTS `index_contributions_status` ON `contributions` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_contributions_due_date` ON `contributions` (`due_date`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "member_id" to textOf(source, "member_id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "policy_id" to textOf(source, "policy_id"),
        "amount" to moneyOf(source, "amount"),
        "created_at" to textOf(source, "created_at"),
        "due_date" to textOf(source, "due_date", "''"),
        "paid_at" to textOf(source, "paid_at"),
        "status" to textOf(source, "status", "'DUE'"),
        "type" to textOf(source, "type", "'contribution'"),
        "payment_method" to textOf(source, "payment_method", "'bank'"),
        "late_fees_applied" to numberOrZero(source, "late_fees_applied"),
        "late_fee_amount" to moneyOf(source, "late_fee_amount"),
        "principal_amount" to moneyOf(source, "principal_amount"),
        "transaction_id" to textOf(source, "transaction_id"),
        "updated_at" to textOf(source, "updated_at")
    )
}

private fun SupportSQLiteDatabase.rebuildPayments() = rebuildV82Table(
    table = "payments",
    createSql = """
        CREATE TABLE `payments` (
            `id` TEXT NOT NULL, `member_id` TEXT NOT NULL, `group_id` TEXT NOT NULL,
            `amount` TEXT NOT NULL, `payment_type` TEXT NOT NULL,
            `payment_method` TEXT NOT NULL, `transaction_id` TEXT, `status` TEXT NOT NULL,
            `processed_at` TEXT, `created_at` TEXT, `updated_at` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_payments_member_id` ON `payments` (`member_id`)",
        "CREATE INDEX IF NOT EXISTS `index_payments_group_id` ON `payments` (`group_id`)",
        "CREATE INDEX IF NOT EXISTS `index_payments_status` ON `payments` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_payments_payment_type` ON `payments` (`payment_type`)",
        "CREATE INDEX IF NOT EXISTS `index_payments_created_at` ON `payments` (`created_at`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "member_id" to textOf(source, "member_id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "amount" to moneyOf(source, "amount"),
        "payment_type" to textOf(source, "payment_type", "'CONTRIBUTION'"),
        "payment_method" to textOf(source, "payment_method", "'BANK'"),
        "transaction_id" to textOf(source, "transaction_id"),
        "status" to textOf(source, "status", "'PENDING'"),
        "processed_at" to textOf(source, "processed_at"),
        "created_at" to textOf(source, "created_at"),
        "updated_at" to textOf(source, "updated_at", "0")
    )
}

private fun SupportSQLiteDatabase.rebuildPayouts() = rebuildV82Table(
    table = "payouts",
    createSql = """
        CREATE TABLE `payouts` (
            `id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `member_id` TEXT,
            `amount` TEXT NOT NULL, `bank_name` TEXT NOT NULL, `account_no` TEXT NOT NULL,
            `branch_code` TEXT NOT NULL, `status` TEXT NOT NULL, `payout_type` TEXT NOT NULL,
            `description` TEXT, `processed_by` TEXT, `processed_at` TEXT,
            `payout_reference` TEXT, `supporting_document_url` TEXT,
            `supporting_document_status` TEXT NOT NULL, `created_at` TEXT, `updated_at` TEXT,
            `metadata` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_payouts_group_id` ON `payouts` (`group_id`)",
        "CREATE INDEX IF NOT EXISTS `index_payouts_status` ON `payouts` (`status`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "member_id" to textOf(source, "member_id"),
        "amount" to moneyOf(source, "amount"),
        "bank_name" to textOf(source, "bank_name", "''"),
        "account_no" to textOf(source, "account_no", "''"),
        "branch_code" to textOf(source, "branch_code", "''"),
        "status" to textOf(source, "status", "'PENDING'"),
        "payout_type" to textOf(source, "payout_type", "'GENERAL'"),
        "description" to textOf(source, "description"),
        "processed_by" to textOf(source, "processed_by"),
        "processed_at" to textOf(source, "processed_at"),
        "payout_reference" to textOf(source, "payout_reference"),
        "supporting_document_url" to textOf(source, "supporting_document_url"),
        "supporting_document_status" to coalescedText(source, "supporting_document_status", "'PENDING'"),
        "created_at" to textOf(source, "created_at"),
        "updated_at" to textOf(source, "updated_at"),
        "metadata" to textOf(source, "metadata")
    )
}

private fun SupportSQLiteDatabase.rebuildLoans() = rebuildV82Table(
    table = "loans",
    createSql = """
        CREATE TABLE `loans` (
            `id` TEXT NOT NULL, `member_id` TEXT NOT NULL, `group_id` TEXT NOT NULL,
            `amount` TEXT NOT NULL, `interest_rate` REAL NOT NULL,
            `total_to_repay` TEXT NOT NULL, `total_repaid` TEXT NOT NULL,
            `monthly_repayment` TEXT NOT NULL, `start_date` TEXT, `end_date` TEXT,
            `next_payment_date` TEXT, `status` TEXT NOT NULL, `purpose` TEXT,
            `contract_url` TEXT, `surety_amount` TEXT, `reviewed_by` TEXT, `reviewed_at` TEXT,
            `admin_notes` TEXT, `rejection_reason` TEXT, `amortization_type` TEXT NOT NULL,
            `effective_apr` REAL, `created_at` TEXT, `updated_at` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_loans_member_id` ON `loans` (`member_id`)",
        "CREATE INDEX IF NOT EXISTS `index_loans_group_id` ON `loans` (`group_id`)",
        "CREATE INDEX IF NOT EXISTS `index_loans_status` ON `loans` (`status`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "member_id" to textOf(source, "member_id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "amount" to moneyOf(source, "amount"),
        "interest_rate" to numberOrZero(source, "interest_rate"),
        "total_to_repay" to moneyOf(source, "total_to_repay"),
        "total_repaid" to moneyOf(source, "total_repaid"),
        "monthly_repayment" to moneyOf(source, "monthly_repayment"),
        "start_date" to textOf(source, "start_date"),
        "end_date" to textOf(source, "end_date"),
        "next_payment_date" to textOf(source, "next_payment_date"),
        "status" to textOf(source, "status", "'PENDING'"),
        "purpose" to textOf(source, "purpose"),
        "contract_url" to textOf(source, "contract_url"),
        "surety_amount" to nullableMoneyOf(source, "surety_amount"),
        "reviewed_by" to textOf(source, "reviewed_by"),
        "reviewed_at" to textOf(source, "reviewed_at"),
        "admin_notes" to textOf(source, "admin_notes"),
        "rejection_reason" to textOf(source, "rejection_reason"),
        "amortization_type" to coalescedText(source, "amortization_type", "'FLAT'"),
        "effective_apr" to textOf(source, "effective_apr"),
        "created_at" to textOf(source, "created_at"),
        "updated_at" to textOf(source, "updated_at")
    )
}

private fun SupportSQLiteDatabase.rebuildLoanRepayments() = rebuildV82Table(
    table = "loan_repayments",
    createSql = """
        CREATE TABLE `loan_repayments` (
            `id` TEXT NOT NULL, `loan_id` TEXT NOT NULL, `member_id` TEXT NOT NULL,
            `group_id` TEXT NOT NULL, `amount` TEXT NOT NULL, `paid_at` TEXT,
            `payment_method` TEXT NOT NULL, `transaction_id` TEXT, `created_at` TEXT,
            `updated_at` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_loan_repayments_loan_id` ON `loan_repayments` (`loan_id`)",
        "CREATE INDEX IF NOT EXISTS `index_loan_repayments_member_id` ON `loan_repayments` (`member_id`)",
        "CREATE INDEX IF NOT EXISTS `index_loan_repayments_group_id` ON `loan_repayments` (`group_id`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "loan_id" to textOf(source, "loan_id", "''"),
        "member_id" to textOf(source, "member_id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "amount" to moneyOf(source, "amount"),
        "paid_at" to textOf(source, "paid_at"),
        "payment_method" to textOf(source, "payment_method", "'BANK'"),
        "transaction_id" to textOf(source, "transaction_id"),
        "created_at" to textOf(source, "created_at"),
        "updated_at" to textOf(source, "updated_at")
    )
}

private fun SupportSQLiteDatabase.rebuildBeneficiaryClaims() = rebuildV82Table(
    table = "beneficiary_payout_claims",
    createSql = """
        CREATE TABLE `beneficiary_payout_claims` (
            `id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `member_id` TEXT NOT NULL,
            `beneficiary_id` TEXT NOT NULL, `beneficiary_name` TEXT NOT NULL,
            `face_photo_url` TEXT, `face_photo_status` TEXT NOT NULL,
            `beneficiary_id_url` TEXT, `beneficiary_id_status` TEXT NOT NULL,
            `death_certificate_url` TEXT, `death_certificate_status` TEXT NOT NULL,
            `member_id_url` TEXT, `member_id_status` TEXT NOT NULL,
            `cause_of_death` TEXT NOT NULL, `date_of_death` TEXT NOT NULL,
            `claim_amount` TEXT NOT NULL, `bank_name` TEXT NOT NULL, `account_no` TEXT NOT NULL,
            `branch_code` TEXT NOT NULL, `account_holder` TEXT NOT NULL, `notes` TEXT,
            `status` TEXT NOT NULL, `reviewed_by` TEXT, `reviewed_at` TEXT, `admin_notes` TEXT,
            `rejection_reason` TEXT, `created_at` TEXT, `updated_at` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_beneficiary_payout_claims_group_id` ON `beneficiary_payout_claims` (`group_id`)",
        "CREATE INDEX IF NOT EXISTS `index_beneficiary_payout_claims_member_id` ON `beneficiary_payout_claims` (`member_id`)",
        "CREATE INDEX IF NOT EXISTS `index_beneficiary_payout_claims_beneficiary_id` ON `beneficiary_payout_claims` (`beneficiary_id`)",
        "CREATE INDEX IF NOT EXISTS `index_beneficiary_payout_claims_status` ON `beneficiary_payout_claims` (`status`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "member_id" to textOf(source, "member_id", "''"),
        "beneficiary_id" to textOf(source, "beneficiary_id", "''"),
        "beneficiary_name" to textOf(source, "beneficiary_name", "''"),
        "face_photo_url" to textOf(source, "face_photo_url"),
        "face_photo_status" to coalescedText(source, "face_photo_status", "'PENDING'"),
        "beneficiary_id_url" to textOf(source, "beneficiary_id_url"),
        "beneficiary_id_status" to coalescedText(source, "beneficiary_id_status", "'PENDING'"),
        "death_certificate_url" to textOf(source, "death_certificate_url"),
        "death_certificate_status" to coalescedText(source, "death_certificate_status", "'PENDING'"),
        "member_id_url" to textOf(source, "member_id_url"),
        "member_id_status" to coalescedText(source, "member_id_status", "'PENDING'"),
        "cause_of_death" to textOf(source, "cause_of_death", "''"),
        "date_of_death" to textOf(source, "date_of_death", "''"),
        "claim_amount" to moneyOf(source, "claim_amount"),
        "bank_name" to textOf(source, "bank_name", "''"),
        "account_no" to textOf(source, "account_no", "''"),
        "branch_code" to textOf(source, "branch_code", "''"),
        "account_holder" to textOf(source, "account_holder", "''"),
        "notes" to textOf(source, "notes"),
        "status" to textOf(source, "status", "'SUBMITTED'"),
        "reviewed_by" to textOf(source, "reviewed_by"),
        "reviewed_at" to textOf(source, "reviewed_at"),
        "admin_notes" to textOf(source, "admin_notes"),
        "rejection_reason" to textOf(source, "rejection_reason"),
        "created_at" to textOf(source, "created_at"),
        "updated_at" to textOf(source, "updated_at", "0")
    )
}

private fun SupportSQLiteDatabase.rebuildGroupLedger() = rebuildV82Table(
    table = "group_ledger",
    createSql = """
        CREATE TABLE `group_ledger` (
            `id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `member_id` TEXT,
            `transaction_id` TEXT, `amount` TEXT NOT NULL, `balance_after` TEXT NOT NULL,
            `description` TEXT NOT NULL, `category` TEXT NOT NULL,
            `is_platform` INTEGER NOT NULL, `created_at` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_group_ledger_group_id` ON `group_ledger` (`group_id`)"
    ),
) { source ->
    val groupId = when {
        "group_id" in source -> "COALESCE(`group_id`, '')"
        "groupId" in source -> "COALESCE(`groupId`, '')"
        else -> "''"
    }
    val transactionId = when {
        "transaction_id" in source -> "`transaction_id`"
        "transactionId" in source -> "`transactionId`"
        else -> "NULL"
    }
    val balanceAfter = when {
        "balance_after" in source -> "printf('%.2f', COALESCE(`balance_after`, `amount`, 0))"
        "balanceAfter" in source -> "printf('%.2f', COALESCE(`balanceAfter`, `amount`, 0))"
        else -> moneyOf(source, "amount")
    }
    val createdAt = when {
        "created_at" in source -> "`created_at`"
        "createdAt" in source -> "`createdAt`"
        else -> "NULL"
    }
    listOf(
        "id" to textOf(source, "id", "lower(hex(randomblob(16)))"),
        "group_id" to groupId,
        "member_id" to textOf(source, "member_id"),
        "transaction_id" to transactionId,
        "amount" to moneyOf(source, "amount"),
        "balance_after" to balanceAfter,
        "description" to coalescedText(source, "description", "''"),
        "category" to coalescedText(source, "category", "'OTHER'"),
        "is_platform" to numberOrZero(source, "is_platform"),
        "created_at" to createdAt
    )
}

private fun SupportSQLiteDatabase.rebuildSettlements() = rebuildV82Table(
    table = "settlements",
    createSql = """
        CREATE TABLE `settlements` (
            `id` TEXT NOT NULL, `transaction_id` TEXT NOT NULL, `provider_reference` TEXT,
            `status` TEXT NOT NULL, `settled_at` INTEGER, `fee_deducted` TEXT NOT NULL,
            PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_settlements_transaction_id` ON `settlements` (`transaction_id`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "transaction_id" to textOf(source, "transaction_id", "''"),
        "provider_reference" to textOf(source, "provider_reference"),
        "status" to textOf(source, "status", "'PENDING'"),
        "settled_at" to if ("settled_at" in source) "CAST(`settled_at` AS INTEGER)" else "NULL",
        "fee_deducted" to moneyOf(source, "fee_deducted")
    )
}

private fun SupportSQLiteDatabase.rebuildReconciliations() = rebuildV82Table(
    table = "reconciliations",
    createSql = """
        CREATE TABLE `reconciliations` (
            `id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `period_start` TEXT NOT NULL,
            `period_end` TEXT NOT NULL, `status` TEXT NOT NULL,
            `ledger_balance` TEXT NOT NULL, `actual_balance` TEXT NOT NULL,
            `discrepancy` TEXT NOT NULL, `notes` TEXT, `performed_by` TEXT, `created_at` TEXT,
            PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_reconciliations_group_id` ON `reconciliations` (`group_id`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "period_start" to textOf(source, "period_start", "''"),
        "period_end" to textOf(source, "period_end", "''"),
        "status" to textOf(source, "status", "'PENDING'"),
        "ledger_balance" to moneyOf(source, "ledger_balance"),
        "actual_balance" to moneyOf(source, "actual_balance"),
        "discrepancy" to moneyOf(source, "discrepancy"),
        "notes" to textOf(source, "notes"),
        "performed_by" to textOf(source, "performed_by"),
        "created_at" to textOf(source, "created_at")
    )
}

private fun SupportSQLiteDatabase.rebuildMemberBehaviorTrack() = rebuildV82Table(
    table = "member_behavior_track",
    createSql = """
        CREATE TABLE `member_behavior_track` (
            `id` TEXT NOT NULL, `member_id` TEXT NOT NULL, `member_id_number` TEXT NOT NULL,
            `group_id` TEXT NOT NULL,
            `total_contributions` INTEGER NOT NULL, `on_time_contributions` INTEGER NOT NULL,
            `late_contributions` INTEGER NOT NULL, `overdue_count` INTEGER NOT NULL,
            `missed_contributions` INTEGER NOT NULL,
            `payment_consistency_score` REAL NOT NULL, `average_days_late` REAL NOT NULL,
            `current_payment_streak` INTEGER NOT NULL, `longest_payment_streak` INTEGER NOT NULL,
            `has_broken_streak_recently` INTEGER NOT NULL,
            `total_amount_contributed` TEXT NOT NULL, `total_late_fees_paid` TEXT NOT NULL,
            `pending_late_fees` TEXT NOT NULL, `total_outstanding_amount` TEXT NOT NULL,
            `total_loans_requested` INTEGER NOT NULL, `total_loans_approved` INTEGER NOT NULL,
            `total_loans_completed` INTEGER NOT NULL, `active_loans` INTEGER NOT NULL,
            `overdue_loans` INTEGER NOT NULL, `loan_default_count` INTEGER NOT NULL,
            `loan_completion_rate` REAL NOT NULL,
            `duplicate_transaction_count` INTEGER NOT NULL,
            `suspicious_activity_count` INTEGER NOT NULL,
            `unusual_payment_patterns` INTEGER NOT NULL,
            `multiple_accounts_detected` INTEGER NOT NULL,
            `velocity_check_failed` INTEGER NOT NULL,
            `rapid_disbursement_attempts` INTEGER NOT NULL,
            `member_status` TEXT NOT NULL, `fraud_risk_level` TEXT NOT NULL,
            `fraud_score` REAL NOT NULL, `behavior_score` REAL NOT NULL,
            `is_flagged_for_review` INTEGER NOT NULL, `is_suspended` INTEGER NOT NULL,
            `suspension_reason` TEXT, `review_notes` TEXT, `months_in_group` INTEGER NOT NULL,
            `joined_at` TEXT, `last_activity_at` TEXT, `last_contribution_at` TEXT,
            `admin_notes` TEXT, `last_reviewed_at` TEXT, `reviewed_by` TEXT,
            `created_at` TEXT, `updated_at` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_member_behavior_track_member_id` ON `member_behavior_track` (`member_id`)",
        "CREATE INDEX IF NOT EXISTS `index_member_behavior_track_member_id_number` ON `member_behavior_track` (`member_id_number`)",
        "CREATE INDEX IF NOT EXISTS `index_member_behavior_track_group_id` ON `member_behavior_track` (`group_id`)",
        "CREATE INDEX IF NOT EXISTS `index_member_behavior_track_fraud_risk_level` ON `member_behavior_track` (`fraud_risk_level`)",
        "CREATE INDEX IF NOT EXISTS `index_member_behavior_track_is_flagged_for_review` ON `member_behavior_track` (`is_flagged_for_review`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "lower(hex(randomblob(16)))"),
        "member_id" to textOf(source, "member_id", "''"),
        "member_id_number" to coalescedText(source, "member_id_number", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "total_contributions" to numberOrZero(source, "total_contributions"),
        "on_time_contributions" to numberOrZero(source, "on_time_contributions"),
        "late_contributions" to numberOrZero(source, "late_contributions"),
        "overdue_count" to numberOrZero(source, "overdue_count"),
        "missed_contributions" to numberOrZero(source, "missed_contributions"),
        "payment_consistency_score" to numberOrZero(source, "payment_consistency_score"),
        "average_days_late" to numberOrZero(source, "average_days_late"),
        "current_payment_streak" to numberOrZero(source, "current_payment_streak"),
        "longest_payment_streak" to numberOrZero(source, "longest_payment_streak"),
        "has_broken_streak_recently" to numberOrZero(source, "has_broken_streak_recently"),
        "total_amount_contributed" to moneyOf(source, "total_amount_contributed"),
        "total_late_fees_paid" to moneyOf(source, "total_late_fees_paid"),
        "pending_late_fees" to moneyOf(source, "pending_late_fees"),
        "total_outstanding_amount" to moneyOf(source, "total_outstanding_amount"),
        "total_loans_requested" to numberOrZero(source, "total_loans_requested"),
        "total_loans_approved" to numberOrZero(source, "total_loans_approved"),
        "total_loans_completed" to numberOrZero(source, "total_loans_completed"),
        "active_loans" to numberOrZero(source, "active_loans"),
        "overdue_loans" to numberOrZero(source, "overdue_loans"),
        "loan_default_count" to numberOrZero(source, "loan_default_count"),
        "loan_completion_rate" to numberOrZero(source, "loan_completion_rate"),
        "duplicate_transaction_count" to numberOrZero(source, "duplicate_transaction_count"),
        "suspicious_activity_count" to numberOrZero(source, "suspicious_activity_count"),
        "unusual_payment_patterns" to numberOrZero(source, "unusual_payment_patterns"),
        "multiple_accounts_detected" to numberOrZero(source, "multiple_accounts_detected"),
        "velocity_check_failed" to numberOrZero(source, "velocity_check_failed"),
        "rapid_disbursement_attempts" to numberOrZero(source, "rapid_disbursement_attempts"),
        "member_status" to coalescedText(source, "member_status", "'FAIR'"),
        "fraud_risk_level" to coalescedText(source, "fraud_risk_level", "'LOW'"),
        "fraud_score" to numberOrZero(source, "fraud_score"),
        "behavior_score" to numberOrZero(source, "behavior_score"),
        "is_flagged_for_review" to numberOrZero(source, "is_flagged_for_review"),
        "is_suspended" to numberOrZero(source, "is_suspended"),
        "suspension_reason" to textOf(source, "suspension_reason"),
        "review_notes" to textOf(source, "review_notes"),
        "months_in_group" to numberOrZero(source, "months_in_group"),
        "joined_at" to textOf(source, "joined_at"),
        "last_activity_at" to textOf(source, "last_activity_at"),
        "last_contribution_at" to textOf(source, "last_contribution_at"),
        "admin_notes" to textOf(source, "admin_notes"),
        "last_reviewed_at" to textOf(source, "last_reviewed_at"),
        "reviewed_by" to textOf(source, "reviewed_by"),
        "created_at" to textOf(source, "created_at"),
        "updated_at" to textOf(source, "updated_at")
    )
}

private fun SupportSQLiteDatabase.rebuildFraudDetectionEvents() = rebuildV82Table(
    table = "fraud_detection_events",
    createSql = """
        CREATE TABLE `fraud_detection_events` (
            `id` TEXT NOT NULL, `member_id` TEXT NOT NULL, `group_id` TEXT NOT NULL,
            `event_type` TEXT NOT NULL, `severity` TEXT NOT NULL,
            `details_json` TEXT NOT NULL, `action_taken` TEXT, `resolved` INTEGER NOT NULL,
            `created_at` TEXT, `updated_at` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_fraud_detection_events_member_id` ON `fraud_detection_events` (`member_id`)",
        "CREATE INDEX IF NOT EXISTS `index_fraud_detection_events_group_id` ON `fraud_detection_events` (`group_id`)",
        "CREATE INDEX IF NOT EXISTS `index_fraud_detection_events_severity` ON `fraud_detection_events` (`severity`)",
        "CREATE INDEX IF NOT EXISTS `index_fraud_detection_events_resolved` ON `fraud_detection_events` (`resolved`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "member_id" to textOf(source, "member_id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "event_type" to textOf(source, "event_type", "''"),
        "severity" to textOf(source, "severity", "'LOW'"),
        "details_json" to textOf(source, "details_json", "'{}'"),
        "action_taken" to textOf(source, "action_taken"),
        "resolved" to numberOrZero(source, "resolved"),
        "created_at" to textOf(source, "created_at"),
        "updated_at" to textOf(source, "updated_at")
    )
}

private fun SupportSQLiteDatabase.rebuildBehaviorAnalyticsSummary() = rebuildV82Table(
    table = "behavior_analytics_summary",
    createSql = """
        CREATE TABLE `behavior_analytics_summary` (
            `group_id` TEXT NOT NULL, `total_members_tracked` INTEGER NOT NULL,
            `excellent_members` INTEGER NOT NULL, `good_members` INTEGER NOT NULL,
            `fair_members` INTEGER NOT NULL, `poor_members` INTEGER NOT NULL,
            `suspended_members` INTEGER NOT NULL, `high_fraud_risk_count` INTEGER NOT NULL,
            `flagged_members_count` INTEGER NOT NULL,
            `average_behavior_score` REAL NOT NULL, `average_fraud_score` REAL NOT NULL,
            `on_time_payment_rate` REAL NOT NULL, `loan_default_rate` REAL NOT NULL,
            `calculated_at` TEXT, PRIMARY KEY(`group_id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_behavior_analytics_summary_group_id` ON `behavior_analytics_summary` (`group_id`)"
    ),
) { source ->
    listOf(
        "group_id" to textOf(source, "group_id", "''"),
        "total_members_tracked" to numberOrZero(source, "total_members_tracked"),
        "excellent_members" to numberOrZero(source, "excellent_members"),
        "good_members" to numberOrZero(source, "good_members"),
        "fair_members" to numberOrZero(source, "fair_members"),
        "poor_members" to numberOrZero(source, "poor_members"),
        "suspended_members" to numberOrZero(source, "suspended_members"),
        "high_fraud_risk_count" to numberOrZero(source, "high_fraud_risk_count"),
        "flagged_members_count" to numberOrZero(source, "flagged_members_count"),
        "average_behavior_score" to numberOrZero(source, "average_behavior_score"),
        "average_fraud_score" to numberOrZero(source, "average_fraud_score"),
        "on_time_payment_rate" to numberOrZero(source, "on_time_payment_rate"),
        "loan_default_rate" to numberOrZero(source, "loan_default_rate"),
        "calculated_at" to textOf(source, "calculated_at")
    )
}

private fun SupportSQLiteDatabase.rebuildPlatformFeeConfig() = rebuildV82Table(
    table = "platform_fee_config",
    createSql = """
        CREATE TABLE `platform_fee_config` (
            `groupType` TEXT NOT NULL, `roscaCycle` INTEGER NOT NULL,
            `registrationFee` TEXT NOT NULL, `monthlyFee` TEXT NOT NULL,
            `shortfall_recovery_fee` TEXT NOT NULL, `updated_at` TEXT,
            PRIMARY KEY(`groupType`, `roscaCycle`)
        )
    """.trimIndent(),
    indices = emptyList(),
) { source ->
    val shortfallFee = when {
        "shortfall_recovery_fee" in source -> moneyOf(source, "shortfall_recovery_fee")
        "shortfallRecoveryFee" in source -> moneyOf(source, "shortfallRecoveryFee")
        else -> "'0.00'"
    }
    val updatedAt = when {
        "updated_at" in source -> "`updated_at`"
        "updatedAt" in source -> "`updatedAt`"
        else -> "NULL"
    }
    listOf(
        "groupType" to textOf(source, "groupType", "''"),
        "roscaCycle" to numberOrZero(source, "roscaCycle"),
        "registrationFee" to moneyOf(source, "registrationFee"),
        "monthlyFee" to moneyOf(source, "monthlyFee"),
        "shortfall_recovery_fee" to shortfallFee,
        "updated_at" to updatedAt
    )
}

private fun SupportSQLiteDatabase.rebuildBankTransactions() = rebuildV82Table(
    table = "bank_transactions",
    createSql = """
        CREATE TABLE `bank_transactions` (
            `id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `bank_reference` TEXT NOT NULL,
            `amount` TEXT NOT NULL, `transaction_date` TEXT NOT NULL, `description` TEXT,
            `status` TEXT NOT NULL, `reconciled_with` TEXT, `created_at` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_bank_transactions_group_id` ON `bank_transactions` (`group_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_bank_transactions_bank_reference` ON `bank_transactions` (`bank_reference`)",
        "CREATE INDEX IF NOT EXISTS `index_bank_transactions_status` ON `bank_transactions` (`status`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "bank_reference" to textOf(source, "bank_reference", "''"),
        "amount" to moneyOf(source, "amount"),
        "transaction_date" to textOf(source, "transaction_date", "''"),
        "description" to textOf(source, "description"),
        "status" to textOf(source, "status", "'UNRECONCILED'"),
        "reconciled_with" to textOf(source, "reconciled_with"),
        "created_at" to textOf(source, "created_at")
    )
}

private fun SupportSQLiteDatabase.rebuildFinancialProviders() = rebuildV82Table(
    table = "financial_providers",
    createSql = """
        CREATE TABLE `financial_providers` (
            `id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL,
            `logo_url` TEXT, `supported_ops` TEXT NOT NULL, `is_active` INTEGER NOT NULL,
            `metadata` TEXT NOT NULL, `updated_at` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = emptyList(),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "name" to textOf(source, "name", "''"),
        "type" to textOf(source, "type", "'BANK'"),
        "logo_url" to textOf(source, "logo_url"),
        "supported_ops" to textOf(source, "supported_ops", "'[]'"),
        "is_active" to numberOrZero(source, "is_active"),
        "metadata" to textOf(source, "metadata", "'{}'"),
        "updated_at" to textOf(source, "updated_at")
    )
}

private fun SupportSQLiteDatabase.rebuildLedgerCheckpoints() = rebuildV82Table(
    table = "ledger_checkpoints",
    createSql = """
        CREATE TABLE `ledger_checkpoints` (
            `id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `ledger_entry_id` TEXT NOT NULL,
            `balance` TEXT NOT NULL, `created_at` TEXT, PRIMARY KEY(`id`)
        )
    """.trimIndent(),
    indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_ledger_checkpoints_group_id` ON `ledger_checkpoints` (`group_id`)"
    ),
) { source ->
    listOf(
        "id" to textOf(source, "id", "''"),
        "group_id" to textOf(source, "group_id", "''"),
        "ledger_entry_id" to textOf(source, "ledger_entry_id", "''"),
        "balance" to moneyOf(source, "balance"),
        "created_at" to textOf(source, "created_at")
    )
}

// ── Tables that never had an upgrade migration ──────────────────────────────

private fun SupportSQLiteDatabase.createBankConnections() {
    if (tableExists("bank_connections")) return
    execSQL(
        """
        CREATE TABLE `bank_connections` (
            `id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `provider_id` TEXT NOT NULL,
            `status` TEXT NOT NULL, `account_name` TEXT, `account_number_hidden` TEXT,
            `linked_at` TEXT NOT NULL, `expires_at` TEXT, `updated_at` TEXT,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )
    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bank_connections_group_id` ON `bank_connections` (`group_id`)")
    execSQL("CREATE INDEX IF NOT EXISTS `index_bank_connections_provider_id` ON `bank_connections` (`provider_id`)")
}

private fun SupportSQLiteDatabase.createGroupPolls() {
    if (!tableExists("group_polls")) {
        execSQL(
            """
            CREATE TABLE `group_polls` (
                `id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `created_by_member_id` TEXT,
                `title` TEXT NOT NULL, `description` TEXT, `type` TEXT NOT NULL,
                `status` TEXT NOT NULL, `effect_status` TEXT NOT NULL, `effect_data` TEXT,
                `allow_multiple_choice` INTEGER NOT NULL, `starts_at` TEXT, `ends_at` TEXT,
                `created_at` TEXT, `updated_at` TEXT, PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        execSQL("CREATE INDEX IF NOT EXISTS `index_group_polls_group_id` ON `group_polls` (`group_id`)")
        execSQL("CREATE INDEX IF NOT EXISTS `index_group_polls_status` ON `group_polls` (`status`)")
    }
    if (!tableExists("group_poll_options")) {
        execSQL(
            """
            CREATE TABLE `group_poll_options` (
                `id` TEXT NOT NULL, `poll_id` TEXT NOT NULL, `label` TEXT NOT NULL,
                `position` INTEGER NOT NULL, `created_at` TEXT, PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        execSQL("CREATE INDEX IF NOT EXISTS `index_group_poll_options_poll_id` ON `group_poll_options` (`poll_id`)")
    }
    if (!tableExists("group_poll_votes")) {
        execSQL(
            """
            CREATE TABLE `group_poll_votes` (
                `id` TEXT NOT NULL, `poll_id` TEXT NOT NULL, `option_id` TEXT NOT NULL,
                `member_id` TEXT NOT NULL, `group_id` TEXT NOT NULL, `created_at` TEXT,
                `updated_at` TEXT, PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        execSQL("CREATE INDEX IF NOT EXISTS `index_group_poll_votes_poll_id` ON `group_poll_votes` (`poll_id`)")
        execSQL("CREATE INDEX IF NOT EXISTS `index_group_poll_votes_member_id` ON `group_poll_votes` (`member_id`)")
        execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_group_poll_votes_poll_id_member_id` ON `group_poll_votes` (`poll_id`, `member_id`)")
    }
}
