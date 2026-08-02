package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {

    fun sharePdf(context: Context, reportText: String, caName: String, date: String) {
        try {
            val fileName = sanitizeFilename(caName, date) + ".pdf"
            val pdfFile = createFormattedPdf(context, reportText, fileName)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "com.mypump.cal.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share PDF Report"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFormattedPdf(context: Context, text: String, fileName: String): File {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 35f
        
        val lines = text.split("\n")
        
        // --- DRY RUN TO CALCULATE TOTAL REQUIRED HEIGHT ---
        var dryY = 30f
        var dryInNozzleBlock = false
        var dryInDenomsBlock = false
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                dryY += 4f
                continue
            }
            if (line.matches(Regex("[-=]{3,}"))) {
                dryY += 6f
                continue
            }
            if (line.contains("D R INAMDAR PETROLEUM")) {
                dryY += 32f
                continue
            }
            if (line.contains("AUDIT REPORT") || line.contains("RECONCILE")) {
                dryY += 16f
                continue
            }
            if (line.startsWith("Date:") || line.startsWith("Meter No:")) {
                dryY += 12f
                continue
            }
            if (line.endsWith(":") && (line.contains("Recoveries") || line.contains("Kharch") || line.contains("Udhar") || line.contains("BREAKDOWN") || line.contains("Expenses") || line.contains("Credit"))) {
                dryY += 18f
                dryInDenomsBlock = line.contains("BREAKDOWN")
                continue
            }
            if (line.startsWith("Noz ") && line.contains(":")) {
                dryY += 18f
                dryInNozzleBlock = true
                continue
            }
            if (rawLine.startsWith(" ") && (line.startsWith("+") || line.startsWith("-") || line.startsWith("Coins") || (dryInDenomsBlock && line.contains("x")))) {
                dryY += 11f
                continue
            }
            if (line.startsWith("TALLY RESULT:")) {
                dryY += 22f
                continue
            }
            if (line.contains(":")) {
                val parts = line.split(":")
                val key = parts[0].trim().replace("-", "").replace("+", "").trim()
                if (key.contains("GRAND TOTAL") || key.contains("EXPECTED NET CASH") || key.contains("ACTUAL CASH")) {
                    dryY += 15f
                } else {
                    dryY += 11f
                }
                continue
            }
            dryY += 11f
        }
        val totalNeededHeight = dryY
        
        // Compute layout scale factor to fit exactly on 1 page if it exceeds the max available height
        val maxAvailableHeight = pageHeight - 60f // 782f
        val rawScale = if (totalNeededHeight > maxAvailableHeight) {
            maxAvailableHeight / totalNeededHeight
        } else {
            1.0f
        }
        val scaleFactor = rawScale.coerceAtLeast(0.70f) // Keep readable text size
        
        // Define clean professional corporate typography paints with scaling
        val titlePaint = Paint().apply {
            textSize = 13f * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF0F172A.toInt() // slate-900
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            textSize = 10f * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF334155.toInt() // slate-700
            isAntiAlias = true
        }
        val metadataLabelPaint = Paint().apply {
            textSize = 8.5f * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF475569.toInt() // slate-600
            isAntiAlias = true
        }
        val metadataValuePaint = Paint().apply {
            textSize = 8.5f * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = 0xFF1E293B.toInt() // slate-800
            isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            textSize = 9.5f * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF1E3A8A.toInt() // navy primary
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            textSize = 8.5f * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = 0xFF334155.toInt() // slate-700
            isAntiAlias = true
        }
        val bodyBoldPaint = Paint().apply {
            textSize = 8.5f * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF0F172A.toInt() // slate-900
            isAntiAlias = true
        }
        val dividerPaint = Paint().apply {
            color = 0xFFE2E8F0.toInt() // gray-200
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = 0xFFCBD5E1.toInt() // gray-300
            strokeWidth = 0.75f * scaleFactor
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val accentFillPaint = Paint().apply {
            color = 0xFFF1F5F9.toInt() // slate-100
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // Semantic color system for tally status
        val greenPaint = Paint().apply {
            textSize = 9f * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF15803D.toInt() // green-700
            isAntiAlias = true
        }
        val redPaint = Paint().apply {
            textSize = 9f * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFFB91C1C.toInt() // red-700
            isAntiAlias = true
        }
        val orangePaint = Paint().apply {
            textSize = 9f * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFFC2410C.toInt() // orange-700
            isAntiAlias = true
        }
        
        val tallySuccessBg = Paint().apply { color = 0xFFF0FDF4.toInt(); style = Paint.Style.FILL }
        val tallySuccessBorder = Paint().apply { color = 0xFF86EFAC.toInt(); strokeWidth = 1f * scaleFactor; style = Paint.Style.STROKE; isAntiAlias = true }
        
        val tallyShortageBg = Paint().apply { color = 0xFFFEF2F2.toInt(); style = Paint.Style.FILL }
        val tallyShortageBorder = Paint().apply { color = 0xFFFCA5A5.toInt(); strokeWidth = 1f * scaleFactor; style = Paint.Style.STROKE; isAntiAlias = true }
        
        val tallyExtraBg = Paint().apply { color = 0xFFFFFBEB.toInt(); style = Paint.Style.FILL }
        val tallyExtraBorder = Paint().apply { color = 0xFFFDE047.toInt(); strokeWidth = 1f * scaleFactor; style = Paint.Style.STROKE; isAntiAlias = true }
        
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        
        var currentY = 30f
        val maxY = pageHeight - 30f
        
        fun checkAndStartNewPage(spaceRequired: Float) {
            if (currentY + spaceRequired > maxY) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = 30f
                
                // Draw dynamic running headers for additional pages
                canvas.drawText("D R INAMDAR PETROLEUM • Audit Summary", margin, 20f, metadataLabelPaint)
                canvas.drawLine(margin, 25f, pageWidth - margin, 25f, dividerPaint)
                currentY = 35f
            }
        }
        
        var inNozzleBlock = false
        var inDenomsBlock = false
        
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                currentY += 4f * scaleFactor
                continue
            }
            
            // Clean up separator characters from text report representation
            if (line.matches(Regex("[-=]{3,}"))) {
                checkAndStartNewPage(6f * scaleFactor)
                canvas.drawLine(margin, currentY, pageWidth - margin, currentY, dividerPaint)
                currentY += 6f * scaleFactor
                continue
            }
            
            // 1. Petroleum Brand Header
            if (line.contains("D R INAMDAR PETROLEUM")) {
                checkAndStartNewPage(32f * scaleFactor)
                val boxHeight = 26f * scaleFactor
                canvas.drawRoundRect(margin, currentY, pageWidth - margin, currentY + boxHeight, 5f, 5f, accentFillPaint)
                canvas.drawRoundRect(margin, currentY, pageWidth - margin, currentY + boxHeight, 5f, 5f, borderPaint)
                
                val titleWidth = titlePaint.measureText("D R INAMDAR PETROLEUM")
                val titleX = (pageWidth - titleWidth) / 2f
                canvas.drawText("D R INAMDAR PETROLEUM", titleX, currentY + 17f * scaleFactor, titlePaint)
                currentY += (26f + 6f) * scaleFactor
                continue
            }
            
            // 2. Main screen heading
            if (line.contains("AUDIT REPORT") || line.contains("RECONCILE")) {
                checkAndStartNewPage(16f * scaleFactor)
                val subtitleText = line.replace("-", "").trim()
                val subWidth = subtitlePaint.measureText(subtitleText)
                val subX = (pageWidth - subWidth) / 2f
                canvas.drawText(subtitleText, subX, currentY + 10f * scaleFactor, subtitlePaint)
                currentY += 16f * scaleFactor
                continue
            }
            
            // 3. Document Metadata line (Date, Operator name)
            if (line.startsWith("Date:") || line.startsWith("Meter No:")) {
                checkAndStartNewPage(14f * scaleFactor)
                
                if (line.contains("|")) {
                    val parts = line.split("|")
                    var currentX = margin + 8f
                    val availableSpace = (pageWidth - 2 * margin - 16f) / parts.size
                    for (part in parts) {
                        val subParts = part.trim().split(":")
                        if (subParts.size >= 2) {
                            val label = subParts[0].trim() + ": "
                            val value = subParts.subList(1, subParts.size).joinToString(":").trim()
                            
                            canvas.drawText(label, currentX, currentY + 8f * scaleFactor, metadataLabelPaint)
                            val labelWidth = metadataLabelPaint.measureText(label)
                            canvas.drawText(value, currentX + labelWidth, currentY + 8f * scaleFactor, metadataValuePaint)
                        } else {
                            canvas.drawText(part.trim(), currentX, currentY + 8f * scaleFactor, metadataValuePaint)
                        }
                        currentX += availableSpace
                    }
                } else {
                    val subParts = line.split(":")
                    if (subParts.size >= 2) {
                        val label = subParts[0].trim() + ": "
                        val value = subParts.subList(1, subParts.size).joinToString(":").trim()
                        canvas.drawText(label, margin + 8f, currentY + 8f * scaleFactor, metadataLabelPaint)
                        val labelWidth = metadataLabelPaint.measureText(label)
                        canvas.drawText(value, margin + 8f + labelWidth, currentY + 8f * scaleFactor, metadataValuePaint)
                    } else {
                        canvas.drawText(line, margin + 8f, currentY + 8f * scaleFactor, metadataValuePaint)
                    }
                }
                
                currentY += 12f * scaleFactor
                continue
            }
            
            // 4. Section headers (e.g. Recoveries, Expenses, Credit, Cash breakdown)
            if (line.endsWith(":") && (line.contains("Recoveries") || line.contains("Kharch") || line.contains("Udhar") || line.contains("BREAKDOWN") || line.contains("Expenses") || line.contains("Credit"))) {
                checkAndStartNewPage(18f * scaleFactor)
                val headerText = line.replace(":", "").trim()
                canvas.drawText(headerText, margin, currentY + 8f * scaleFactor, sectionPaint)
                canvas.drawLine(margin, currentY + 11f * scaleFactor, margin + 120f * scaleFactor, currentY + 11f * scaleFactor, sectionPaint)
                currentY += 18f * scaleFactor
                inDenomsBlock = line.contains("BREAKDOWN")
                continue
            }
            
            // 5. Nozzle block indicators
            if (line.startsWith("Noz ") && line.contains(":")) {
                checkAndStartNewPage(18f * scaleFactor)
                val boxHeight = 14f * scaleFactor
                canvas.drawRoundRect(margin, currentY, pageWidth - margin, currentY + boxHeight, 3f, 3f, accentFillPaint)
                canvas.drawRoundRect(margin, currentY, pageWidth - margin, currentY + boxHeight, 3f, 3f, borderPaint)
                
                val parts = line.split(":")
                val nozzleTitle = parts[0].trim()
                canvas.drawText(nozzleTitle, margin + 8f, currentY + 10f * scaleFactor, sectionPaint)
                
                if (parts.size >= 2) {
                    val readings = parts[1].trim()
                    val textW = bodyBoldPaint.measureText(readings)
                    canvas.drawText(readings, pageWidth - margin - 8f - textW, currentY + 10f * scaleFactor, bodyBoldPaint)
                }
                
                currentY += 18f * scaleFactor
                inNozzleBlock = true
                continue
            }
            
            // 6. Indented details and tabular lists (+ Recoveries, - Expenses, - Credit etc.)
            if (rawLine.startsWith(" ") && (line.startsWith("+") || line.startsWith("-") || line.startsWith("Coins") || (inDenomsBlock && line.contains("x")))) {
                checkAndStartNewPage(11f * scaleFactor)
                
                if (line.contains(":") || line.contains("=")) {
                    val delimiter = if (line.contains(":")) ":" else "="
                    val parts = line.split(delimiter)
                    val desc = parts[0].trim()
                    val amt = parts.subList(1, parts.size).joinToString(delimiter).trim()
                    
                    val drawX = margin + 10f
                    canvas.drawText("• $desc", drawX, currentY + 8f * scaleFactor, bodyPaint)
                    
                    val amtPaint = when {
                        amt.contains("+") || desc.contains("Recoveries") -> greenPaint
                        amt.contains("-") || desc.contains("Expense") || desc.contains("Udhar") -> redPaint
                        else -> bodyPaint
                    }
                    val textW = amtPaint.measureText(amt)
                    canvas.drawText(amt, pageWidth - margin - 10f - textW, currentY + 8f * scaleFactor, amtPaint)
                } else {
                    canvas.drawText("• $line", margin + 10f, currentY + 8f * scaleFactor, bodyPaint)
                }
                currentY += 11f * scaleFactor
                continue
            }
            
            // 7. Dynamic Tally Status Badge Rendering
            if (line.startsWith("TALLY RESULT:")) {
                checkAndStartNewPage(22f * scaleFactor)
                
                val resultText = line.replace("TALLY RESULT:", "").trim()
                val textToDraw = "TALLY STATUS: $resultText"
                
                val (bgPaint, borderP, textP) = when {
                    resultText.contains("SUCCESS") || resultText.contains("Perfect") -> Triple(tallySuccessBg, tallySuccessBorder, greenPaint)
                    resultText.contains("SHORTAGE") || resultText.contains("LOSS") -> Triple(tallyShortageBg, tallyShortageBorder, redPaint)
                    else -> Triple(tallyExtraBg, tallyExtraBorder, orangePaint)
                }
                
                val boxHeight = 16f * scaleFactor
                canvas.drawRoundRect(margin, currentY, pageWidth - margin, currentY + boxHeight, 3f, 3f, bgPaint)
                canvas.drawRoundRect(margin, currentY, pageWidth - margin, currentY + boxHeight, 3f, 3f, borderP)
                
                val textWidth = textP.measureText(textToDraw)
                val textX = (pageWidth - textWidth) / 2f
                canvas.drawText(textToDraw, textX, currentY + 11f * scaleFactor, textP)
                
                currentY += 22f * scaleFactor
                continue
            }
            
            // 8. Key-Value calculations and running totals
            if (line.contains(":")) {
                val parts = line.split(":")
                val key = parts[0].trim().replace("-", "").replace("+", "").trim()
                val value = parts.subList(1, parts.size).joinToString(":").trim()
                
                checkAndStartNewPage(14f * scaleFactor)
                
                val isImportant = key.contains("GRAND TOTAL") || key.contains("EXPECTED NET CASH") || key.contains("ACTUAL CASH") || key.contains("Total Sales")
                
                val keyPaintToUse = if (isImportant) bodyBoldPaint else bodyPaint
                val valPaintToUse = if (isImportant) {
                    when {
                        key.contains("EXPECTED") -> bodyBoldPaint
                        key.contains("ACTUAL") -> bodyBoldPaint
                        key.contains("GRAND TOTAL") -> titlePaint
                        else -> bodyBoldPaint
                    }
                } else bodyPaint
                
                if (key.contains("GRAND TOTAL") || key.contains("EXPECTED NET CASH") || key.contains("ACTUAL CASH")) {
                    val boxHeight = 12f * scaleFactor
                    // Draw highlight stripe for key accounting fields
                    canvas.drawRect(margin, currentY, pageWidth - margin, currentY + boxHeight, accentFillPaint)
                    canvas.drawText(key, margin + 6f, currentY + 9f * scaleFactor, keyPaintToUse)
                    val textW = valPaintToUse.measureText(value)
                    canvas.drawText(value, pageWidth - margin - 6f - textW, currentY + 9f * scaleFactor, valPaintToUse)
                    currentY += 15f * scaleFactor
                } else {
                    canvas.drawText(key, margin + (if (inNozzleBlock) 8f else 0f), currentY + 8f * scaleFactor, keyPaintToUse)
                    val textW = valPaintToUse.measureText(value)
                    canvas.drawText(value, pageWidth - margin - textW, currentY + 8f * scaleFactor, valPaintToUse)
                    currentY += 11f * scaleFactor
                }
                continue
            }
            
            // 9. Simple plain text line fallback
            checkAndStartNewPage(11f * scaleFactor)
            canvas.drawText(line, margin, currentY + 8f * scaleFactor, bodyPaint)
            currentY += 11f * scaleFactor
        }
        
        pdfDocument.finishPage(page)
        
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        
        return file
    }

    private fun sanitizeFilename(ca: String, date: String): String {
        val cleanCa = ca.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val cleanDate = date.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        var base = "${cleanCa}_${cleanDate}"
        if (cleanCa.isEmpty() && cleanDate.isEmpty()) {
            base = "CA_Report_${System.currentTimeMillis()}"
        } else if (cleanCa.isEmpty()) {
            base = "CA_${cleanDate}"
        } else if (cleanDate.isEmpty()) {
            base = "${cleanCa}_Report"
        }
        return base.replace(Regex("__+"), "_").trim('_')
    }
}
