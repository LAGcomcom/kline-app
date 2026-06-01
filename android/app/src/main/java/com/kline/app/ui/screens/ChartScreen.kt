package com.kline.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kline.app.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen() {
    var selectedSymbol by remember { mutableStateOf("BTC-USDT") }
    var selectedInterval by remember { mutableStateOf("1H") }
    var analysis by remember { mutableStateOf<AnalysisResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val symbols = listOf("BTC-USDT", "ETH-USDT", "SOL-USDT", "BNB-USDT", "XRP-USDT", "DOGE-USDT")
    val intervals = listOf("15m", "1H", "4H", "1D")

    LaunchedEffect(selectedSymbol, selectedInterval) {
        isLoading = true
        try {
            analysis = ApiClient.api.getAnalysis(selectedSymbol, selectedInterval)
        } catch (_: Exception) { }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Symbol selector
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(symbols) { sym ->
                val isSelected = sym == selectedSymbol
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { selectedSymbol = sym }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        sym.replace("-USDT", ""),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Interval selector
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(intervals) { iv ->
                val isSelected = iv == selectedInterval
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFF3A3A3C) else Color.Transparent)
                        .clickable { selectedInterval = iv }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(iv, color = if (isSelected) Color.White else Color.Gray, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // WebView chart
        val chartUrl = "$BASE_URL/?symbol=${selectedSymbol}&interval=${selectedInterval}"
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webViewClient = WebViewClient()
                    loadUrl(chartUrl)
                }
            },
            update = { webView ->
                webView.loadUrl(chartUrl)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Analysis panel
        analysis?.let { a ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    a.bollinger?.let { b ->
                        Text("Bollinger Band", color = Color.Gray, fontSize = 12.sp)
                        Text(
                            "Upper: ${"%.2f".format(b.upper)}  Mid: ${"%.2f".format(b.middle)}  Lower: ${"%.2f".format(b.lower)}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                    a.trend_strength?.let { t ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "趋势: ${t.trend}  ADX: ${"%.1f".format(t.adx)}  动量: ${"%.2f".format(t.momentum)}%",
                            color = if (t.momentum > 0) Color(0xFF34C759) else Color(0xFFFF3B30),
                            fontSize = 13.sp
                        )
                    }
                    a.enhanced_signal?.let { sig ->
                        Spacer(modifier = Modifier.height(6.dp))
                        val sigColor = when (sig.direction) {
                            "buy" -> Color(0xFF34C759)
                            "sell" -> Color(0xFFFF3B30)
                            else -> Color.Gray
                        }
                        Text(
                            "信号: ${sig.direction.uppercase()}  置信度: ${"%.0f".format(sig.confidence * 100)}%",
                            color = sigColor,
                            fontSize = 13.sp
                        )
                    }
                    a.divergence?.forEach { d ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(d.desc, color = Color(0xFFFFCC00), fontSize = 12.sp)
                    }
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
