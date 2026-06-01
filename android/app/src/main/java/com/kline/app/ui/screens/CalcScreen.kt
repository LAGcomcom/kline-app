package com.kline.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kline.app.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcScreen() {
    var symbols by remember { mutableStateOf<List<SymbolInfo>>(emptyList()) }
    var selectedSymbol by remember { mutableStateOf("BTC-USDT") }
    var currentPrice by remember { mutableStateOf(0.0) }
    var entryPrice by remember { mutableStateOf("") }
    var exitPrice by remember { mutableStateOf("") }
    var positionSize by remember { mutableStateOf("") }
    var leverage by remember { mutableStateOf("1") }
    var direction by remember { mutableStateOf("long") }
    var showSymbolMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 加载币种列表和当前价格
    LaunchedEffect(Unit) {
        try {
            symbols = ApiClient.api.getSymbols()
        } catch (_: Exception) { }
    }

    LaunchedEffect(selectedSymbol) {
        try {
            val t = ApiClient.api.getTicker(selectedSymbol)
            currentPrice = t.last
            if (entryPrice.isEmpty()) entryPrice = "%.6f".format(currentPrice)
        } catch (_: Exception) { }
    }

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

    val filteredSymbols = remember(symbols, searchQuery) {
        if (searchQuery.isEmpty()) symbols.take(20)
        else symbols.filter { it.symbol.contains(searchQuery, ignoreCase = true) }.take(20)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("盈亏计算器", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        // 币种选择
        Text("币种", color = Color.Gray, fontSize = 12.sp)
        Box {
            OutlinedTextField(
                value = selectedSymbol.replace("-USDT", ""),
                onValueChange = {},
                readOnly = true,
                label = { Text("选择币种") },
                modifier = Modifier.fillMaxWidth().clickable { showSymbolMenu = true }
            )
            DropdownMenu(expanded = showSymbolMenu, onDismissRequest = { showSymbolMenu = false }) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
                filteredSymbols.forEach { sym ->
                    DropdownMenuItem(
                        text = {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(sym.symbol.replace("-USDT", ""))
                                Text(formatPrice(sym.last), color = Color.Gray, fontSize = 12.sp)
                            }
                        },
                        onClick = {
                            selectedSymbol = sym.symbol
                            showSymbolMenu = false
                            searchQuery = ""
                        }
                    )
                }
            }
        }

        // 当前价格显示
        if (currentPrice > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("当前价格", color = Color.Gray)
                    Text(formatPrice(currentPrice.toString()), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 方向
        Text("方向", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = direction == "long",
                onClick = { direction = "long" },
                label = { Text("做多") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF34C759).copy(alpha = 0.2f))
            )
            FilterChip(
                selected = direction == "short",
                onClick = { direction = "short" },
                label = { Text("做空") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFF3B30).copy(alpha = 0.2f))
            )
        }

        // 输入
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

        // 快捷杠杆
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1", "3", "5", "10", "20", "50", "100").forEach { lev ->
                val sel = leverage == lev
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (sel) Color(0xFF007AFF) else Color(0xFF1C1C1E))
                        .clickable { leverage = lev }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("${lev}x", color = if (sel) Color.White else Color.Gray, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 结果
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("计算结果", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                ResultRow("盈亏", pnl, isPnl = true)
                ResultRow("收益率", roi, suffix = "%")
                if (liquidation > 0) {
                    ResultRow("强平价格", liquidation)
                }
                if (size > 0 && lev > 1) {
                    ResultRow("实际仓位", size * lev)
                }
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: Double, isPnl: Boolean = false, suffix: String = "") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        val color = when {
            isPnl && value > 0 -> Color(0xFF34C759)
            isPnl && value < 0 -> Color(0xFFFF3B30)
            else -> Color.White
        }
        Text(
            if (suffix == "%") "${"%.2f".format(value)}$suffix" else "$${"%.2f".format(value)}",
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}
