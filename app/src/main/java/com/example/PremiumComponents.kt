package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PremiumStatusCard(
    title: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    iconColor: Color,
    statusText: String? = null,
    statusColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(115.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Surface(color = iconColor.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp)) }
                }
                if (statusText != null) {
                    Surface(color = statusColor.copy(alpha = 0.08f), shape = CircleShape) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                            Text(statusText, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Column {
                Text(title, color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(value, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, color = Color(0xFF1E293B))
                if (subValue.isNotEmpty()) Text(subValue, color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun DashboardQuickAction(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable { onClick() }.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, Color(0xFFF1F5F9))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
            }
        }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF334155), textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 12.sp)
    }
}

@Composable
fun SummaryMetricCard(
    label: String,
    value: String,
    trendText: String,
    trendColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                Surface(color = Color(0xFFF8FAFC), shape = CircleShape, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp)) }
                }
            }
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E293B))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = trendColor, modifier = Modifier.size(12.dp))
                Text(trendText, color = trendColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun ActivityTimelineItem(title: String, desc: String, time: String, icon: ImageVector, iconColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = iconColor.copy(alpha = 0.12f), shape = CircleShape, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp)) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
            Text(desc, fontSize = 11.sp, color = Color(0xFF64748B))
        }
        Text(time, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF94A3B8))
    }
}
