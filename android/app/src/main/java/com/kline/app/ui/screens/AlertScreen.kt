package com.kline.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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

@Composable
fun AlertScreen() {
    var alerts by remember { mutableStateOf<List<PriceAlert>>(emptyList()) }
    var symbols by remember { mutableStateOf<List<SymbolInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            try { alerts = ApiClient.api.getAlerts() } catch (_: Exception) { }
            try { symbols = ApiClient.api.getSymbols() } catch (_: Exception) { }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("价格预警", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, "添加", tint = Color(0xFF007AFF))
            }
        }
        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (alerts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无预警", color = Color.Gray, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("点击右上角 + 添加价格预警", color = Color(0xFF8E8E93), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(alerts) { alert ->
                    AlertItem(alert, symbols) {
                        scope.launch {
                            try { alerts = ApiClient.api.deleteAlert(alert.id) } catch (_: Exception) { }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddAlertDialog(
            symbols = symbols.map { it.symbol },
            onDismiss = { showDialog = false },
            onAdd = { symbol, target, dir ->
                scope.launch {
                    try {
                        ApiClient.api.addAlert(PriceAlert(symbol = symbol, target = target, dir = dir))
                        reload()
                    } catch (_: Exception) { }
                }
                showDialog = false
            }
        )
    }
}

@Composable
fun AlertItem(alert: PriceAlert, symbols: List<SymbolInfo>, onDelete: () -> Unit) {
    val sym = symbols.find { it.symbol == alert.symbol }
    val currentPrice = sym?.last?.toDoubleOrNull()
    val isTriggered = currentPrice != null && (
        (alert.dir == "above" && currentPrice >= alert.target) ||
        (alert.dir == "below" && currentPrice <= alert.target)
    )

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(alert.symbol.replace("-USDT", ""), fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp)
                    if (isTriggered) {
                        Spacer(Modifier.width(8.dp))
                        Text("已触发", color = Color(0xFFFFCC00), fontSize = 11.sp,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFCC00).copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${if (alert.dir == "above") "高于" else "低于"} ${"%.4f".format(alert.target)}",
                    color = if (alert.dir == "above") Color(0xFF34C759) else Color(0xFFFF3B30),
                    fontSize = 14.sp
                )
                currentPrice?.let {
                    Text("当前: ${formatPrice(it.toString())}", color = Color.Gray, fontSize = 12.sp)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = Color(0xFFFF3B30))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlertDialog(symbols: List<String>, onDismiss: () -> Unit, onAdd: (String, Double, String) -> Unit) {
    var selectedSymbol by remember { mutableStateOf(symbols.firstOrNull() ?: "BTC-USDT") }
    var target by remember { mutableStateOf("") }
    var dir by remember { mutableStateOf("above") }
    var showSymbolMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredSymbols = remember(symbols, searchQuery) {
        if (searchQuery.isEmpty()) symbols.take(20)
        else symbols.filter { it.contains(searchQuery, ignoreCase = true) }.take(20)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("添加预警", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                text = { Text(sym.replace("-USDT", "")) },
                                onClick = { selectedSymbol = sym; showSymbolMenu = false; searchQuery = "" }
                            )
                        }
                    }
                }

                // 目标价格
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("目标价格") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 方向
                Text("触发方向", color = Color.Gray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = dir == "above", onClick = { dir = "above" },
                        label = { Text("高于") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF34C759).copy(alpha = 0.2f)))
                    FilterChip(selected = dir == "below", onClick = { dir = "below" },
                        label = { Text("低于") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFF3B30).copy(alpha = 0.2f)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = target.toDoubleOrNull() ?: return@TextButton
                onAdd(selectedSymbol, t, dir)
            }) { Text("添加", color = Color(0xFF007AFF)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color.Gray) }
        }
    )
}
