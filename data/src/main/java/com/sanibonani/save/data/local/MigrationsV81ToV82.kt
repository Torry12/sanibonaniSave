package com.sanibonani.save.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Repairs every table whose legacy migration chain never caught up with the Room entity
 * schema. Money columns were created as REAL before the Money (BigDecimal) migration and
 * several tables gained columns or lost NOT NULL constraints without a rebuild, so a
 * pre-Money install would fail Room's schema validation as soon as it opened v82.
 *
 * Room validates against these tables one at a time, so the whole set is repaired here
 * rather than one failing release at a time. Data is preserved: REAL money is converted
 * to canonical 2-dp strings, epoch-millis columns keep their numeric value as text, and
 * columns that did not exist yet are filled with the entity defaults.
 */
val MIGRATION_81_82 = object : Migration(81, 82) {
    override fun migrate(db: SupportSQLiteDatabase) {
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
    if (name in source) "printf('%.2f', COALESCE(`$name`, 0))" else fallback

private fun nullableMoneyOf(source: Set<String>, name: String): String =
    if (name in source) "CASE WHEN `$name` IS NULL THEN NULL ELSE printf('%.2f', `$name`) END" else "NULL"

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
