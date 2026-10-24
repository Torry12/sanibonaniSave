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
        } catch (_: Exception) { /* column may already exist */ }
        try {
            db.execSQL("ALTER TABLE contributions ADD COLUMN late_fees_applied BOOLEAN")
        } catch (_: Exception) { /* column may already exist */ }
    }
}

// Placeholder migrations for versions 9-15 (safe no-ops)
val MIGRATION_9_10 = object : Migration(9, 10) { override fun migrate(db: SupportSQLiteDatabase) {} }
