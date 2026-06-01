package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"sync"
	"time"
)

const remoteAPI = "http://162.251.94.184:9800"

type Kline struct {
	Time   int64   `json:"time"`
	Open   float64 `json:"open"`
	High   float64 `json:"high"`
	Low    float64 `json:"low"`
	Close  float64 `json:"close"`
	Volume float64 `json:"volume"`
}

// 简易内存缓存
type cacheEntry struct {
	data      []byte
	expiresAt time.Time
}

var (
	cache   = make(map[string]cacheEntry)
	cacheMu sync.RWMutex
)

func cacheGet(key string) ([]byte, bool) {
	cacheMu.RLock()
	defer cacheMu.RUnlock()
	if e, ok := cache[key]; ok && time.Now().Before(e.expiresAt) {
		return e.data, true
	}
	return nil, false
}

func cacheSet(key string, data []byte, ttl time.Duration) {
	cacheMu.Lock()
	defer cacheMu.Unlock()
	cache[key] = cacheEntry{data: data, expiresAt: time.Now().Add(ttl)}
}

func corsMiddleware(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}
		next(w, r)
	}
}

func proxyWithCache(path string, ttl time.Duration) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		cacheKey := path + "?" + r.URL.RawQuery

		if cached, ok := cacheGet(cacheKey); ok {
			w.Header().Set("Content-Type", "application/json")
			w.Header().Set("X-Cache", "HIT")
			w.Write(cached)
			return
		}

		remoteURL := remoteAPI + path
		if r.URL.RawQuery != "" {
			remoteURL += "?" + r.URL.RawQuery
		}

		client := &http.Client{Timeout: 12 * time.Second}
		resp, err := client.Get(remoteURL)
		if err != nil {
			http.Error(w, fmt.Sprintf("API不可用: %v", err), http.StatusBadGateway)
			return
		}
		defer resp.Body.Close()

		body, err := io.ReadAll(resp.Body)
		if err != nil {
			http.Error(w, "读取响应失败", http.StatusInternalServerError)
			return
		}

		cacheSet(cacheKey, body, ttl)

		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("X-Cache", "MISS")
		w.WriteHeader(resp.StatusCode)
		w.Write(body)
	}
}

func main() {
	// 远程 API 代理（30秒缓存）
	http.HandleFunc("/api/klines", corsMiddleware(proxyWithCache("/api/klines", 30*time.Second)))
	http.HandleFunc("/api/v2/klines", corsMiddleware(proxyWithCache("/api/v2/klines", 30*time.Second)))
	http.HandleFunc("/api/symbols", corsMiddleware(proxyWithCache("/api/symbols", 30*time.Second)))
	http.HandleFunc("/api/v2/symbols", corsMiddleware(proxyWithCache("/api/v2/symbols", 30*time.Second)))
	http.HandleFunc("/api/multi-exchange", corsMiddleware(proxyWithCache("/api/multi-exchange", 30*time.Second)))
	http.HandleFunc("/api/ticker", corsMiddleware(proxyWithCache("/api/ticker", 30*time.Second)))
	http.HandleFunc("/api/v2/ticker", corsMiddleware(proxyWithCache("/api/v2/ticker", 30*time.Second)))
	http.HandleFunc("/api/fear-greed", corsMiddleware(proxyWithCache("/api/fear-greed", 30*time.Second)))
	http.HandleFunc("/api/v2/fear-greed", corsMiddleware(proxyWithCache("/api/v2/fear-greed", 30*time.Second)))

	// 自选收藏 API（持久化到文件）
	http.HandleFunc("/api/favorites", corsMiddleware(handleFavorites))

	// 价格预警 API
	http.HandleFunc("/api/alerts", corsMiddleware(handleAlerts))

	// 本地增强分析 API（30秒缓存）
	http.HandleFunc("/api/analysis", corsMiddleware(handleAnalysisCached))

	// 本地交易所备用
	http.HandleFunc("/api/local/klines", corsMiddleware(handleLocalKlines))
	http.HandleFunc("/api/local/exchanges", corsMiddleware(handleLocalExchanges))

	// 静态文件（禁用缓存以便开发）
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
		w.Header().Set("Pragma", "no-cache")
		http.FileServer(http.Dir("static")).ServeHTTP(w, r)
	})

	addr := ":9999"
	fmt.Printf("K线分析平台启动: http://localhost%s\n", addr)
	log.Fatal(http.ListenAndServe(addr, nil))
}

const favFile = "favorites.json"

func loadFavsFromFile() []string {
	data, err := os.ReadFile(favFile)
	if err != nil {
		return []string{}
	}
	var favs []string
	json.Unmarshal(data, &favs)
	if favs == nil {
		return []string{}
	}
	return favs
}

func saveFavsToFile(favs []string) {
	data, _ := json.Marshal(favs)
	os.WriteFile(favFile, data, 0644)
}

func handleFavorites(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	switch r.Method {
	case "GET":
		json.NewEncoder(w).Encode(loadFavsFromFile())
	case "POST":
		var favs []string
		if err := json.NewDecoder(r.Body).Decode(&favs); err != nil {
			http.Error(w, "invalid json", http.StatusBadRequest)
			return
		}
		saveFavsToFile(favs)
		json.NewEncoder(w).Encode(favs)
	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

// 价格预警
type PriceAlert struct {
	ID     string  `json:"id"`
	Symbol string  `json:"symbol"`
	Target float64 `json:"target"`
	Dir    string  `json:"dir"` // "above" or "below"
	Active bool    `json:"active"`
}

const alertFile = "alerts.json"

func loadAlerts() []PriceAlert {
	data, err := os.ReadFile(alertFile)
	if err != nil {
		return []PriceAlert{}
	}
	var alerts []PriceAlert
	json.Unmarshal(data, &alerts)
	if alerts == nil {
		return []PriceAlert{}
	}
	return alerts
}

func saveAlerts(alerts []PriceAlert) {
	data, _ := json.Marshal(alerts)
	os.WriteFile(alertFile, data, 0644)
}

func handleAlerts(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	switch r.Method {
	case "GET":
		json.NewEncoder(w).Encode(loadAlerts())
	case "POST":
		var alert PriceAlert
		if err := json.NewDecoder(r.Body).Decode(&alert); err != nil {
			http.Error(w, "invalid json", http.StatusBadRequest)
			return
		}
		alerts := loadAlerts()
		alert.ID = fmt.Sprintf("%d", time.Now().UnixNano())
		alert.Active = true
		alerts = append(alerts, alert)
		saveAlerts(alerts)
		json.NewEncoder(w).Encode(alert)
	case "DELETE":
		id := r.URL.Query().Get("id")
		alerts := loadAlerts()
		filtered := make([]PriceAlert, 0, len(alerts))
		for _, a := range alerts {
			if a.ID != id {
				filtered = append(filtered, a)
			}
		}
		saveAlerts(filtered)
		json.NewEncoder(w).Encode(filtered)
	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

func handleLocalExchanges(w http.ResponseWriter, r *http.Request) {
	type ExchangeInfo struct {
		ID        string   `json:"id"`
		Name      string   `json:"name"`
		Symbols   []string `json:"symbols"`
		Intervals []string `json:"intervals"`
	}
	exchanges := []ExchangeInfo{
		{ID: "binance", Name: "Binance", Symbols: []string{"BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT"}, Intervals: []string{"1m", "5m", "15m", "1h", "4h", "1d"}},
		{ID: "okx", Name: "OKX", Symbols: []string{"BTC-USDT", "ETH-USDT", "SOL-USDT"}, Intervals: []string{"1m", "5m", "15m", "1H", "4H", "1D"}},
		{ID: "gate", Name: "Gate.io", Symbols: []string{"BTC_USDT", "ETH_USDT", "SOL_USDT"}, Intervals: []string{"1m", "5m", "15m", "1h", "4h", "1d"}},
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(exchanges)
}

func handleLocalKlines(w http.ResponseWriter, r *http.Request) {
	exchange := r.URL.Query().Get("exchange")
	symbol := r.URL.Query().Get("symbol")
	interval := r.URL.Query().Get("interval")
	limit := r.URL.Query().Get("limit")
	if exchange == "" || symbol == "" {
		http.Error(w, "exchange and symbol required", http.StatusBadRequest)
		return
	}
	if interval == "" {
		interval = "1h"
	}
	if limit == "" {
		limit = "200"
	}

	var klines []Kline
	var err error
	switch exchange {
	case "binance":
		klines, err = fetchBinance(symbol, interval, limit)
	case "okx":
		klines, err = fetchOKX(symbol, interval, limit)
	case "gate":
		klines, err = fetchGate(symbol, interval, limit)
	default:
		http.Error(w, "unsupported exchange", http.StatusBadRequest)
		return
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(klines)
}
