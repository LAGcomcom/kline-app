package com.kline.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalcScreen() {
    var entryPrice by remember { mutableStateOf("") }
    var exitPrice by remember { mutableStateOf("") }
    var positionSize by remember { mutableStateOf("") }
    var leverage by remember { mutableStateOf("1") }
    var direction by remember { mutableStateOf("long") } // long or short

    val entry = entryPrice.toDoubleOrNull() ?: 0.0
    val exit = exitPrice.toDoubleOrNull() ?: 0.0
    val size = positionSize.toDoubleOrNull() ?: 0.0
    val lev = leverage.toDoubleOrNull() ?: 1.0

    val pnl = if (entry > 0 && exit > 0 && size > 0) {
        val priceChange = if (direction == "long") exit - entry else entry - exit
        (priceChange / entry) * size * lev
    } else 0.0

    val roi = if (size > 0) (pnl / size) * 100 else 0.0

    val liquidation = if (entry > 0 && lev > 0) {
        if (direction == "long") entry * (1 - 1.0 / lev) else entry * (1 + 1.0 / lev)
    } else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("盈亏计算器", style = MaterialTheme.typography.titleMedium)

        // Direction toggle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = direction == "long",
                onClick = { direction = "long" },
                label = { Text("做多") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF34C759).copy(alpha = 0.2f)
                )
            )
            FilterChip(
                selected = direction == "short",
                onClick = { direction = "short" },
                label = { Text("做空") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFF3B30).copy(alpha = 0.2f)
                )
            )
        }

        OutlinedTextField(
            value = entryPrice,
            onValueChange = { entryPrice = it },
            label = { Text("开仓价格") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = exitPrice,
            onValueChange = { exitPrice = it },
            label = { Text("平仓价格") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = positionSize,
            onValueChange = { positionSize = it },
            label = { Text("仓位大小 (USDT)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = leverage,
            onValueChange = { leverage = it },
            label = { Text("杠杆倍数") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Results
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ResultRow("盈亏", pnl, isPnl = true)
                ResultRow("收益率", roi, suffix = "%")
                if (liquidation > 0) {
                    ResultRow("强平价格", liquidation)
                }
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: Double, isPnl: Boolean = false, suffix: String = "") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        val color = when {
            isPnl && value > 0 -> Color(0xFF34C759)
            isPnl && value < 0 -> Color(0xFFFF3B30)
            else -> MaterialTheme.colorScheme.onSurface
        }
        Text(
            if (suffix == "%") "${"%.2f".format(value)}$suffix" else "$${"%.2f".format(value)}",
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}
