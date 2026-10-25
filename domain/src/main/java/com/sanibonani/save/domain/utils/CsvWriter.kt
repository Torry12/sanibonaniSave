package com.sanibonani.save.domain.utils

/**
 * RFC-4180 CSV writer used by every financial export.
 *
 * Kept free of Android and of file I/O so the money-facing behaviour is unit-testable, and
 * centralized because escaping done per-call-site is escaping done inconsistently.
 */
object CsvWriter {

    fun headerRow(vararg fields: String): String = row(*fields)

    fun row(vararg fields: Any?): String =
        fields.joinToString(",") { format(it) }

    /** The line terminator a CSV file is written with; CRLF is what RFC-4180 specifies. */
    const val LINE_SEPARATOR: String = "\r\n"

    fun format(value: Any?): String = escape(value?.toString().orEmpty())

    fun escape(raw: String): String {
        val neutralized = guardFormula(raw)
        val needsQuoting = neutralized.any { it == '"' || it == ',' || it == '\n' || it == '\r' } ||
            neutralized.firstOrNull() == ' ' || neutralized.lastOrNull() == ' '
        return if (needsQuoting) {
            "\"" + neutralized.replace("\"", "\"\"") + "\""
        } else {
            neutralized
        }
    }

    /**
     * Spreadsheet applications evaluate a cell that begins with `=`, `+`, `@`, tab or CR as a
     * formula, so a description like `=cmd|...` uploaded by a member becomes code the moment an
     * admin opens the statement. A leading `-` is only dangerous when the cell is not a number,
     * which is why negative amounts such as `-250.00` are left untouched.
     */
    private fun guardFormula(raw: String): String {
        if (raw.isEmpty()) return raw
        if (raw.toBigDecimalOrNull() != null) return raw
        val first = raw[0]
        return if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r')
            "'" + raw
        else raw
    }
}
