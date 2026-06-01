package main

import (
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"sort"
	"time"
)

// AnalysisResult 本地增强分析结果
type AnalysisResult struct {
	Bollinger    BollingerBand     `json:"bollinger"`
	SupportResist []SRLevel        `json:"support_resist"`
	VolumeProfile []VPLevel        `json:"volume_profile"`
	MultiTF       MultiTFSignal    `json:"multi_tf"`
	TrendStrength  TrendScore      `json:"trend_strength"`
	EnhancedSignal EnhancedSignal  `json:"enhanced_signal"`
	Divergence    []DivergenceInfo `json:"divergence"`
}

type BollingerBand struct {
	Upper  float64 `json:"upper"`
	Middle float64 `json:"middle"`
	Lower  float64 `json:"lower"`
	Width  float64 `json:"width"`
	PB     float64 `json:"pb"` // %B 指标
}

type SRLevel struct {
	Price    float64 `json:"price"`
	Type     string  `json:"type"` // "support" or "resistance"
	Strength int     `json:"strength"`
}

type VPLevel struct {
	Price  float64 `json:"price"`
	Volume float64 `json:"volume"`
	Pct    float64 `json:"pct"`
}

type MultiTFSignal struct {
	M15  string `json:"m15"`
	H1   string `json:"h1"`
	H4   string `json:"h4"`
	D1   string `json:"d1"`
	Score int    `json:"score"` // -4 到 +4
}

type TrendScore struct {
	ADX      float64 `json:"adx"`
	Trend    string  `json:"trend"` // "strong_up","up","weak","down","strong_down"
	Momentum float64 `json:"momentum"`
	Volume   float64 `json:"volume_trend"`
}

type EnhancedSignal struct {
	Direction  string  `json:"direction"` // buy/sell/wait
	Confidence float64 `json:"confidence"`
	Score      float64 `json:"score"`
	Reasons    []string `json:"reasons"`
}

type DivergenceInfo struct {
	Type   string `json:"type"` // "bullish" or "bearish"
	Indicator string `json:"indicator"`
	Desc   string `json:"desc"`
}

type candle struct {
	Time   int64
	Open   float64
	High   float64
	Low    float64
	Close  float64
	Volume float64
}

func handleAnalysis(w http.ResponseWriter, r *http.Request) {
	instID := r.URL.Query().Get("inst_id")
	bar := r.URL.Query().Get("bar")
	if instID == "" {
		instID = "BTC-USDT"
	}
	if bar == "" {
		bar = "1H"
	}

	klines, err := fetchRemoteKlines(instID, bar, 200)
	if err != nil {
		http.Error(w, fmt.Sprintf("获取K线失败: %v", err), http.StatusBadGateway)
		return
	}
	if len(klines) < 30 {
		http.Error(w, "K线数据不足", http.StatusBadRequest)
		return
	}

	result := analyzeKlines(klines)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

func handleAnalysisCached(w http.ResponseWriter, r *http.Request) {
	cacheKey := "analysis:" + r.URL.RawQuery
	if cached, ok := cacheGet(cacheKey); ok {
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("X-Cache", "HIT")
		w.Write(cached)
		return
	}

	instID := r.URL.Query().Get("inst_id")
	bar := r.URL.Query().Get("bar")
	if instID == "" { instID = "BTC-USDT" }
	if bar == "" { bar = "1H" }

	klines, err := fetchRemoteKlines(instID, bar, 200)
	if err != nil {
		http.Error(w, fmt.Sprintf("获取K线失败: %v", err), http.StatusBadGateway)
		return
	}
	if len(klines) < 30 {
		http.Error(w, "K线数据不足", http.StatusBadRequest)
		return
	}

	result := analyzeKlines(klines)
	data, _ := json.Marshal(result)
	cacheSet(cacheKey, data, 30*time.Second)

	w.Header().Set("Content-Type", "application/json")
	w.Write(data)
}

func fetchRemoteKlines(instID, bar string, limit int) ([]candle, error) {
	url := fmt.Sprintf("%s/api/klines?inst_id=%s&bar=%s&limit=%d", remoteAPI, instID, bar, limit)
	client := httpClient()
	resp, err := client.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	var result struct {
		Candles []struct {
			TS   int64   `json:"ts"`
			Open float64 `json:"open"`
			High float64 `json:"high"`
			Low  float64 `json:"low"`
			Close float64 `json:"close"`
			Vol  float64 `json:"vol"`
		} `json:"candles"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	klines := make([]candle, len(result.Candles))
	for i, c := range result.Candles {
		klines[i] = candle{Time: c.TS / 1000, Open: c.Open, High: c.High, Low: c.Low, Close: c.Close, Volume: c.Vol}
	}
	return klines, nil
}

func httpClient() *http.Client {
	return &http.Client{Timeout: 12000000000} // 12s
}

func analyzeKlines(kl []candle) AnalysisResult {
	closes := extractCloses(kl)
	highs := extractHighs(kl)
	lows := extractLows(kl)
	volumes := extractVolumes(kl)

	return AnalysisResult{
		Bollinger:     calcBollinger(closes, 20, 2.0),
		SupportResist: calcSupportResistance(kl),
		VolumeProfile: calcVolumeProfile(kl, 20),
		TrendStrength: calcTrendStrength(closes, highs, lows, volumes),
		Divergence:    detectDivergence(kl, closes),
		EnhancedSignal: EnhancedSignal{Direction: "wait", Confidence: 0, Score: 0},
	}
}

func extractCloses(kl []candle) []float64 {
	r := make([]float64, len(kl))
	for i, k := range kl { r[i] = k.Close }
	return r
}
func extractHighs(kl []candle) []float64 {
	r := make([]float64, len(kl))
	for i, k := range kl { r[i] = k.High }
	return r
}
func extractLows(kl []candle) []float64 {
	r := make([]float64, len(kl))
	for i, k := range kl { r[i] = k.Low }
	return r
}
func extractVolumes(kl []candle) []float64 {
	r := make([]float64, len(kl))
	for i, k := range kl { r[i] = k.Volume }
	return r
}

// 布林带
func calcBollinger(closes []float64, period int, mult float64) BollingerBand {
	n := len(closes)
	if n < period {
		return BollingerBand{}
	}
	// SMA
	sum := 0.0
	for i := n - period; i < n; i++ { sum += closes[i] }
	mid := sum / float64(period)
	// StdDev
	varSum := 0.0
	for i := n - period; i < n; i++ { d := closes[i] - mid; varSum += d * d }
	std := math.Sqrt(varSum / float64(period))
	upper := mid + mult*std
	lower := mid - mult*std
	width := (upper - lower) / mid * 100
	pb := 0.0
	if upper != lower { pb = (closes[n-1] - lower) / (upper - lower) }
	return BollingerBand{Upper: upper, Middle: mid, Lower: lower, Width: width, PB: pb}
}

// 支撑阻力位（基于局部极值）
func calcSupportResistance(kl []candle) []SRLevel {
	n := len(kl)
	if n < 10 { return nil }
	levels := make(map[float64]int)
	window := 5
	for i := window; i < n-window; i++ {
		// 局部高点
		isHigh := true
		isLow := true
		for j := i - window; j <= i+window; j++ {
			if j == i { continue }
			if kl[j].High >= kl[i].High { isHigh = false }
			if kl[j].Low <= kl[i].Low { isLow = false }
		}
		if isHigh {
			p := math.Round(kl[i].High*100) / 100
			levels[p]++
		}
		if isLow {
			p := math.Round(kl[i].Low*100) / 100
			levels[p]++
		}
	}
	// 聚类合并
	currentPrice := kl[n-1].Close
	var result []SRLevel
	for price, count := range levels {
		if count < 1 { continue }
		srType := "resistance"
		if price < currentPrice { srType = "support" }
		result = append(result, SRLevel{Price: price, Type: srType, Strength: count})
	}
	sort.Slice(result, func(i, j int) bool { return result[i].Strength > result[j].Strength })
	if len(result) > 8 { result = result[:8] }
	return result
}

// 成交量分布
func calcVolumeProfile(kl []candle, bins int) []VPLevel {
	if len(kl) == 0 { return nil }
	minP, maxP := kl[0].Low, kl[0].High
	for _, k := range kl {
		if k.Low < minP { minP = k.Low }
		if k.High > maxP { maxP = k.High }
	}
	if maxP == minP { return nil }
	step := (maxP - minP) / float64(bins)
	vols := make([]float64, bins)
	for _, k := range kl {
		for b := 0; b < bins; b++ {
			bottom := minP + float64(b)*step
			top := bottom + step
			if k.Low < top && k.High > bottom {
				vols[b] += k.Volume
			}
		}
	}
	totalVol := 0.0
	for _, v := range vols { totalVol += v }
	if totalVol == 0 { return nil }
	var result []VPLevel
	for b, v := range vols {
		if v > 0 {
			result = append(result, VPLevel{
				Price: minP + (float64(b)+0.5)*step,
				Volume: v,
				Pct: v / totalVol * 100,
			})
		}
	}
	sort.Slice(result, func(i, j int) bool { return result[i].Volume > result[j].Volume })
	if len(result) > 10 { result = result[:10] }
	return result
}

// 趋势强度
func calcTrendStrength(closes, highs, lows, volumes []float64) TrendScore {
	n := len(closes)
	if n < 20 { return TrendScore{} }

	// 简化 ADX 计算
	adx := calcSimpleADX(highs, lows, closes, 14)

	// 动量: 最近价格 vs 20 期前
	momentum := (closes[n-1] - closes[n-20]) / closes[n-20] * 100

	// 成交量趋势: 最近5期均量 vs 前20期均量
	recentVol := avg(volumes[n-5:])
	prevVol := avg(volumes[n-20:n-5])
	volTrend := 0.0
	if prevVol > 0 { volTrend = (recentVol - prevVol) / prevVol * 100 }

	trend := "weak"
	if adx > 25 {
		if momentum > 2 { trend = "strong_up" } else if momentum > 0 { trend = "up" }
		if momentum < -2 { trend = "strong_down" } else if momentum < 0 { trend = "down" }
	} else {
		if momentum > 1 { trend = "up" } else if momentum < -1 { trend = "down" }
	}

	return TrendScore{ADX: adx, Trend: trend, Momentum: momentum, Volume: volTrend}
}

func calcSimpleADX(highs, lows, closes []float64, period int) float64 {
	n := len(highs)
	if n < period+1 { return 0 }
	// 简化: 用平均真实波幅比率估算
	sumTR := 0.0
	sumDM := 0.0
	for i := n - period; i < n; i++ {
		tr := math.Max(highs[i]-lows[i], math.Max(math.Abs(highs[i]-closes[i-1]), math.Abs(lows[i]-closes[i-1])))
		sumTR += tr
		upMove := highs[i] - highs[i-1]
		downMove := lows[i-1] - lows[i]
		if upMove > downMove && upMove > 0 { sumDM += upMove }
		if downMove > upMove && downMove > 0 { sumDM += downMove }
	}
	if sumTR == 0 { return 0 }
	return math.Min(100, sumDM/sumTR*100)
}

// RSI 背离检测
func detectDivergence(kl []candle, closes []float64) []DivergenceInfo {
	n := len(closes)
	if n < 30 { return nil }
	rsi := calcRSI(closes, 14)
	var divergences []DivergenceInfo

	// 检查最近的低点是否形成看涨背离（价格新低但 RSI 未新低）
	if n >= 40 {
		recentLow := minOf(closes[n-10:])
		prevLow := minOf(closes[n-20:n-10])
		recentRSI := minOf(rsi[n-10:])
		prevRSI := minOf(rsi[n-20:n-10])

		if recentLow < prevLow && recentRSI > prevRSI {
			divergences = append(divergences, DivergenceInfo{
				Type: "bullish", Indicator: "RSI",
				Desc: "价格创新低但RSI未创新低，看涨背离",
			})
		}
		// 看跌背离
		recentHigh := maxOf(closes[n-10:])
		prevHigh := maxOf(closes[n-20:n-10])
		recentRSIHigh := maxOf(rsi[n-10:])
		prevRSIHigh := maxOf(rsi[n-20:n-10])

		if recentHigh > prevHigh && recentRSIHigh < prevRSIHigh {
			divergences = append(divergences, DivergenceInfo{
				Type: "bearish", Indicator: "RSI",
				Desc: "价格创新高但RSI未创新高，看跌背离",
			})
		}
	}
	return divergences
}

func calcRSI(closes []float64, period int) []float64 {
	n := len(closes)
	rsi := make([]float64, n)
	if n < period+1 { return rsi }
	avgGain, avgLoss := 0.0, 0.0
	for i := 1; i <= period; i++ {
		change := closes[i] - closes[i-1]
		if change > 0 { avgGain += change } else { avgLoss -= change }
	}
	avgGain /= float64(period)
	avgLoss /= float64(period)
	if avgLoss == 0 { rsi[period] = 100 } else { rsi[period] = 100 - 100/(1+avgGain/avgLoss) }
	for i := period + 1; i < n; i++ {
		change := closes[i] - closes[i-1]
		gain, loss := 0.0, 0.0
		if change > 0 { gain = change } else { loss = -change }
		avgGain = (avgGain*float64(period-1) + gain) / float64(period)
		avgLoss = (avgLoss*float64(period-1) + loss) / float64(period)
		if avgLoss == 0 { rsi[i] = 100 } else { rsi[i] = 100 - 100/(1+avgGain/avgLoss) }
	}
	return rsi
}

func avg(v []float64) float64 {
	if len(v) == 0 { return 0 }
	s := 0.0
	for _, x := range v { s += x }
	return s / float64(len(v))
}

func minOf(v []float64) float64 {
	m := v[0]
	for _, x := range v[1:] { if x < m { m = x } }
	return m
}

func maxOf(v []float64) float64 {
	m := v[0]
	for _, x := range v[1:] { if x > m { m = x } }
	return m
}
