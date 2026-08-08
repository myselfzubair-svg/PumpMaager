package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.SavedAudit
import kotlin.math.roundToInt

@Composable
fun SalesTrendChart(
    audits: List<SavedAudit>,
    modifier: Modifier = Modifier
) {
    if (audits.isEmpty()) return

    // Sort by timestamp ascending for the chart
    val sortedAudits = audits.sortedBy { it.timestamp }
    val maxVal = sortedAudits.maxOfOrNull { it.actualCashCollected }?.toFloat() ?: 1f
    val minVal = 0f
    val range = if (maxVal == minVal) 1f else maxVal - minVal

    var selectedIndex by remember { mutableStateOf(-1) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(sortedAudits) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val stepX = width / (sortedAudits.size - 1).coerceAtLeast(1)
                            val index = (offset.x / stepX).roundToInt().coerceIn(0, sortedAudits.size - 1)
                            selectedIndex = index
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val padding = 20.dp.toPx()
                
                val chartWidth = width - (padding * 2)
                val chartHeight = height - (padding * 2)
                
                val stepX = if (sortedAudits.size > 1) chartWidth / (sortedAudits.size - 1) else 0f
                
                // Draw Grid Lines (Horizontal)
                val gridLines = 4
                for (i in 0..gridLines) {
                    val y = padding + chartHeight - (i * chartHeight / gridLines)
                    drawLine(
                        color = labelColor.copy(alpha = 0.1f),
                        start = Offset(padding, y),
                        end = Offset(width - padding, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Draw Line Path
                val points = sortedAudits.mapIndexed { index, audit ->
                    val x = padding + index * stepX
                    val y = padding + chartHeight - ((audit.actualCashCollected.toFloat() - minVal) / range * chartHeight)
                    Offset(x, y)
                }

                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Draw Gradient Fill
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, padding + chartHeight)
                        lineTo(points.first().x, padding + chartHeight)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                            startY = points.minOf { it.y },
                            endY = padding + chartHeight
                        )
                    )
                }

                // Draw Data Points
                points.forEachIndexed { index, point ->
                    val isSelected = index == selectedIndex
                    drawCircle(
                        color = if (isSelected) secondaryColor else primaryColor,
                        radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                        center = point
                    )
                    if (isSelected) {
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = point
                        )
                    }
                }
            }
        }

        // Selection Detail Row
        if (selectedIndex != -1 && selectedIndex < sortedAudits.size) {
            val audit = sortedAudits[selectedIndex]
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = audit.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor
                        )
                        Text(
                            text = "Cash Collection",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "₹${audit.actualCashCollected.roundToInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        } else {
            // Hint text
            Text(
                text = "Tap on chart points to see details",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
        }
    }
}
