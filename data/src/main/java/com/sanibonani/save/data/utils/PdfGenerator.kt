package com.sanibonani.save.data.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Reusable utility for generating PDF documents using Android's PdfDocument API.
 */
class PdfGenerator(
    private val pageWidth: Int = 595, // A4 width
    private val pageHeight: Int = 842, // A4 height
    private val margin: Float = 40f
) {
    private val document = PdfDocument()
    private var currentPage: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var currentY = margin
    private var pageNumber = 1

    private val titlePaint = Paint().apply {
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val headerPaint = Paint().apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val bodyPaint = Paint().apply {
        textSize = 10f
        isAntiAlias = true
    }

    private val subPaint = Paint().apply {
        textSize = 9f
        isAntiAlias = true
        alpha = 180
    }

    fun startNewPage() {
        finishPage()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
        currentPage = document.startPage(pageInfo)
        canvas = currentPage?.canvas
        currentY = margin
    }

    fun drawText(text: String, x: Float = margin, isTitle: Boolean = false, isBold: Boolean = false, spacing: Float = 20f) {
        val paint = when {
            isTitle -> titlePaint
            isBold -> headerPaint
            else -> bodyPaint
        }

        checkSpace(spacing)
        canvas?.drawText(text, x, currentY, paint)
        currentY += spacing
    }

    fun drawLine(thickness: Float = 1f, color: Int = android.graphics.Color.LTGRAY) {
        val paint = Paint().apply {
            this.color = color
            strokeWidth = thickness
        }
        checkSpace(10f)
        canvas?.drawLine(margin, currentY, pageWidth - margin, currentY, paint)
        currentY += 10f
    }

    fun drawRow(columns: List<String>, xOffsets: List<Float>, isHeader: Boolean = false) {
        val paint = if (isHeader) headerPaint else bodyPaint
        val spacing = if (isHeader) 25f else 18f

        checkSpace(spacing)
        columns.forEachIndexed { index, text ->
            val x = xOffsets.getOrNull(index) ?: (margin + (index * 100f))
            canvas?.drawText(text, x, currentY, paint)
        }
        currentY += spacing
    }

    fun drawParagraph(text: String, spacing: Float = 20f) {
        val maxWidth = pageWidth - (margin * 2)
        val words = text.split(" ")
        var line = ""

        words.forEach { word ->
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (bodyPaint.measureText(testLine) < maxWidth) {
                line = testLine
            } else {
                drawText(line, spacing = spacing)
                line = word
            }
        }
        if (line.isNotEmpty()) {
            drawText(line, spacing = spacing)
        }
    }

    fun checkSpace(required: Float) {
        if (canvas == null || currentY + required >= pageHeight - margin) {
            startNewPage()
        }
    }

    fun save(file: File): Result<File> = runCatching {
        finishPage()
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        file
    }

    private fun finishPage() {
        currentPage?.let { document.finishPage(it) }
        currentPage = null
        canvas = null
    }
}
