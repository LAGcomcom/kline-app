package com.kline.app.data

data class Kline(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class SymbolInfo(
    val symbol: String,
    val name: String = "",
    val last: String = "",
    val change24h: String = "",
    val high24h: String = "",
    val low24h: String = "",
    val vol24h: String = ""
)

data class PriceAlert(
    val id: String = "",
    val symbol: String,
    val target: Double,
    val dir: String, // "above" or "below"
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

data class TickerData(
    val symbol: String = "",
    val last: String = "",
    val change24h: String = "",
    val high24h: String = "",
    val low24h: String = "",
    val vol24h: String = ""
)

data class ExchangePrice(
    val exchange: String = "",
    val price: String = ""
)

data class MultiExchangeResult(
    val symbol: String = "",
    val prices: List<ExchangePrice>? = null,
    val avg_price: String = "",
    val spread_pct: String = ""
)
