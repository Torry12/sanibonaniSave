package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvWriterTest {

    @Test
    fun moneyColumnsRenderAsPlainAmounts() {
        val row = CsvWriter.row("2026-10-14", Money("250.00"), Money("-1200.50"))
        assertEquals("2026-10-14,250.00,-1200.50", row)
    }

    @Test
    fun separatorDelimitsEveryField() {
        assertEquals("A,B,C", CsvWriter.headerRow("A", "B", "C"))
    }

    @Test
    fun embeddedCommaAndQuoteAreEscaped() {
        assertEquals("\"Mkhize, Sipho\"", CsvWriter.escape("Mkhize, Sipho"))
        assertEquals("\"He said \"\"pay now\"\"\"", CsvWriter.escape("He said \"pay now\""))
    }

    @Test
    fun embeddedNewlineIsQuotedNotSplit() {
        assertEquals("\"line one\nline two\"", CsvWriter.escape("line one\nline two"))
    }

    @Test
    fun formulaLeadingCharactersAreNeutralized() {
        assertEquals("'=SUM(A1:A9)", CsvWriter.escape("=SUM(A1:A9)"))
        assertEquals("'@member:1234", CsvWriter.escape("@member:1234"))
        assertEquals("'+1+1", CsvWriter.escape("+1+1"))
    }

    @Test
    fun negativeAmountsStayNumeric() {
        assertEquals("-250.00", CsvWriter.escape("-250.00"))
    }

    @Test
    fun onlyLeadingFormulaCharactersAreGuarded() {
        assertEquals("refund = owed", CsvWriter.escape("refund = owed"))
        assertEquals("\"'=1,2\"", CsvWriter.escape("=1,2"))
    }

    @Test
    fun nullAndBlankFieldsDegradeToEmptyCell() {
        assertEquals(",", CsvWriter.row(null, null))
        assertEquals("", CsvWriter.row(null))
    }

    @Test
    fun leadingTrailingSpaceIsQuoted() {
        assertEquals("\" padded\"", CsvWriter.escape(" padded"))
    }
}
