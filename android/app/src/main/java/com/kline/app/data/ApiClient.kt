package com.kline.app.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

const val BASE_URL = "http://162.251.94.184:9999"

interface KlineApi {

    @GET("/api/v2/klines")
    suspend fun getKlines(
        @Query("inst_id") instId: String = "BTC-USDT",
        @Query("bar") bar: String = "1H",
        @Query("limit") limit: Int = 200
    ): KlineResponse

    @GET("/api/v2/symbols")
    suspend fun getSymbols(): List<SymbolInfo>

    @GET("/api/v2/ticker")
    suspend fun getTicker(
        @Query("inst_id") instId: String
    ): TickerData

    @GET("/api/multi-exchange")
    suspend fun getMultiExchange(
        @Query("symbol") symbol: String
    ): MultiExchangeResult

    @GET("/api/analysis")
    suspend fun getAnalysis(
        @Query("inst_id") instId: String = "BTC-USDT",
        @Query("bar") bar: String = "1H"
    ): AnalysisResult

    @GET("/api/alerts")
    suspend fun getAlerts(): List<PriceAlert>

    @POST("/api/alerts")
    suspend fun addAlert(@Body alert: PriceAlert): PriceAlert

    @HTTP(method = "DELETE", path = "/api/alerts", hasBody = false)
    suspend fun deleteAlert(@Query("id") id: String): List<PriceAlert>

    @GET("/api/favorites")
    suspend fun getFavorites(): List<String>

    @POST("/api/favorites")
    suspend fun saveFavorites(@Body favorites: List<String>): List<String>

    @GET("/api/fear-greed")
    suspend fun getFearGreed(): FearGreedResponse
}

data class KlineResponse(
    val candles: List<CandleData>? = null
)

data class CandleData(
    val ts: Long = 0,
    val open: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val close: Double = 0.0,
    val vol: Double = 0.0
)

data class FearGreedResponse(
    val value: Int = 50,
    val classification: String = "Neutral",
    val timestamp: Long = 0
)

object ApiClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: KlineApi = retrofit.create(KlineApi::class.java)
}
