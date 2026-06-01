package com.kline.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var errorMsg by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("change") } // change, vol, price

    LaunchedEffect(Unit) {
        try {
            symbols = ApiClient.api.getSymbols()
        } catch (e: Exception) {
            errorMsg = e.message ?: "加载失败"
        }
        isLoading = false
    }

    val sorted = remember(symbols, sortMode) {
        when (sortMode) {
            "vol" -> symbols.sortedByDescending { it.vol }
            "price" -> symbols.sortedByDescending { it.last.toDoubleOrNull() ?: 0.0 }
            else -> symbols.sortedByDescending { it.chg }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(12.dp)
    ) {
        Text("涨跌幅排行", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // 排序选项
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortChip("涨跌幅", sortMode == "change") { sortMode = "change" }
            SortChip("成交量", sortMode == "vol") { sortMode = "vol" }
            SortChip("价格", sortMode == "price") { sortMode = "price" }
        }

        Spacer(Modifier.height(8.dp))

        // 表头
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
            Text("币种", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("价格", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("24H涨跌", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("成交量", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = Color(0xFF2C2C2E))

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            errorMsg.isNotEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg, color = Color.Gray)
            }
            else -> LazyColumn {
                items(sorted) { sym -> RankItem(sym) }
            }
        }
    }
}

@Composable
fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF007AFF) else Color(0xFF1C1C1E))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) Color.White else Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun RankItem(sym: SymbolInfo) {
    val changeColor = if (sym.chg >= 0) Color(0xFF34C759) else Color(0xFFFF3B30)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            sym.symbol.replace("-USDT", "").replace("_USDT", ""),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatRankPrice(sym.last),
            color = Color(0xFFE5E5E5),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp))
                .background(changeColor.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${if (sym.chg >= 0) "+" else ""}${"%.2f".format(sym.chg)}%",
                color = changeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            formatVolNum(sym.vol),
            color = Color(0xFF8E8E93),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
    HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)
}

fun formatRankPrice(p: String): String {
    val d = p.toDoubleOrNull() ?: return p
    return when {
        d >= 1000 -> "%,.2f".format(d)
        d >= 1 -> "%.4f".format(d)
        else -> "%.6f".format(d)
    }
}

fun formatVolNum(d: Double): String {
    return when {
        d >= 1e9 -> "%.1fB".format(d / 1e9)
        d >= 1e6 -> "%.1fM".format(d / 1e6)
        d >= 1e3 -> "%.1fK".format(d / 1e3)
        else -> "%.0f".format(d)
    }
}
