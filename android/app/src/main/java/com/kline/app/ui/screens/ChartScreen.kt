package com.kline.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kline.app.data.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Candle(val time: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)

@Composable
fun ChartScreen() {
    var selectedSymbol by remember { mutableStateOf("BTC-USDT") }
    var selectedInterval by remember { mutableStateOf("1H") }
    var candles by remember { mutableStateOf<List<Candle>>(emptyList()) }
    var analysis by remember { mutableStateOf<AnalysisResult?>(null) }
    var ticker by remember { mutableStateOf<TickerData?>(null) }
    var multiEx by remember { mutableStateOf<MultiExchangeResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val symbols = listOf("BTC-USDT", "ETH-USDT", "SOL-USDT", "BNB-USDT", "XRP-USDT", "DOGE-USDT", "TON-USDT", "ADA-USDT")
    val intervals = listOf("15m", "1H", "4H", "1D")

    fun loadData() {
        scope.launch {
            isLoading = true
            // K线数据
            try {
                val resp = ApiClient.api.getKlines(selectedSymbol, selectedInterval, 200)
                candles = resp.candles?.map {
                    Candle(it.ts / 1000, it.open, it.high, it.low, it.close, it.vol)
                } ?: emptyList()
            } catch (_: Exception) { }
            // 分析数据
            try { analysis = ApiClient.api.getAnalysis(selectedSymbol, selectedInterval) } catch (_: Exception) { }
            // Ticker
            try { ticker = ApiClient.api.getTicker(selectedSymbol) } catch (_: Exception) { }
            // 多交易所价格
            try {
                val base = selectedSymbol.replace("-USDT", "").replace("_USDT", "")
                multiEx = ApiClient.api.getMultiExchange(base)
            } catch (_: Exception) { }
            isLoading = false
        }
    }

    LaunchedEffect(selectedSymbol, selectedInterval) { loadData() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))
    ) {
        // 币种选择
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(symbols) { sym ->
                    val sel = sym == selectedSymbol
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (sel) Color(0xFF007AFF) else Color(0xFF1C1C1E))
                            .clickable { selectedSymbol = sym }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(sym.replace("-USDT", ""), color = if (sel) Color.White else Color(0xFF8E8E93), fontSize = 13.sp)
                    }
                }
            }
        }

        // 周期选择
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(intervals) { iv ->
                    val sel = iv == selectedInterval
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (sel) Color(0xFF3A3A3C) else Color.Transparent)
                            .clickable { selectedInterval = iv }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(iv, color = if (sel) Color.White else Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 价格信息卡片
        item {
            ticker?.let { t ->
                PriceTickerCard(t)
            }
        }

        // K线图
        item {
            if (candles.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
                ) {
                    CandlestickChart(
                        candles = candles,
                        modifier = Modifier.fillMaxWidth().height(300.dp).padding(8.dp)
                    )
                }
            } else if (isLoading) {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // 多交易所价格
        item {
            multiEx?.let { m ->
                MultiExchangeCard(m)
            }
        }

        // 分析面板
        item {
            analysis?.let { a ->
                AnalysisCard(a)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun PriceTickerCard(t: TickerData) {
    val changeColor = if (t.change24h >= 0) Color(0xFF34C759) else Color(0xFFFF3B30)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatPrice(t.last.toString()),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${if (t.change24h >= 0) "+" else ""}${"%.2f".format(t.change24h)}%",
                    color = changeColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TickerItem("24H高", "%.2f".format(t.high24h))
                TickerItem("24H低", "%.2f".format(t.low24h))
                TickerItem("24H量", formatVol("%.0f".format(t.vol24h)))
            }
        }
    }
}

@Composable
fun TickerItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(formatPrice(value), color = Color(0xFFE5E5E5), fontSize = 13.sp)
    }
}

fun formatVol(v: String): String {
    val d = v.toDoubleOrNull() ?: return v
    return when {
        d >= 1e9 -> "%.2fB".format(d / 1e9)
        d >= 1e6 -> "%.2fM".format(d / 1e6)
        d >= 1e3 -> "%.2fK".format(d / 1e3)
        else -> "%.0f".format(d)
    }
}

@Composable
fun MultiExchangeCard(m: MultiExchangeResult) {
    val prices = m.exchanges ?: return
    if (prices.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("多交易所价格", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            prices.forEach { ep ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ep.exchange.uppercase(), color = Color(0xFF8E8E93), fontSize = 13.sp)
                    Text(formatPrice(ep.last.toString()), color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            val avg = prices.map { it.last }.average()
            val spread = if (avg > 0) (prices.maxOf { it.last } - prices.minOf { it.last }) / avg * 100 else 0.0
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("均价", color = Color.Gray, fontSize = 13.sp)
                Text(formatPrice(avg.toString()), color = Color(0xFF007AFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("价差", color = Color.Gray, fontSize = 13.sp)
                Text("${"%.3f".format(spread)}%", color = Color(0xFFFFCC00), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AnalysisCard(a: AnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("技术分析", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            // 布林带
            a.bollinger?.let { b ->
                SectionTitle("Bollinger Band")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    InfoItem("上轨", "%.2f".format(b.upper), Color(0xFFFF3B30))
                    InfoItem("中轨", "%.2f".format(b.middle), Color.White)
                    InfoItem("下轨", "%.2f".format(b.lower), Color(0xFF34C759))
                    InfoItem("%B", "%.2f".format(b.pb), Color(0xFF007AFF))
                }
                Spacer(Modifier.height(8.dp))
            }

            // 趋势强度
            a.trend_strength?.let { t ->
                SectionTitle("趋势强度")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val trendLabel = when (t.trend) {
                        "strong_up" -> "强势上涨"
                        "up" -> "上涨"
                        "down" -> "下跌"
                        "strong_down" -> "强势下跌"
                        else -> "震荡"
                    }
                    val trendColor = when {
                        t.trend.contains("up") -> Color(0xFF34C759)
                        t.trend.contains("down") -> Color(0xFFFF3B30)
                        else -> Color.Gray
                    }
                    InfoItem("趋势", trendLabel, trendColor)
                    InfoItem("ADX", "%.1f".format(t.adx), Color.White)
                    InfoItem("动量", "%.2f%%".format(t.momentum), if (t.momentum > 0) Color(0xFF34C759) else Color(0xFFFF3B30))
                    InfoItem("量能", "%.1f%%".format(t.volume_trend), if (t.volume_trend > 0) Color(0xFF34C759) else Color(0xFFFF3B30))
                }
                Spacer(Modifier.height(8.dp))
            }

            // 信号
            a.enhanced_signal?.let { sig ->
                SectionTitle("交易信号")
                val sigColor = when (sig.direction) {
                    "buy" -> Color(0xFF34C759)
                    "sell" -> Color(0xFFFF3B30)
                    else -> Color.Gray
                }
                val sigLabel = when (sig.direction) {
                    "buy" -> "买入"
                    "sell" -> "卖出"
                    else -> "观望"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sigLabel, color = sigColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text("置信度 ${"%.0f".format(sig.confidence * 100)}%", color = Color.Gray, fontSize = 13.sp)
                }
                sig.reasons?.forEach { reason ->
                    Text("· $reason", color = Color(0xFF8E8E93), fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            // 支撑阻力
            a.support_resist?.let { levels ->
                if (levels.isNotEmpty()) {
                    SectionTitle("支撑阻力位")
                    levels.take(6).forEach { sr ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (sr.type == "support") "支撑" else "阻力", color = if (sr.type == "support") Color(0xFF34C759) else Color(0xFFFF3B30), fontSize = 12.sp)
                            Text("%.2f".format(sr.price), color = Color.White, fontSize = 13.sp)
                            Text("强度 ${sr.strength}", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // 背离
            a.divergence?.forEach { d ->
                SectionTitle("背离信号")
                Text(d.desc, color = Color(0xFFFFCC00), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, color = Color(0xFF8E8E93), fontSize = 11.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun RowScope.InfoItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ============ 原生 K 线图 ============

@Composable
fun CandlestickChart(candles: List<Candle>, modifier: Modifier = Modifier) {
    val green = Color(0xFF34C759)
    val red = Color(0xFFFF3B30)
    val ma5Color = Color(0xFFFFCC00)
    val ma10Color = Color(0xFF007AFF)
    val ma20Color = Color(0xFFFF6B9D)

    Canvas(modifier = modifier) {
        if (candles.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val volH = h * 0.2f
        val chartH = h - volH - 20f
        val rightPad = 60f
        val chartW = w - rightPad

        val visibleCandles = candles.takeLast(60)
        val n = visibleCandles.size
        if (n == 0) return@Canvas

        val candleW = chartW / n
        val bodyW = candleW * 0.6f

        val allHigh = visibleCandles.maxOf { it.high }
        val allLow = visibleCandles.minOf { it.low }
        val range = if (allHigh - allLow > 0) allHigh - allLow else 1.0
        val maxVol = visibleCandles.maxOf { it.volume }.let { if (it > 0) it else 1.0 }

        fun priceY(p: Double): Float {
            return (chartH * (1 - (p - allLow) / range)).toFloat()
        }

        // 价格刻度线
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = chartH * i / gridLines
            drawLine(Color(0xFF2C2C2E), Offset(0f, y), Offset(chartW, y), strokeWidth = 0.5f)
            val price = allHigh - range * i / gridLines
            drawContext.canvas.nativeCanvas.drawText(
                "%.2f".format(price),
                chartW + 4f, y + 4f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 22f
                }
            )
        }

        // MA 线
        fun drawMA(period: Int, color: Color) {
            if (n < period) return
            val path = Path()
            var started = false
            for (i in period - 1 until n) {
                var sum = 0.0
                for (j in i - period + 1..i) sum += visibleCandles[j].close
                val ma = sum / period
                val x = i * candleW + candleW / 2
                val y = priceY(ma)
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 1.5f))
        }
        drawMA(5, ma5Color)
        drawMA(10, ma10Color)
        drawMA(20, ma20Color)

        // 蜡烛图
        for (i in visibleCandles.indices) {
            val c = visibleCandles[i]
            val x = i * candleW + candleW / 2
            val isUp = c.close >= c.open
            val color = if (isUp) green else red

            // 影线
            val highY = priceY(c.high)
            val lowY = priceY(c.low)
            drawLine(color, Offset(x, highY), Offset(x, lowY), strokeWidth = 1f)

            // 实体
            val openY = priceY(c.open)
            val closeY = priceY(c.close)
            val top = min(openY, closeY)
            val bottom = max(openY, closeY)
            val bodyH = max(bottom - top, 1f)
            drawRect(color, Offset(x - bodyW / 2, top), androidx.compose.ui.geometry.Size(bodyW, bodyH))

            // 成交量
            val volY = h - (c.volume / maxVol * volH).toFloat()
            val volColor = if (isUp) green.copy(alpha = 0.5f) else red.copy(alpha = 0.5f)
            drawRect(volColor, Offset(x - bodyW / 2, volY), androidx.compose.ui.geometry.Size(bodyW, h - volY))
        }
    }
}

fun formatPrice(p: String): String {
    val d = p.toDoubleOrNull() ?: return p
    return when {
        d >= 10000 -> "%,.2f".format(d)
        d >= 100 -> "%.2f".format(d)
        d >= 1 -> "%.4f".format(d)
        d >= 0.001 -> "%.6f".format(d)
        else -> "%.8f".format(d)
    }
}
