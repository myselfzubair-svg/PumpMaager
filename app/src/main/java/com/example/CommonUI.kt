package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyPlaceholder(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "No data",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)),
            textAlign = TextAlign.Center
        )
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun isSameProduct(name1: String, name2: String): Boolean {
    val n1 = name1.trim().lowercase()
    val n2 = name2.trim().lowercase()
    if (n1 == n2) return true
    
    val msPetrolAliases = listOf("ms", "petrol", "ms petrol", "motor spirit", "ms petrol (petrol)")
    val hsdDieselAliases = listOf("hsd", "diesel", "hsd diesel", "high speed diesel", "hsd diesel (diesel)")
    val premiumAliases = listOf("premium", "premium petrol", "speed", "power", "premium petrol (petrol)")
    
    if (msPetrolAliases.contains(n1) && msPetrolAliases.contains(n2)) return true
    if (hsdDieselAliases.contains(n1) && hsdDieselAliases.contains(n2)) return true
    if (premiumAliases.contains(n1) && premiumAliases.contains(n2)) return true
    
    return false
}

fun generateProductId(productName: String): String {
    return "PROD_${productName.trim().uppercase().replace(" ", "_")}"
}
