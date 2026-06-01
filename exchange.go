package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"time"
)

// getProxyURL 从环境变量获取 HTTP 代理地址
func getProxyURL() *url.URL {
	proxy := os.Getenv("HTTP_PROXY")
	if proxy == "" {
		proxy = os.Getenv("http_proxy")
	}
	if proxy == "" {
		return nil
	}
	u, _ := url.Parse(proxy)
	return u
}

func httpGet(rawURL string) ([]byte, error) {
	transport := &http.Transport{}
	if proxyURL := getProxyURL(); proxyURL != nil {
		transport.Proxy = http.ProxyURL(proxyURL)
	}
	client := &http.Client{Timeout: 15 * time.Second, Transport: transport}
	resp, err := client.Get(rawURL)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP %d from %s", resp.StatusCode, rawURL)
	}

	var buf []byte
	buf = make([]byte, 0, 1024*1024)
	tmp := make([]byte, 32*1024)
	for {
		n, err := resp.Body.Read(tmp)
		buf = append(buf, tmp[:n]...)
		if err != nil {
			break
		}
	}
	return buf, nil
}

// Binance K线（多域名尝试）
// GET /api/v3/klines?symbol=BTCUSDT&interval=1h&limit=200
func fetchBinance(symbol, interval, limit string) ([]Kline, error) {
	endpoints := []string{
		"https://data-api.binance.vision",
		"https://api.binance.com",
		"https://api1.binance.com",
		"https://api2.binance.com",
		"https://api3.binance.com",
	}

	var data []byte
	var err error
	for _, base := range endpoints {
		apiURL := fmt.Sprintf("%s/api/v3/klines?symbol=%s&interval=%s&limit=%s", base, symbol, interval, limit)
		data, err = httpGet(apiURL)
		if err == nil {
			break
		}
	}
	if err != nil {
		return nil, fmt.Errorf("Binance 所有节点均不可用: %w", err)
	}

	// Binance 返回 [[time, open, high, low, close, volume, ...], ...]
	var raw [][]interface{}
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, err
	}

	klines := make([]Kline, 0, len(raw))
	for _, item := range raw {
		if len(item) < 6 {
			continue
		}
		timeMs, _ := item[0].(float64)
		open, _ := strconv.ParseFloat(fmt.Sprint(item[1]), 64)
		high, _ := strconv.ParseFloat(fmt.Sprint(item[2]), 64)
		low, _ := strconv.ParseFloat(fmt.Sprint(item[3]), 64)
		closePrice, _ := strconv.ParseFloat(fmt.Sprint(item[4]), 64)
		volume, _ := strconv.ParseFloat(fmt.Sprint(item[5]), 64)

		klines = append(klines, Kline{
			Time:   int64(timeMs) / 1000,
			Open:   open,
			High:   high,
			Low:    low,
			Close:  closePrice,
			Volume: volume,
		})
	}
	return klines, nil
}

// OKX K线（多域名尝试）
// GET /api/v5/market/candles?instId=BTC-USDT&bar=1H&limit=200
func fetchOKX(symbol, interval, limit string) ([]Kline, error) {
	endpoints := []string{
		"https://www.okx.com",
		"https://okx.com",
	}

	var data []byte
	var err error
	for _, base := range endpoints {
		apiURL := fmt.Sprintf("%s/api/v5/market/candles?instId=%s&bar=%s&limit=%s", base, symbol, interval, limit)
		data, err = httpGet(apiURL)
		if err == nil {
			break
		}
	}
	if err != nil {
		return nil, fmt.Errorf("OKX 所有节点均不可用: %w", err)
	}

	// OKX 返回 {"code":"0","data":[[ts, open, high, low, close, vol, volCcy, volCcyQuote, confirm], ...]}
	var result struct {
		Code string          `json:"code"`
		Data [][]interface{} `json:"data"`
	}
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, err
	}
	if result.Code != "0" {
		return nil, fmt.Errorf("OKX API error: code=%s", result.Code)
	}

	klines := make([]Kline, 0, len(result.Data))
	for _, item := range result.Data {
		if len(item) < 6 {
			continue
		}
		timeMs, _ := strconv.ParseFloat(fmt.Sprint(item[0]), 64)
		open, _ := strconv.ParseFloat(fmt.Sprint(item[1]), 64)
		high, _ := strconv.ParseFloat(fmt.Sprint(item[2]), 64)
		low, _ := strconv.ParseFloat(fmt.Sprint(item[3]), 64)
		closePrice, _ := strconv.ParseFloat(fmt.Sprint(item[4]), 64)
		volume, _ := strconv.ParseFloat(fmt.Sprint(item[5]), 64)

		klines = append(klines, Kline{
			Time:   int64(timeMs) / 1000,
			Open:   open,
			High:   high,
			Low:    low,
			Close:  closePrice,
			Volume: volume,
		})
	}
	return klines, nil
}

// Bybit K线（多域名尝试）
// GET /v5/market/kline?category=spot&symbol=BTCUSDT&interval=60&limit=200
func fetchBybit(symbol, interval, limit string) ([]Kline, error) {
	endpoints := []string{
		"https://api.bybit.com",
		"https://api.bytick.com",
	}

	var data []byte
	var err error
	for _, base := range endpoints {
		apiURL := fmt.Sprintf("%s/v5/market/kline?category=spot&symbol=%s&interval=%s&limit=%s", base, symbol, interval, limit)
		data, err = httpGet(apiURL)
		if err == nil {
			break
		}
	}
	if err != nil {
		return nil, fmt.Errorf("Bybit 所有节点均不可用: %w", err)
	}

	// Bybit 返回 {"result":{"list":[[startTime, open, high, low, close, volume, turnover], ...]}}
	var result struct {
		Result struct {
			List [][]string `json:"list"`
		} `json:"result"`
	}
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, err
	}

	klines := make([]Kline, 0, len(result.Result.List))
	for _, item := range result.Result.List {
		if len(item) < 7 {
			continue
		}
		timeMs, _ := strconv.ParseFloat(item[0], 64)
		open, _ := strconv.ParseFloat(item[1], 64)
		high, _ := strconv.ParseFloat(item[2], 64)
		low, _ := strconv.ParseFloat(item[3], 64)
		closePrice, _ := strconv.ParseFloat(item[4], 64)
		volume, _ := strconv.ParseFloat(item[5], 64)

		klines = append(klines, Kline{
			Time:   int64(timeMs) / 1000,
			Open:   open,
			High:   high,
			Low:    low,
			Close:  closePrice,
			Volume: volume,
		})
	}
	return klines, nil
}

// Gate.io K线
// GET /api/v4/spot/candlesticks?currency_pair=BTC_USDT&interval=1h&limit=200
func fetchGate(symbol, interval, limit string) ([]Kline, error) {
	apiURL := fmt.Sprintf("https://api.gateio.ws/api/v4/spot/candlesticks?currency_pair=%s&interval=%s&limit=%s", symbol, interval, limit)
	data, err := httpGet(apiURL)
	if err != nil {
		return nil, fmt.Errorf("Gate.io 不可用: %w", err)
	}

	// Gate.io 返回 [[unix_ts, volume_quote, close, high, low, open, volume_base, is_window_closed], ...]
	var raw [][]string
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, err
	}

	klines := make([]Kline, 0, len(raw))
	for _, item := range raw {
		if len(item) < 7 {
			continue
		}
		timeSec, _ := strconv.ParseFloat(item[0], 64)
		open, _ := strconv.ParseFloat(item[5], 64)
		high, _ := strconv.ParseFloat(item[3], 64)
		low, _ := strconv.ParseFloat(item[4], 64)
		closePrice, _ := strconv.ParseFloat(item[2], 64)
		volume, _ := strconv.ParseFloat(item[6], 64)

		klines = append(klines, Kline{
			Time:   int64(timeSec),
			Open:   open,
			High:   high,
			Low:    low,
			Close:  closePrice,
			Volume: volume,
		})
	}
	return klines, nil
}
