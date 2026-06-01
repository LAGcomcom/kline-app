package com.kline.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kline.app.data.*

@Composable
fun RankScreen() {
    var symbols by remember { mutableStateOf<List<SymbolInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortAsc by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            symbols = ApiClient.api.getSymbols()
        } catch (_: Exception) { }
        isLoading = false
    }

    val sorted = remember(symbols, sortAsc) {
        symbols.sortedBy {
            it.change24h.replace("%", "").toDoubleOrNull() ?: 0.0
        }.let { if (sortAsc) it else it.reversed() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("涨跌幅排行", style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = { sortAsc = false }) {
                    Text("涨幅", color = if (!sortAsc) MaterialTheme.colorScheme.primary else Color.Gray)
                }
                TextButton(onClick = { sortAsc = true }) {
                    Text("跌幅", color = if (sortAsc) MaterialTheme.colorScheme.primary else Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("币种", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("价格", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("24H涨跌", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(sorted) { sym ->
                    RankItem(sym)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
fun RankItem(sym: SymbolInfo) {
    val change = sym.change24h.replace("%", "").toDoubleOrNull() ?: 0.0
    val changeColor = if (change >= 0) Color(0xFF34C759) else Color(0xFFFF3B30)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            sym.symbol.replace("-USDT", "").replace("_USDT", ""),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatPrice(sym.last),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(changeColor.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${if (change >= 0) "+" else ""}${"%.2f".format(change)}%",
                color = changeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun formatPrice(p: String): String {
    val d = p.toDoubleOrNull() ?: return p
    return when {
        d >= 1000 -> "%,.2f".format(d)
        d >= 1 -> "%.2f".format(d)
        else -> "%.6f".format(d)
    }
}
