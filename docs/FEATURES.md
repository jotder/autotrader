# Features — AutoTrader

> **Purpose:** Feature catalog for product tracking and development. Each feature maps to PRD requirements, lists backend classes, REST/WS APIs, and UI surfaces.
>
> **Status legend:** ✅ Done · 🔧 Partial · 📋 Planned
>
> **Phase legend:** P1 (Core Engine) · P2 (Multi-Asset + UI) · P3 (Intelligence) · P4 (Scale)

---

## Implemented Features (P1 — Core Engine)

### F-01 · Multi-Mode Execution ✅

Run the same pipeline in three modes via `APP_ENV`.

| Mode | Orders | API Calls |
|---|---|---|
| `backtest` | Simulated at next-bar open + slippage | None |
| `paper` (default) | Simulated at live LTP | WebSocket only |
| `live` | Real Fyers market orders | Full API |

**PRD refs:** MKT-01, ORD-01–08 · **Classes:** `TradingEngine`, `*OrderExecutor`

**REST API:** `GET /api/status` — returns mode, running state, active symbols

**UI surfaces:** Mode badge in header · Red border in LIVE · Confirmation dialog before LIVE switch

---

### F-02 · Real-Time Tick Ingestion ✅

WebSocket → per-symbol `TickBuffer` (thread-safe `ArrayDeque` with RW lock). Zero-blocking concurrent append + snapshot.

**PRD refs:** MKT-01–04, MKT-07 · **Classes:** `FyersSocketListener`, `TickStore`, `TickBuffer`

**REST API:** `GET /api/ticks/{symbol}` — latest LTP

**UI surfaces:** Live price ticker · WS connection status indicator · Last tick age per symbol

---

### F-03 · Multi-Timeframe Candle Aggregation ✅

One virtual thread per (symbol × timeframe). Sleeps until boundary, snapshots ticks, builds OHLCV, publishes `CandleRecommendation`.

**PRD refs:** CND-01–06 · **Classes:** `CandleService`, `CandleAnalyzer`

**UI surfaces:** Candle data tables per symbol · Worker health indicator

---

### F-04 · Technical Indicator Computation ✅

Rolling `BaseBarSeries` (max 200 bars) via ta4j. Minimum 21 candles before signals.

| Indicator | Period | Use |
|---|---|---|
| EMA | 20, 50 | Trend direction |
| RSI | 14 | Mean-reversion |
| ATR | 14 | SL sizing |
| Relative Volume | 20-bar avg | Entry quality |

**PRD refs:** TA-01–06 · **Classes:** `CandleAnalyzer`

**UI surfaces:** Indicator value table · Trend classification badge

---

### F-05 · Multi-Strategy Signal Generation ✅

Three strategies per candle close. Highest confidence wins on same symbol.

| Strategy | Trigger | Confidence | Window |
|---|---|---|---|
| Trend Following | Strong trend + relVol > 1.2 | 0.85 | All day |
| Mean Reversion | Sideways + RSI extreme | 0.70 | 10:00–14:30 |
| Volatility Breakout | High vol + relVol > 2.0 | 0.90 | 09:15–10:00, 14:30–15:00 |

**PRD refs:** STR-01–07, STR-11 · **Classes:** `CandleAnalyzer`, `StrategyEvaluator`

**UI surfaces:** Signal history table (pass/fail per gate) · Per-strategy color coding

---

### F-06 · Multi-Timeframe Compound Filter ✅

8 sequential gates before signal reaches risk layer:

1. M5 is directional (BUY/SELL)
2. M15 agrees
3. H1 not opposing (HOLD OK)
4. Combined confidence ≥ 0.70 (H1 agree → +5%)
5. Before 15:00 IST
6. Not in cooldown
7. No existing position for symbol
8. → Risk gates (F-08)

**PRD refs:** STR-02–06 · **Classes:** `StrategyEvaluator`

**UI surfaces:** Gate trace table per signal · Cooldown timer · Timeframe vote display

---

### F-07 · Position Sizing ✅

`risk_budget / risk_per_unit` → lot-aligned → exposure-capped → fat-finger-capped.

**PRD refs:** RSK-08–10 · **Classes:** `RiskManager`

**UI surfaces:** Signal card (entry, SL, TP, qty, R, risk INR) · Exposure bar per symbol

---

### F-08 · Pre-Trade Risk Gates ✅

7 sequential gates before broker:

| # | Gate | Action |
|---|---|---|
| 1 | Kill switch active | Reject |
| 2 | Daily profit locked | Reject |
| 3 | Daily PnL ≤ −₹5K | Kill switch ON + Reject |
| 4 | After 15:00 IST | Reject |
| 5 | Strategy ≥ 3 consecutive losses | Reject |
| 6 | Symbol exposure ≥ 20% | Reject |
| 7 | Quantity = 0 | Reject |

**PRD refs:** RSK-01–13 · **Classes:** `RiskManager`

**REST API:** `GET /api/risk` — PnL, kill switch, exposure · `POST /api/kill` · `POST /api/reset`

**UI surfaces:** Risk dashboard · Kill switch toggle · Consecutive loss counter · Exposure bars

---

### F-09 · Live Order Execution ✅

Market orders via `FyersOrderPlacement`. Retry 3×, backoff 500/1K/2K ms. No retry on 4xx (except 429).

**PRD refs:** ORD-03–08 · **Classes:** `LiveOrderExecutor`

**UI surfaces:** Order log (timestamp, symbol, side, qty, fill, broker ID) · Status chip · Retry badge

---

### F-10 · Paper Order Execution ✅

Fill at LTP (entry) / trigger price (exit). Zero API calls.

**PRD refs:** ORD-01 · **Classes:** `PaperOrderExecutor`

**UI surfaces:** Same order log as live (labelled PAPER) · Simulated PnL

---

### F-11 · Position Monitor & Exit Triggers ✅

Every 1 second: SL, TP, trailing stop, force square-off (15:15), manual exit.

**PRD refs:** POS-01–08, POS-10 · **Classes:** `PositionMonitor`

**REST API:** `GET /api/positions` · `POST /api/exit/{correlationId}`

**UI surfaces:** Open positions table (live PnL, SL, TP, trail) · Exit reason chip · Manual exit button

---

### F-12 · Trailing Stop ✅

Activate at +1% unrealized gain. Step: 1% of HWM. Monotonic. High-water mark updated each cycle.

**PRD refs:** POS-05–06 · **Classes:** `OpenPosition`

**UI surfaces:** Trail level in position table · Activation badge · HWM price

---

### F-13 · Force Square-Off ✅

15:15 IST → `ExitReason.FORCE_SQUAREOFF` for all open positions.

**PRD refs:** POS-04 · **Classes:** `PositionMonitor`

**UI surfaces:** EOD countdown timer · FORCE_SQUAREOFF badge in trade history

---

### F-14 · Transaction Journal (NDJSON) ✅

Append-only `data/journal/journal-YYYY-MM-DD-<mode>.ndjson`. Events: SIGNAL_GENERATED, SIGNAL_REJECTED, ORDER_ENTRY, ORDER_EXIT, TRADE_CLOSED.

**PRD refs:** JRN-01–07 · **Classes:** `TradeJournal`

**UI surfaces:** Audit log viewer · Download NDJSON · Rejection reasons table

---

### F-15 · Trade Record Lifecycle ✅

Entry fill → excursion updates → exit fill. Sealed on close: PnL, PnL%, R-multiple, hold time, MAE, MFE, exit reason.

**PRD refs:** JRN-06–07 · **Classes:** `TradeRecord`

**REST API:** `GET /api/trades`

**UI surfaces:** Closed trades table · Trade detail modal with full audit trail

---

### F-16 · Performance Analytics ✅

Win rate, profit factor, expectancy, Sharpe, max drawdown, avg R, avg hold time, per-strategy/symbol breakdown, equity curve, suggestions.

**PRD refs:** ANA-01–07 · **Classes:** `StrategyAnalyzer`

**REST API:** `GET /api/metrics`

**UI surfaces:** KPI cards · Equity curve · Win/loss chart · Suggestions panel · Sharpe/PF gauges

---

### F-17 · Health Monitoring ✅

Every 60s: tick freshness, candle workers, queue depth, position monitor, JVM heap.

**PRD refs:** HLT-01–06 · **Classes:** `HealthMonitor`

**REST API:** `GET /api/health`

**UI surfaces:** Traffic-light panel · JVM heap gauge · Alert feed

---

### F-18 · Backtest Engine ✅

M5 replay → derives M15/H1 → same pipeline → SL/TP on candle high/low → full report.

**PRD refs:** BKT-01–06 · **Classes:** `BacktestEngine`

**UI surfaces:** Backtest wizard · Progress bar · Full report display · Trade timeline

---

### F-19 · Kill Switch ✅

Manual (`POST /api/kill`) + automatic (daily loss breach). Reset via `POST /api/reset`.

**PRD refs:** RSK-01–03, RSK-13 · **Classes:** `RiskManager`

**UI surfaces:** Red kill switch toggle · Reason log · Day reset button

---

### F-20 · Session Summary ✅

On shutdown, `TradingEngine.analyzeSession()` generates full report if trades occurred.

**PRD refs:** ANA-07 · **Classes:** `TradingEngine`, `StrategyAnalyzer`

**UI surfaces:** EOD summary modal · Exportable report (planned)

---

### F-21 · Configuration Management ✅ (.env) | ✅ YAML loader | ✅ Per-strategy risk/order | ✅ Validation + rollback | ✅ Hot-reload

`.env` for global config. YAML for strategy params (`YamlStrategyLoader` + `StrategyYamlConfig`). Per-strategy risk overrides via `StrategyRiskConfig` record; order config via `StrategyOrderConfig` record. `loadWithDefaults()` merges `defaults.yaml` as fallback. `RiskManager.applyStrategyRiskOverride()` applies YAML values at runtime. `ConfigValidator` validates all required fields, numeric ranges, and enum values; `YamlStrategyLoader.reloadWithRollback()` retains last-valid config on failure. `ConfigFileWatcher` monitors `config/strategies/` via WatchService on a virtual thread; debounce 500ms; on valid change applies `StrategyRiskConfig` overrides to RiskManager; wired into TradingEngine start/stop lifecycle.

**PRD refs:** CFG-01–07, CFG-Y01–Y09 · **Classes:** `ConfigManager`, `RiskConfig`, `StrategyConfig`, `YamlStrategyLoader`, `StrategyYamlConfig`, `StrategyRiskConfig`, `StrategyOrderConfig`, `ConfigValidator`, `ConfigFileWatcher`

**UI surfaces:** Settings page (read-only MVP) · Strategy config editor (P2) · Symbol manager

---

### F-22 · Multi-Symbol Support ✅

All features keyed by symbol. `FYERS_SYMBOLS` comma-separated. Concurrent across symbols.

**UI surfaces:** Symbol selector / watchlist · Per-symbol filter on all views

---

## Planned Features

### P1 — Core Engine (Month 1–2)

| ID | Feature | PRD Refs | Priority |
|---|---|---|---|
| F-23 | Position reconciliation on startup | POS-09, NFR-11 | ✅ Done |
| F-24 | OMS state machine (idempotent client order IDs) | ORD-09 | P1-Critical |
| F-25 | Token auto-refresh (background, before 8h expiry) | CFG-07 | P1-High |
| F-26 | YAML strategy configuration system | CFG-Y01–Y09 | P1-High |
| F-27 | Pluggable strategy interface | STR-10 | P1-High |
| F-28 | Anomaly auto-protection (close all → cash → manual restart) | RSK-14, RSK-16 | P1-Critical |
| F-29 | Circuit breaker on API error rate | NFR-12 | P1-Medium |
| F-30 | API health tracking (error rate, latency, rate-limits) | HLT-08 | P1-Medium |

### P2 — Multi-Asset + UI (Month 3–4)

| ID | Feature | PRD Refs | Priority |
|---|---|---|---|
| F-31 | F&O support (futures + options orders) | MKT-09–10, ORD-11, STR-12 | P2-High |
| F-32 | Multi-leg F&O strategies | STR-12 | P2-Medium |
| F-33 | Options Greeks computation | TA-09–10 | P2-Medium |
| F-34 | Telegram bot integration | NTF-01–04 | P2-High |
| F-35 | Web UI control center (data tables, risk, positions) | UI-01–08 | P2-High |
| F-36 | UI strategy config editor (YAML read/write) | UI-04, CFG-Y08 | P2-Medium |
| F-37 | F&O margin awareness | RSK-17–18 | P2-Medium |

### P3 — Intelligence (Month 5–6)

| ID | Feature | PRD Refs | Priority |
|---|---|---|---|
| F-38 | Data lake (tick capture + hybrid storage) | DL-01–05 | P3-High |
| F-39 | Advanced analytics (drawdown curves, time heat maps) | ANA-08–10 | P3-Medium |
| F-40 | Walk-forward optimization | BKT-07 | P3-Medium |
| F-41 | Extended indicators (Bollinger, MACD, VWAP, SuperTrend) | TA-08 | P3-Medium |
| F-42 | Backtest UI wizard | UI-09 | P3-Low |
| F-43 | Rich journal (market context at decision time) | JRN-08 | P3-Low |

### P4 — Scale (Month 7+)

| ID | Feature | PRD Refs | Priority |
|---|---|---|---|
| F-44 | Dynamic capital allocation (Kelly/risk-parity) | CAP-01–04 | P4-High |
| F-45 | Continuous validation pipeline | VAL-01–04 | P4-High |
| F-46 | Strategy drift detection | ANA-11 | P4-Medium |
| F-47 | Quant/statistical strategies | STR-14 | P4-Medium |
| F-48 | Monte Carlo simulation | BKT-08 | P4-Low |

---

## REST API (Port 7777)

All endpoints live via `EngineController`. Server starts with `mvn spring-boot:run`.

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/api/status` | Engine state, mode, symbols | — |
| GET | `/api/positions` | Open positions with live PnL | — |
| GET | `/api/trades` | Closed trade history | — |
| GET | `/api/metrics` | Session analytics report | — |
| GET | `/api/risk` | Daily PnL, kill switch, exposure | — |
| GET | `/api/health` | Component health checks | — |
| GET | `/api/ticks/{symbol}` | Latest LTP | — |
| POST | `/api/kill?reason=...` | Activate kill switch | — |
| POST | `/api/reset` | Reset daily risk state | — |
| POST | `/api/exit/{correlationId}` | Manual exit a position | — |
| GET | `/api/reconciliation` | Last startup reconciliation result | — |

**Planned API additions (P2):**
- `GET /api/strategies` — list strategies with config + status
- `PUT /api/strategies/{id}/config` — update strategy YAML at runtime
- `POST /api/strategies/{id}/toggle` — enable/disable strategy
- `GET /api/journal?from=&to=&symbol=` — paginated journal query
- `WebSocket /ws/live` — real-time push (positions, ticks, signals, health)

---

*Last updated: 2026-03-23*
