package com.kline.app.data

import com.google.gson.annotations.SerializedName

data class Kline(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class SymbolInfo(
    @SerializedName("id") val symbol: String = "",
    val base: String = "",
    val last: String = "",
    val vol: Double = 0.0,
    val chg: Double = 0.0
)

data class PriceAlert(
    val id: String = "",
    val symbol: String,
    val target: Double,
    val dir: String,
    val active: Boolean = true
)

data class AnalysisResult(
    val bollinger: BollingerBand? = null,
    val support_resist: List<SRLevel>? = null,
    val trend_strength: TrendScore? = null,
    val enhanced_signal: EnhancedSignal? = null,
    val divergence: List<DivergenceInfo>? = null
)

data class BollingerBand(
    val upper: Double = 0.0,
    val middle: Double = 0.0,
    val lower: Double = 0.0,
    val width: Double = 0.0,
    val pb: Double = 0.0
)

data class SRLevel(
    val price: Double = 0.0,
    val type: String = "",
    val strength: Int = 0
)

data class TrendScore(
    val adx: Double = 0.0,
    val trend: String = "",
    val momentum: Double = 0.0,
    val volume_trend: Double = 0.0
)

data class EnhancedSignal(
    val direction: String = "",
    val confidence: Double = 0.0,
    val score: Double = 0.0,
    val reasons: List<String>? = null
)

data class DivergenceInfo(
    val type: String = "",
    val indicator: String = "",
    val desc: String = ""
)

// Ticker API: /api/v2/ticker?inst_id=BTC-USDT
// {"last":71602.4,"bid":71602.3,"ask":71602.4,"high24h":74210.9,"low24h":71384.4,"vol24h":5790.2695489,"change24h":-2.74}
data class TickerData(
    val last: Double = 0.0,
    val bid: Double = 0.0,
    val ask: Double = 0.0,
    val high24h: Double = 0.0,
    val low24h: Double = 0.0,
    val vol24h: Double = 0.0,
    val change24h: Double = 0.0
)

// Multi-exchange API: /api/multi-exchange?symbol=BTC
// {"exchanges":[{"exchange":"okx","last":71622.1,"change24h":-2.71},...]}
data class ExchangePrice(
    val exchange: String = "",
    val last: Double = 0.0,
    val change24h: Double = 0.0
)

data class MultiExchangeResult(
    val exchanges: List<ExchangePrice>? = null
)
