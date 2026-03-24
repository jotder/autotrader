# Product Requirements — AutoTrader

> **Purpose:** Defines what the system must do across all phases. Source of truth for features, risk controls, and architecture constraints. Update whenever behaviour, limits, or contracts change.
>
> **Owner:** Solo developer · **Broker:** Fyers API v3 · **Market:** NSE/BSE + MCX + CDS (Indian markets)

---

## 1. Vision & Scope

Professional-grade multi-asset algorithmic trading system. Single JVM process managing 5–15 concurrent strategies across equity, F&O, commodities, and currency — with institutional-level risk controls for ₹25L+ capital deployment.

### Execution Modes

| Mode | `APP_ENV` | Behaviour |
|---|---|---|
| Backtest | `backtest` | Synchronous replay of historical candles — no API calls |
| Paper | `paper` (default) | Forward test on live data; fills simulated at live price |
| Live | `live` | Real orders via Fyers API — go-live checklist must pass |

### Phased Rollout

| Phase | Target | Scope |
|---|---|---|
| **P1 — Core Engine** | Month 1–2 | Bulletproof execution + risk + monitoring for equity. Go-live ready. |
| **P2 — Multi-Asset + UI** | Month 3–4 | F&O support, web control center, Telegram alerts |
| **P3 — Intelligence** | Month 5–6 | Data lake, advanced analytics, strategy auto-management |
| **P4 — Scale** | Month 7+ | Dynamic allocation, quant strategies, continuous validation |

---

## 2. Functional Requirements

### 2.1 Market Data (MKT)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| MKT-01 | Connect to Fyers WebSocket and receive real-time ticks for all configured symbols | P1 | ✅ |
| MKT-02 | Store ticks per symbol in thread-safe buffer ordered by arrival time | P1 | ✅ |
| MKT-03 | Support concurrent tick append (WS thread) and snapshot read (candle thread) without blocking | P1 | ✅ |
| MKT-04 | Mark context stale and block new signals if WS feed silent > 30s | P1 | ✅ |
| MKT-05 | Reconnect WebSocket on disconnect with bounded exponential backoff (max 5 attempts) | P1 | 🔧 Partial |
| MKT-06 | Fetch historical candles via Fyers REST API to seed indicators on startup | P1 | 📋 Planned |
| MKT-07 | All timestamps in `Asia/Kolkata` timezone | P1 | ✅ |
| MKT-08 | Persist all live ticks to local storage for data lake (hybrid: embedded DB + file archive) | P3 | 📋 Planned |
| MKT-09 | Support multi-asset symbol formats (equity, futures, options, commodity, currency) | P2 | 📋 Planned |
| MKT-10 | Options chain data: strike prices, OI, IV for configured underlyings | P2 | 📋 Planned |

### 2.2 Candle Aggregation (CND)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| CND-01 | Aggregate live ticks into OHLCV candles for each (symbol × timeframe) | P1 | ✅ |
| CND-02 | Supported timeframes: M1, M5, M15, H1 (configurable per strategy via YAML) | P1 | ✅ (M5/M15/H1) |
| CND-03 | Candle boundary in `Asia/Kolkata`, aligned to clock (09:15, 09:20, …) | P1 | ✅ |
| CND-04 | Only emit fully closed candles to strategy engine — no partial candles | P1 | ✅ |
| CND-05 | OHLCV: open=first LTP, high=max LTP, low=min LTP, close=last LTP, volume=Σ(lastTradedQty) | P1 | ✅ |
| CND-06 | Prune tick buffer to 1-candle lookback after emission to control memory | P1 | ✅ |

### 2.3 Technical Analysis (TA)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| TA-01 | Compute EMA(configurable period) on each closed candle via ta4j | P1 | ✅ (EMA-20, EMA-50) |
| TA-02 | Compute RSI(configurable period) for mean-reversion detection | P1 | ✅ (RSI-14) |
| TA-03 | Compute ATR(configurable period) for stop-loss sizing | P1 | ✅ (ATR-14) |
| TA-04 | Compute relative volume (current bar / N-period average) | P1 | ✅ (20-bar) |
| TA-05 | Require minimum N closed candles (configurable) before emitting signals | P1 | ✅ (21) |
| TA-06 | Classify market trend: STRONG_BULLISH, BULLISH, SIDEWAYS, BEARISH, STRONG_BEARISH | P1 | ✅ |
| TA-07 | All indicator periods and thresholds configurable via strategy YAML | P1 | ✅ |
| TA-08 | Support Bollinger Bands, MACD, VWAP, SuperTrend as pluggable indicators | P2 | 📋 Planned |
| TA-09 | Options Greeks computation: delta, gamma, theta, vega (Black-Scholes or equivalent) | P2 | 📋 Planned |
| TA-10 | IV surface and IV percentile tracking for options strategies | P2 | 📋 Planned |

### 2.4 Strategy Engine (STR)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| STR-01 | Evaluate configured strategies per candle close (currently: Trend, MeanRev, VolBreakout) | P1 | ✅ |
| STR-02 | Multi-timeframe compound gate: M5 directional → M15 agrees → H1 not opposing | P1 | ✅ |
| STR-03 | Combined confidence ≥ threshold (configurable, default 0.70) for signal to proceed | P1 | ✅ |
| STR-04 | H1 agreement adds configurable confidence boost (default +5%) | P1 | ✅ |
| STR-05 | No new entry if open position exists for that symbol | P1 | ✅ |
| STR-06 | Configurable cooldown after position close (default: 25min / 5 × M5) | P1 | ✅ |
| STR-07 | No new entries after configurable cutoff time (default: 15:00 IST) | P1 | ✅ |
| STR-08 | Each strategy has its own YAML config section with all tunables | P1 | ✅ |
| STR-09 | Strategy enable/disable at runtime (via UI or YAML hot-reload) | P1 | ✅ (YAML) |
| STR-10 | Pluggable strategy interface: add new strategies without modifying engine core | P1 | 📋 Planned |
| STR-11 | Produce stable correlation ID per signal (symbol + direction + epoch) for idempotency | P1 | ✅ |
| STR-12 | Support multi-leg F&O strategies (spreads, straddles, strangles) | P2 | 📋 Planned |
| STR-13 | Greeks-aware exit triggers for options (delta/theta decay thresholds) | P2 | 📋 Planned |
| STR-14 | Quant/statistical strategies: mean-reversion, pairs trading | P4 | 📋 Planned |

### 2.5 Strategy Configuration (YAML) — NEW

| ID | Requirement | Phase | Status |
|---|---|---|---|
| CFG-Y01 | YAML config files grouped by type: `config/strategies/intraday.yaml`, `positional.yaml`, `options.yaml` | P1 | ✅ |
| CFG-Y02 | Each strategy section includes: enabled flag, symbols, timeframe, active hours, cooldown | P1 | ✅ |
| CFG-Y03 | Per-strategy indicator config: all periods, thresholds, multipliers | P1 | ✅ |
| CFG-Y04 | Per-strategy risk overrides: stop-loss method, TP method, position size %, max trades/day | P1 | ✅ |
| CFG-Y05 | Per-strategy order config: order type, slippage tolerance, product type | P1 | ✅ |
| CFG-Y06 | Global defaults in `config/defaults.yaml`; strategy YAML overrides specific values | P1 | ✅ |
| CFG-Y07 | Hot-reload: file watcher detects YAML changes, validates, and applies without restart | P1 | ✅ |
| CFG-Y08 | UI can read/write strategy YAML; changes saved to file and applied immediately | P2 | 📋 Planned |
| CFG-Y09 | Validation: reject invalid config with rollback to previous values + WARN log | P1 | ✅ |
| CFG-Y10 | Version history: keep last 5 config snapshots for rollback | P2 | 📋 Planned |

### 2.6 Risk Management (RSK)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| RSK-01 | Kill switch blocks all new entries; allows only exits | P1 | ✅ |
| RSK-02 | Kill switch auto-triggered if daily realized PnL ≤ configurable loss limit | P1 | ✅ (−₹5K) |
| RSK-03 | Kill switch manual activation via API | P1 | ✅ |
| RSK-04 | Daily profit lock: block new entries after configurable profit threshold | P1 | ✅ (₹15K) |
| RSK-05 | No new entries after time cutoff (configurable) | P1 | ✅ |
| RSK-06 | Suspend strategy for the day after N consecutive losses (configurable, default 3) | P1 | ✅ |
| RSK-07 | Max exposure per symbol = configurable % of capital (default 20%) | P1 | ✅ |
| RSK-08 | Position size = floor(riskBudget / riskPerUnit), lot-aligned, fat-finger capped | P1 | ✅ |
| RSK-09 | Risk budget per trade = configurable % of capital (default 2%) | P1 | ✅ |
| RSK-10 | Reject if risk-per-unit ≤ 0 or sized quantity = 0 | P1 | ✅ |
| RSK-11 | Initial SL = entry ± ATR multiplier; initial TP = entry ± R-multiple (configurable) | P1 | ✅ |
| RSK-12 | SL determined before order submission | P1 | ✅ |
| RSK-13 | Daily risk state reset via `resetDay()` at session start | P1 | ✅ |
| RSK-14 | **Anomaly auto-protection**: on flash crash / API down / unusual volume → close all positions, kill all strategies, go to cash, require manual restart | P1 | 📋 Planned |
| RSK-15 | Per-strategy risk limits (override global defaults via YAML) | P1 | ✅ |
| RSK-16 | Max portfolio-level drawdown limit (% of capital) → full auto-protection | P1 | 📋 Planned |
| RSK-17 | F&O margin awareness: check margin requirement before order | P2 | 📋 Planned |
| RSK-18 | Options-specific risk: max premium at risk, Greeks-based position limits | P2 | 📋 Planned |

### 2.7 Capital Allocation (CAP) — NEW

| ID | Requirement | Phase | Status |
|---|---|---|---|
| CAP-01 | Dynamic capital allocation across strategies with rebalancing | P4 | 📋 Planned |
| CAP-02 | Kelly criterion or risk-parity based allocation engine | P4 | 📋 Planned |
| CAP-03 | Auto-rebalance based on recent strategy performance (rolling window) | P4 | 📋 Planned |
| CAP-04 | Per-strategy capital limits (floor and ceiling) | P4 | 📋 Planned |
| CAP-05 | Initial phase: fixed capital per strategy (configurable in YAML) | P1 | 📋 Planned |

### 2.8 Position Monitoring & Exit (POS)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| POS-01 | Monitor all open positions every 1 second | P1 | ✅ |
| POS-02 | Exit on stop-loss hit | P1 | ✅ |
| POS-03 | Exit on take-profit hit | P1 | ✅ |
| POS-04 | Force square-off all positions at 15:15 IST (configurable) | P1 | ✅ |
| POS-05 | Trailing stop: activate at configurable % gain, step by configurable % | P1 | ✅ |
| POS-06 | Trailing stop is monotonic (long SL only moves up; short only down) | P1 | ✅ |
| POS-07 | Notify StrategyEvaluator on position close for cooldown reset | P1 | ✅ |
| POS-08 | Price data from live TickStore (latest LTP) | P1 | ✅ |
| POS-09 | Position reconciliation on startup (diff in-memory vs broker) | P1 | ✅ |
| POS-10 | Manual exit via API (`POST /api/exit/{correlationId}`) | P1 | ✅ |

### 2.9 Order Execution (ORD)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| ORD-01 | Paper mode: fill at current LTP, no API calls | P1 | ✅ |
| ORD-02 | Backtest mode: fill at next-bar open ± configurable slippage | P1 | ✅ |
| ORD-03 | Live mode: market order via Fyers API, product type INTRADAY | P1 | ✅ |
| ORD-04 | Exit side always opposite to entry | P1 | ✅ |
| ORD-05 | Retry failed API calls up to 3× with exponential backoff (500/1000/2000ms) | P1 | ✅ |
| ORD-06 | Never retry 4xx (except 429 rate limit) | P1 | ✅ |
| ORD-07 | Same correlationId on all retries for dedup | P1 | ✅ |
| ORD-08 | Live executor requires valid access token before first use | P1 | ✅ |
| ORD-09 | OMS state machine with idempotent client order IDs | P1 | 📋 Planned |
| ORD-10 | Support limit orders and bracket orders (configurable per strategy in YAML) | P2 | 📋 Planned |
| ORD-11 | F&O order support: futures, options buy/sell, multi-leg orders | P2 | 📋 Planned |

### 2.10 Transaction Journal (JRN)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| JRN-01 | Append every event to NDJSON file (one JSON object per line) | P1 | ✅ |
| JRN-02 | File path: `data/journal/journal-YYYY-MM-DD-<mode>.ndjson` | P1 | ✅ |
| JRN-03 | Events: SIGNAL_GENERATED, SIGNAL_REJECTED, ORDER_ENTRY, ORDER_EXIT, TRADE_CLOSED | P1 | ✅ |
| JRN-04 | Every event carries: timestamp, type, mode, correlationId | P1 | ✅ |
| JRN-05 | Thread-safe writes (synchronized on writer lock) | P1 | ✅ |
| JRN-06 | In-memory list of closed TradeRecords for same-session queries | P1 | ✅ |
| JRN-07 | TRADE_CLOSED includes: PnL, PnL%, R-multiple, MAE, MFE, hold duration, exit reason | P1 | ✅ |
| JRN-08 | Journal includes strategy signals context (indicator values, market state at decision time) | P3 | 📋 Planned |

### 2.11 Analytics & Reporting (ANA)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| ANA-01 | Session metrics: win rate, profit factor, expectancy, Sharpe, max drawdown | P1 | ✅ |
| ANA-02 | Breakdown by strategy and by symbol | P1 | ✅ |
| ANA-03 | Track avg R-multiple, avg win, avg loss, avg hold time | P1 | ✅ |
| ANA-04 | Max consecutive loss count | P1 | ✅ |
| ANA-05 | Rule-based suggestions when ≥ 10 trades available | P1 | ✅ |
| ANA-06 | Equity curve data (running PnL after each trade) | P1 | ✅ |
| ANA-07 | Full report on JVM shutdown if trades occurred | P1 | ✅ |
| ANA-08 | Drawdown curves and peak-to-trough visualization data | P3 | 📋 Planned |
| ANA-09 | Win rate by time-of-day, day-of-week heat maps | P3 | 📋 Planned |
| ANA-10 | Exportable data for Python/R analysis (CSV/Parquet) | P3 | 📋 Planned |
| ANA-11 | Strategy drift detection: alert when live deviates from backtest profile | P4 | 📋 Planned |

### 2.12 Health Monitoring (HLT)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| HLT-01 | Health checks every 60 seconds | P1 | ✅ |
| HLT-02 | Tick freshness per symbol (stale if > 30s ago) | P1 | ✅ |
| HLT-03 | Candle worker count vs expected | P1 | ✅ |
| HLT-04 | Strategy evaluator queue depth (warn > 500) | P1 | ✅ |
| HLT-05 | Position monitor running state | P1 | ✅ |
| HLT-06 | JVM heap: WARN at 80%, CRITICAL at 90% | P1 | ✅ |
| HLT-07 | Suppress tick staleness checks outside market hours | P1 | 🔧 Partial |
| HLT-08 | Fyers API health: track error rate, latency p99, rate-limit hits | P1 | 📋 Planned |
| HLT-09 | Auto-restart on recoverable failures (WS reconnect, scheduler restart) | P1 | 📋 Planned |

### 2.13 Configuration (CFG)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| CFG-01 | Load base config from `.env` via dotenv-java | P1 | ✅ |
| CFG-02 | Required keys: FYERS_APP_ID, SECRET_KEY, REDIRECT_URI, AUTH_CODE, SYMBOLS, APP_ENV, LOG_LEVEL | P1 | ✅ |
| CFG-03 | Fail fast on missing required keys | P1 | ✅ |
| CFG-04 | All risk/strategy params overridable via `.env` or YAML | P1 | ✅ (.env) |
| CFG-05 | Secrets never logged or committed | P1 | ✅ |
| CFG-06 | Hot-reload: `.env` for global params, YAML for strategy params, with validation + rollback | P1 | ✅ (YAML) |
| CFG-07 | Token auto-refresh (background refresh before 8-hour expiry) | P1 | 📋 Planned |

### 2.14 Backtest Engine (BKT)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| BKT-01 | Replay historical M5 candles synchronously through same pipeline | P1 | ✅ |
| BKT-02 | Derive M15 (3×M5) and H1 (12×M5) from M5 feed | P1 | ✅ |
| BKT-03 | Fill at next-bar open + slippage | P1 | ✅ |
| BKT-04 | SL/TP against candle high/low; SL wins if both hit same bar | P1 | ✅ |
| BKT-05 | Same kill switch and consecutive-loss limits as live | P1 | ✅ |
| BKT-06 | Full StrategyAnalyzer report on completion | P1 | ✅ |
| BKT-07 | Walk-forward optimization: rolling train/test windows | P3 | 📋 Planned |
| BKT-08 | Monte Carlo simulation for confidence intervals | P4 | 📋 Planned |

### 2.15 Notifications (NTF)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| NTF-01 | Telegram bot integration for all alerts | P2 | 📋 Planned |
| NTF-02 | Alert categories: TRADE (entry/exit), RISK (kill switch, limits), SYSTEM (errors, health) | P2 | 📋 Planned |
| NTF-03 | Configurable alert levels per category (e.g., only RISK+SYSTEM to Telegram) | P2 | 📋 Planned |
| NTF-04 | Daily session summary sent to Telegram after market close | P2 | 📋 Planned |
| NTF-05 | Webhook support for generic notifications (current implementation) | P1 | ✅ |

### 2.16 Web UI — Control Center (UI)

| ID | Requirement | Phase | Status |
|---|---|---|---|
| UI-01 | Data-tables-first dashboard: positions, trades, orders, signals | P2 | 📋 Planned |
| UI-02 | Real-time position table with live PnL (WebSocket push) | P2 | 📋 Planned |
| UI-03 | Kill switch toggle, strategy pause/resume, manual exit buttons | P2 | 📋 Planned |
| UI-04 | Strategy configuration editor (reads/writes YAML, applies immediately) | P2 | 📋 Planned |
| UI-05 | Performance dashboard: KPI cards, equity curve, drawdown chart | P2 | 📋 Planned |
| UI-06 | Risk dashboard: daily PnL meter, exposure bars, kill switch status | P2 | 📋 Planned |
| UI-07 | Audit log viewer: searchable by correlationId, symbol, type, time | P2 | 📋 Planned |
| UI-08 | System health panel: traffic-light per component, JVM gauge | P2 | 📋 Planned |
| UI-09 | Backtest wizard: symbol selector, date range, slippage, run button | P3 | 📋 Planned |
| UI-10 | Architect for multi-user later (auth layer, user-scoped data) | P2 | 📋 Planned |

### 2.17 Data Lake (DL) — NEW

| ID | Requirement | Phase | Status |
|---|---|---|---|
| DL-01 | Capture and persist all live ticks to local storage during market hours | P3 | 📋 Planned |
| DL-02 | Hybrid storage: embedded DB (H2) for recent/hot data + Parquet files for historical archive | P3 | 📋 Planned |
| DL-03 | Automatic daily archival: move previous day's data from DB to file archive | P3 | 📋 Planned |
| DL-04 | Query API: fetch historical ticks/candles by symbol, date range, timeframe | P3 | 📋 Planned |
| DL-05 | Export to CSV/Parquet for external analysis (Python/R) | P3 | 📋 Planned |

### 2.18 Continuous Validation (VAL) — NEW

| ID | Requirement | Phase | Status |
|---|---|---|---|
| VAL-01 | Mandatory pipeline: backtest → walk-forward → paper trade → small live → scale up | P4 | 📋 Planned |
| VAL-02 | Live strategy monitoring: auto-disable if performance degrades below thresholds | P4 | 📋 Planned |
| VAL-03 | Strategy scorecard: compare live vs backtest metrics in real-time | P4 | 📋 Planned |
| VAL-04 | Minimum paper trading burn-in period before live promotion (configurable days) | P1 | 📋 Planned |

---

## 3. Non-Functional Requirements

### 3.1 Performance

| ID | Requirement | Target | Phase | Status |
|---|---|---|---|---|
| NFR-01 | Tick-to-risk latency | < 1 ms | P1 | ✅ (Disruptor) |
| NFR-02 | Position monitor cycle time | ≤ 1s | P1 | ✅ |
| NFR-03 | Candle boundary precision | ± 500ms | P1 | ✅ |
| NFR-04 | WebSocket tick ingestion | O(1) append, no blocking | P1 | ✅ |
| NFR-05 | YAML config reload | < 500ms with zero trade disruption | P1 | 📋 Planned |

### 3.2 Reliability

| ID | Requirement | Phase | Status |
|---|---|---|---|
| NFR-06 | Every broker call: retry/backoff with bounded attempts (max 3) | P1 | ✅ |
| NFR-07 | Every order decision fully auditable: signal → risk → broker → response | P1 | ✅ |
| NFR-08 | Capital protection above availability — fail closed, not open | P1 | ✅ |
| NFR-09 | Shutdown hook drains Disruptor ring buffer before exit | P1 | ✅ |
| NFR-10 | Duplicate order prevention: symbol lock + stable correlationId | P1 | ✅ |
| NFR-11 | Position reconciliation on startup | P1 | ✅ |
| NFR-12 | Circuit breaker on API error rate (> 5 errors/min → backoff) | P1 | 📋 Planned |

### 3.3 Security

| ID | Requirement | Phase | Status |
|---|---|---|---|
| NFR-13 | Raw access token never in logs | P1 | ✅ |
| NFR-14 | Credentials in `.env` only, never in source | P1 | ✅ |
| NFR-15 | Paper mode cannot call order placement APIs | P1 | ✅ |
| NFR-16 | UI authentication (basic auth initially, upgrade path to OAuth) | P2 | 📋 Planned |

### 3.4 Observability

| ID | Requirement | Phase | Status |
|---|---|---|---|
| NFR-17 | SLF4J + Logback exclusively — no System.out | P1 | ✅ |
| NFR-18 | Log levels: INFO routine, WARN retry/degraded, ERROR exception, DEBUG payloads, TRACE latency | P1 | ✅ |
| NFR-19 | Every strategy cycle logs: symbol, checks, pass/fail per gate, decision | P1 | ✅ |
| NFR-20 | NDJSON audit trail for every signal (approved + rejected) | P1 | ✅ |

### 3.5 Maintainability

| ID | Requirement | Phase | Status |
|---|---|---|---|
| NFR-21 | Maven build only — no Gradle | P1 | ✅ |
| NFR-22 | Java 25+ (`maven.compiler.release=25`) | P1 | ✅ |
| NFR-23 | Shared mutable state: ConcurrentHashMap, AtomicBoolean, volatile, or explicit locks | P1 | ✅ |
| NFR-24 | I/O-bound tasks on `Thread.ofVirtual()` | P1 | ✅ |
| NFR-25 | Periodic jobs on `ScheduledExecutorService` | P1 | ✅ |
| NFR-26 | Pluggable strategy interface for easy strategy addition | P1 | 📋 Planned |

---

## 4. Risk Parameter Defaults

| Parameter | Default | Config Key | Overridable in YAML |
|---|---|---|---|
| Max risk per trade | 2% of capital | `RISK_MAX_PER_TRADE_PCT` | ✅ per-strategy |
| Max daily loss | ₹5,000 | `RISK_MAX_DAILY_LOSS_INR` | ❌ global only |
| Daily profit lock | ₹15,000 | `RISK_MAX_DAILY_PROFIT_INR` | ❌ global only |
| Initial capital | ₹25,00,000 | `RISK_INITIAL_CAPITAL_INR` | ❌ global only |
| Max exposure per symbol | 20% | `RISK_MAX_EXPOSURE_PER_SYMBOL_PCT` | ✅ per-strategy |
| Max quantity per order | 1,000 | `RISK_MAX_QTY_PER_ORDER` | ✅ per-strategy |
| Max consecutive losses | 3 | `RISK_MAX_CONSECUTIVE_LOSSES` | ✅ per-strategy |
| SL ATR multiplier | 2× | `RISK_STOP_LOSS_ATR_MULTIPLIER` | ✅ per-strategy |
| TP R-multiple | 2× (= 4× ATR) | `RISK_TAKE_PROFIT_R_MULTIPLIER` | ✅ per-strategy |
| Trailing activation | 1% unrealized gain | `RISK_TRAIL_ACTIVATION_PCT` | ✅ per-strategy |
| Trailing step | 1% of HWM | `RISK_TRAIL_STEP_PCT` | ✅ per-strategy |
| Instrument lot size | 1 (equity) | `RISK_INSTRUMENT_LOT_SIZE` | ✅ per-strategy |
| No new trades after | 15:00 IST | `RISK_NO_NEW_TRADES_AFTER` | ✅ per-strategy |
| Force square-off | 15:15 IST | `RISK_MARKET_CLOSE_TIME` | ❌ global only |
| Exchange timezone | `Asia/Kolkata` | `RISK_EXCHANGE_ZONE` | ❌ global only |

---

## 5. Session Time Contract

| Phase | Window (IST) | Activity |
|---|---|---|
| Pre-open | 09:00–09:15 | Config validation, WS connect, indicator seeding, reconciliation |
| Opening | 09:15–10:00 | All strategies active; Volatility Breakout prioritized |
| Mid-day | 10:00–14:30 | Trend Following + Mean Reversion; Breakout suppressed |
| Closing | 14:30–15:00 | Volatility Breakout active again |
| No new entries | 15:00–15:15 | Existing positions only |
| Force square-off | 15:15 | All positions closed by market order |
| Post-market | After 15:15 | Health checks suppressed; journal flushed; daily summary |

---

## 6. Strategy YAML Config Schema (Target)

```yaml
# config/strategies/intraday.yaml
strategies:
  trend_following:
    enabled: true
    symbols: ["NSE:SBIN-EQ", "NSE:RELIANCE-EQ"]
    timeframe: M5
    active_hours: { start: "09:15", end: "15:00" }
    cooldown_minutes: 25
    max_trades_per_day: 10

    indicators:
      ema_fast: 20
      ema_slow: 50
      rsi_period: 14
      atr_period: 14
      rel_vol_period: 20
      min_candles: 21

    entry:
      min_confidence: 0.85
      rel_vol_threshold: 1.2
      trend_strength: STRONG_BULLISH  # or STRONG_BEARISH for short

    risk:
      risk_per_trade_pct: 2.0
      sl_atr_multiplier: 2.0
      tp_r_multiple: 2.0
      trailing_activation_pct: 1.0
      trailing_step_pct: 1.0
      max_exposure_pct: 20.0
      max_qty: 1000
      max_consecutive_losses: 3

    order:
      type: MARKET          # MARKET | LIMIT
      slippage_tolerance: 0.05
      product_type: INTRADAY # INTRADAY | CNC | MARGIN
```

---

*Last updated: 2026-03-23*
