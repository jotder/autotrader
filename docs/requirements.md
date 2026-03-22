# Requirements — Personal Trade Assistant

> **Purpose:** Defines what the system must do. Kept in sync with the codebase; update whenever behaviour, limits, or contracts change.

---

## 1. System Overview

Personal algorithmic trading system for Indian equity markets (NSE/BSE) via the Fyers broker API v3. Fully autonomous in live mode; paper-trading and backtest modes are available for validation. A single JVM process runs the entire stack.

**Execution modes**

| Mode | Value (`APP_ENV`) | Behaviour |
|---|---|---|
| Backtest | `backtest` | Synchronous replay of historical M5 candles — no API calls |
| Paper | `paper` (default) | Forward test on live data; fills simulated at live price |
| Live | `live` | Real orders placed via Fyers API — go-live checklist must pass |

---

## 2. Functional Requirements

### 2.1 Market Data

| ID | Requirement | Status |
|---|---|---|
| MKT-01 | Connect to Fyers WebSocket and receive real-time ticks for all configured symbols | Implemented |
| MKT-02 | Store ticks per symbol in a thread-safe buffer ordered by arrival time | Implemented |
| MKT-03 | Support tick append (WebSocket thread) and snapshot read (candle thread) concurrently without blocking | Implemented |
| MKT-04 | Mark market context stale and block new signals if WebSocket feed goes silent for > 30 s | Implemented (HealthMonitor) |
| MKT-05 | Reconnect WebSocket on disconnect with bounded exponential backoff (max 5 attempts) | Partial |
| MKT-06 | Fetch historical candles via Fyers REST API to seed indicators on startup | Planned |
| MKT-07 | All timestamps must be interpreted and stored in `Asia/Kolkata` timezone | Implemented |

### 2.2 Candle Aggregation

| ID | Requirement | Status |
|---|---|---|
| CND-01 | Aggregate live ticks into OHLCV candles for each (symbol × timeframe) | Implemented |
| CND-02 | Supported timeframes: M5, M15, H1 | Implemented |
| CND-03 | Candle boundary computed in `Asia/Kolkata` time, aligned to clock (e.g. 09:15, 09:20, …) | Implemented |
| CND-04 | Only emit a candle to the strategy engine when the window is fully closed — no partial candles | Implemented |
| CND-05 | OHLCV calculation: open=first LTP, high=max LTP, low=min LTP, close=last LTP, volume=sum(lastTradedQty) | Implemented |
| CND-06 | Prune tick buffer to 1-candle lookback after each emission to control memory | Implemented |

### 2.3 Technical Analysis

| ID | Requirement | Status |
|---|---|---|
| TA-01 | Compute EMA(20) and EMA(50) on each closed candle using ta4j | Implemented |
| TA-02 | Compute RSI(14) — used for mean-reversion signal detection | Implemented |
| TA-03 | Compute ATR(14) — used for stop-loss sizing | Implemented |
| TA-04 | Compute relative volume (current bar volume / 20-period average) | Implemented |
| TA-05 | Require minimum 21 closed candles before emitting directional signals | Implemented |
| TA-06 | Classify market trend: STRONG_BULLISH, BULLISH, SIDEWAYS, BEARISH, STRONG_BEARISH | Implemented (CandleAnalyzer) |

### 2.4 Strategy Engine

| ID | Requirement | Status |
|---|---|---|
| STR-01 | Evaluate three strategies per candle: Trend Following, Mean Reversion, Volatility Breakout | Implemented |
| STR-02 | Apply multi-timeframe compound gate: M5 directional → M15 agrees → H1 not opposing | Implemented |
| STR-03 | Combined confidence must be ≥ 0.70 for a signal to proceed | Implemented |
| STR-04 | H1 agreement adds a +5% confidence boost | Implemented |
| STR-05 | No new entry allowed if an open position already exists for that symbol | Implemented |
| STR-06 | Enforce 25-minute cooldown after a position is closed (5 × M5 candle) | Implemented |
| STR-07 | No new entries after 15:00 IST | Implemented |
| STR-08 | Trend Following active all day (relVol > 1.2, strong trend) | Implemented |
| STR-09 | Mean Reversion active only 10:00–14:30 IST (RSI < 30 / > 70, sideways market) | Implemented |
| STR-10 | Volatility Breakout active only OPENING (09:15–10:00) and CLOSING (14:30–15:00) (relVol > 2.0) | Implemented |
| STR-11 | Produce a stable correlation ID per signal (symbol + direction + epoch) for idempotency | Implemented |

### 2.5 Risk Management

| ID | Requirement | Status |
|---|---|---|
| RSK-01 | Kill switch blocks all new entries; allows only exits | Implemented |
| RSK-02 | Kill switch triggered automatically if daily realized PnL ≤ −5,000 INR | Implemented |
| RSK-03 | Kill switch can be activated manually via API | Implemented |
| RSK-04 | Daily profit lock (≥ 15,000 INR): block new entries, allow exits | Implemented |
| RSK-05 | No new entries after 15:00 IST (time cutoff gate) | Implemented |
| RSK-06 | Suspend a strategy for the day after 3 consecutive losses from that strategy | Implemented |
| RSK-07 | Max exposure per symbol = 20% of total capital | Implemented |
| RSK-08 | Position size = floor(riskBudget / riskPerUnit), lot-aligned, fat-finger capped at 1,000 | Implemented |
| RSK-09 | Risk budget per trade = 2% of initial capital | Implemented |
| RSK-10 | Reject trade if risk-per-unit ≤ 0 or if sized quantity is 0 | Implemented |
| RSK-11 | Initial SL = entry ± 2 × ATR(14); initial TP = entry ± 4 × ATR(14) (2R minimum) | Implemented |
| RSK-12 | SL placement must be determined before order is submitted | Implemented |
| RSK-13 | Daily risk state resets via `resetDay()` — must be called at session start | Implemented |

### 2.6 Position Monitoring & Exit Management

| ID | Requirement | Status |
|---|---|---|
| POS-01 | Monitor all open positions every 1 second | Implemented |
| POS-02 | Trigger exit if current price hits or crosses stop-loss | Implemented |
| POS-03 | Trigger exit if current price hits or crosses take-profit | Implemented |
| POS-04 | Force square-off all positions at 15:15 IST | Implemented |
| POS-05 | Trailing stop activates when unrealized PnL ≥ +1% of entry | Implemented |
| POS-06 | Trailing stop steps by 1% of high-water-mark per additional 1% favourable move | Implemented |
| POS-07 | Trailing stop is monotonic: long SL only moves up; short SL only moves down | Implemented |
| POS-08 | Notify StrategyEvaluator on position close to reset cooldown timer | Implemented |
| POS-09 | Price data for monitoring comes from the live TickStore (latest LTP) | Implemented |

### 2.7 Order Execution

| ID | Requirement | Status |
|---|---|---|
| ORD-01 | Paper mode: fill entry at current TickStore LTP; no API calls | Implemented |
| ORD-02 | Backtest mode: fill entry at next-bar open ± configurable slippage (default 0.05%) | Implemented |
| ORD-03 | Live mode: place market order via Fyers API with product type INTRADAY | Implemented |
| ORD-04 | Exit side is always opposite to entry side (BUY→SELL, SELL→BUY) | Implemented |
| ORD-05 | Retry failed API calls up to 3 times with exponential backoff (500 / 1000 / 2000 ms) | Implemented |
| ORD-06 | Never retry on HTTP 4xx errors (except 429 rate limit) | Implemented |
| ORD-07 | Carry same correlationId on all retries — broker deduplicates to prevent double fills | Implemented |
| ORD-08 | Live executor must receive a valid access token before first use | Implemented |

### 2.8 Transaction Journal

| ID | Requirement | Status |
|---|---|---|
| JRN-01 | Append every event to an NDJSON file: one JSON object per line | Implemented |
| JRN-02 | File path: `data/journal/journal-YYYY-MM-DD-<mode>.ndjson` | Implemented |
| JRN-03 | Logged events: SIGNAL_GENERATED, SIGNAL_REJECTED, ORDER_ENTRY, ORDER_EXIT, TRADE_CLOSED | Implemented |
| JRN-04 | Every event carries: timestamp, type, mode, correlationId | Implemented |
| JRN-05 | Writes are thread-safe (synchronized on writer lock) | Implemented |
| JRN-06 | In-memory list of closed TradeRecords available for same-session analysis queries | Implemented |
| JRN-07 | TRADE_CLOSED event includes: PnL, PnL%, R-multiple, MAE, MFE, hold duration, exit reason | Implemented |

### 2.9 Strategy Analysis

| ID | Requirement | Status |
|---|---|---|
| ANA-01 | Compute overall session metrics: win rate, profit factor, expectancy, Sharpe, max drawdown | Implemented |
| ANA-02 | Break down metrics by strategy and by symbol | Implemented |
| ANA-03 | Track average R-multiple achieved, average win, average loss, average hold time | Implemented |
| ANA-04 | Track max consecutive loss count | Implemented |
| ANA-05 | Generate rule-based textual suggestions when ≥ 10 trades are available | Implemented |
| ANA-06 | Produce equity curve data (running PnL after each trade) | Implemented |
| ANA-07 | Print full report on JVM shutdown if any trades occurred | Implemented |

### 2.10 Health Monitoring

| ID | Requirement | Status |
|---|---|---|
| HLT-01 | Run health checks every 60 seconds | Implemented |
| HLT-02 | Check tick freshness per symbol (stale if last tick > 30 s ago) | Implemented |
| HLT-03 | Check candle worker count vs expected (symbols × timeframes) | Implemented |
| HLT-04 | Check strategy evaluator queue depth (warn if > 500) | Implemented |
| HLT-05 | Check position monitor running state | Implemented |
| HLT-06 | Check JVM heap: WARN at 80%, CRITICAL at 90% | Implemented |
| HLT-07 | Health checks outside 09:15–15:30 IST are suppressed for tick staleness | Partial |

### 2.11 Configuration

| ID | Requirement | Status |
|---|---|---|
| CFG-01 | Load all config from `.env` file via dotenv-java | Implemented |
| CFG-02 | Required keys: FYERS_APP_ID, FYERS_SECRET_KEY, FYERS_REDIRECT_URI, FYERS_AUTH_CODE, FYERS_SYMBOLS, APP_ENV, LOG_LEVEL | Implemented |
| CFG-03 | Fail fast on startup if any required key is missing | Implemented |
| CFG-04 | All risk and strategy parameters must be overridable via `.env` without code change | Implemented |
| CFG-05 | Secrets never logged or committed to source control | Implemented |
| CFG-06 | Hot-reload config at runtime with validation and rollback | Planned |

### 2.12 Backtest Engine

| ID | Requirement | Status |
|---|---|---|
| BKT-01 | Replay historical M5 candles synchronously through the same analysis + strategy pipeline | Implemented |
| BKT-02 | Derive M15 candles (3 × M5) and H1 candles (12 × M5) from the M5 feed | Implemented |
| BKT-03 | Fill entry at next-bar open + slippage | Implemented |
| BKT-04 | Check SL and TP against candle high/low; SL wins if both hit the same bar (conservative) | Implemented |
| BKT-05 | Apply same kill switch and consecutive-loss limits as live mode | Implemented |
| BKT-06 | Produce full StrategyAnalyzer report on completion | Implemented |

---

## 3. Non-Functional Requirements

### 3.1 Performance

| ID | Requirement | Target | Status |
|---|---|---|---|
| NFR-01 | Tick-to-risk latency | < 1 ms | Implemented (Disruptor) |
| NFR-02 | Position monitor cycle time | ≤ 1 s | Implemented |
| NFR-03 | Candle boundary precision | ± 500 ms | Implemented |
| NFR-04 | WebSocket tick ingestion | No blocking — O(1) append under write-lock | Implemented |

### 3.2 Reliability

| ID | Requirement | Status |
|---|---|---|
| NFR-05 | Every broker/API interaction has retry/backoff with bounded attempts (max 3) | Implemented |
| NFR-06 | Every order decision fully auditable: signal → risk → broker request → response | Implemented |
| NFR-07 | System must protect capital above availability — fail closed, not open | Implemented |
| NFR-08 | JVM shutdown hook drains Disruptor ring buffer before exit | Implemented |
| NFR-09 | Duplicate order prevention: symbol-level lock + stable correlationId per fingerprint | Implemented |
| NFR-10 | Position reconciliation on startup (diff in-memory vs broker) | Planned |

### 3.3 Security

| ID | Requirement | Status |
|---|---|---|
| NFR-11 | Raw access token must never appear in logs | Implemented |
| NFR-12 | Credentials stored only in `.env`, never in source files | Implemented |
| NFR-13 | Paper mode must not call any order placement API | Implemented |

### 3.4 Observability

| ID | Requirement | Status |
|---|---|---|
| NFR-14 | Use SLF4J + Logback exclusively — no `System.out` | Implemented |
| NFR-15 | Log levels: INFO routine, WARN retries/degraded, ERROR exceptions, DEBUG full payloads, TRACE latency paths | Implemented |
| NFR-16 | Every strategy evaluation cycle logs: symbol, checks run, pass/fail per gate, final decision | Implemented |
| NFR-17 | NDJSON audit trail for every accepted and rejected signal | Implemented |

### 3.5 Maintainability

| ID | Requirement | Status |
|---|---|---|
| NFR-18 | Maven build only — Gradle not permitted | Implemented |
| NFR-19 | Java 26+ (`maven.compiler.release=26`) | Implemented |
| NFR-20 | All shared mutable state uses `ConcurrentHashMap`, `AtomicBoolean`, `volatile`, or explicit locks | Implemented |
| NFR-21 | I/O-bound tasks run on `Thread.ofVirtual()` virtual threads | Implemented |
| NFR-22 | Periodic jobs use `ScheduledExecutorService` | Implemented |

---

## 4. Risk Parameter Defaults

| Parameter | Default | Config Key |
|---|---|---|
| Max risk per trade | 2% of capital | `RISK_MAX_PER_TRADE_PCT` |
| Max daily loss | 5,000 INR | `RISK_MAX_DAILY_LOSS_INR` |
| Daily profit lock | 15,000 INR | `RISK_MAX_DAILY_PROFIT_INR` |
| Initial capital | 500,000 INR | `RISK_INITIAL_CAPITAL_INR` |
| Max exposure per symbol | 20% | `RISK_MAX_EXPOSURE_PER_SYMBOL_PCT` |
| Max quantity per order | 1,000 | `RISK_MAX_QTY_PER_ORDER` |
| Max consecutive losses per strategy | 3 | `RISK_MAX_CONSECUTIVE_LOSSES` |
| SL ATR multiplier | 2× | `RISK_STOP_LOSS_ATR_MULTIPLIER` |
| TP R-multiple | 2× (=4× ATR) | `RISK_TAKE_PROFIT_R_MULTIPLIER` |
| Trailing activation | 1% unrealized gain | `RISK_TRAIL_ACTIVATION_PCT` |
| Trailing step | 1% of high-water-mark | `RISK_TRAIL_STEP_PCT` |
| Instrument lot size | 1 (equity default) | `RISK_INSTRUMENT_LOT_SIZE` |
| No new trades after | 15:00 IST | `RISK_NO_NEW_TRADES_AFTER` |
| Force square-off | 15:15 IST | `RISK_MARKET_CLOSE_TIME` |
| Exchange timezone | `Asia/Kolkata` | `RISK_EXCHANGE_ZONE` |

---

## 5. Session Time Contract

| Phase | Window (IST) | Activity |
|---|---|---|
| Pre-open | 09:00–09:15 | Config validation, WebSocket connect, indicator seeding |
| Opening | 09:15–10:00 | All strategies active; Volatility Breakout prioritized |
| Mid-day | 10:00–14:30 | Trend Following + Mean Reversion; Breakout suppressed |
| Closing | 14:30–15:00 | Volatility Breakout active again |
| No new entries | 15:00–15:15 | Existing positions only; no new signals |
| Force square-off | 15:15 | All open positions closed by market order |
| Post-market | After 15:15 | Health checks suppressed; journal flushed |

---

## 6. Planned / Not Yet Implemented

| Requirement | Priority |
|---|---|
| Position reconciliation on startup (diff in-memory vs Fyers positions API) | P1 |
| OMS state machine with idempotent client order IDs and full lifecycle states | P1 |
| Token auto-refresh (background refresh before 8-hour expiry) | P2 |
| ~~Kill switch HTTP endpoint~~ | ✅ Implemented — `POST /api/kill` on port 7777 |
| Circuit breaker on API error rate (> 5 errors/min → open circuit for backoff) | P3 |
| Runtime config hot-reload with rollback | P3 |
| Multi-timeframe candle aggregation via second Disruptor consumer chain | P3 |
| Strategy performance tracker with intraday auto-suspension of degraded strategies | P3 |

---

*Last updated: 2026-03-22*
