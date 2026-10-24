package com.sanibonani.save.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal fun SupportSQLiteDatabase.safeExec(sql: String) {
    try {
        execSQL(sql)
    } catch (_: Exception) {
    }
}

internal fun SupportSQLiteDatabase.tableExists(tableName: String): Boolean {
    query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'").use { cursor ->
        return cursor.moveToFirst()
    }
}

internal fun SupportSQLiteDatabase.tableColumns(tableName: String): Set<String> {
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

internal fun memberSelectExpr(
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

internal fun SupportSQLiteDatabase.recreateMembersTablePreservingData() {
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
