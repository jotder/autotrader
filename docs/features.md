# Features — Personal Trade Assistant

> **Purpose:** Feature catalog for product tracking and UI development. Each feature maps to implemented backend behaviour and lists the data surfaces a UI needs to display or expose.
>
> **Status legend:** ✅ Implemented | 🔧 Partial | 📋 Planned

---

## F-01 · Multi-Mode Execution

**Status:** ✅ Implemented

Run the same strategy and risk pipeline in three modes selected via `APP_ENV`.

| Mode | Description | Orders |
|---|---|---|
| `backtest` | Synchronous replay of historical M5 candles | Simulated at next-bar open + slippage |
| `paper` | Forward test on live WebSocket data | Simulated at live LTP |
| `live` | Fully autonomous real trading | Real Fyers API market orders |

**Backend entry point:** `TradingEngine.create()` → `resolveMode()` → `createExecutor()`

**UI surfaces:**
- Mode badge (PAPER / LIVE / BACKTEST) persistent in header
- Mode-specific visual treatment (e.g. red border in LIVE)
- Prompt confirmation dialog before switching to LIVE mode

---

## F-02 · Real-Time Tick Ingestion

**Status:** ✅ Implemented

WebSocket connection to Fyers receives ticks for all configured symbols. Each tick is stored in a per-symbol `TickBuffer` (thread-safe `ArrayDeque` with read-write lock). Thread 1 appends; Thread 2 snapshots — no blocking.

**Key classes:** `FyersSocketListener`, `TickStore`, `TickBuffer`

**UI surfaces:**
- Live price ticker per symbol (poll `/api/ticks/latest` every 1–2 s)
- WebSocket connection status indicator (connected / stale / disconnected)
- Last tick age per symbol

---

## F-03 · Multi-Timeframe Candle Aggregation

**Status:** ✅ Implemented

One virtual thread per (symbol × timeframe). Default timeframes: M5, M15, H1. Each thread sleeps until the next candle boundary, snapshots the tick buffer, builds an OHLCV candle, runs analysis, and publishes a `CandleRecommendation`.

**Key classes:** `CandleService`, `CandleAnalyzer`

**UI surfaces:**
- Candlestick chart per symbol (at least M5; optional M15 / H1 overlay toggle)
- Current open candle OHLCV (partial, updated every tick)
- Candle worker health indicator (all N workers alive?)

---

## F-04 · Technical Indicator Computation

**Status:** ✅ Implemented

Computed per candle via ta4j on a rolling `BaseBarSeries` (max 200 bars).

| Indicator | Params | Use |
|---|---|---|
| EMA | 20, 50 | Trend direction gate |
| RSI | 14 | Mean-reversion overbought/oversold |
| ATR | 14 | Stop-loss and position sizing |
| Relative Volume | 20-bar avg | Entry quality filter |

Minimum 21 closed candles required before directional signals are emitted.

**UI surfaces:**
- Indicator overlays on the chart (EMA-20, EMA-50 lines)
- RSI sub-chart (with 30/70 threshold lines)
- ATR value displayed on signal cards
- Relative volume bar chart

---

## F-05 · Multi-Strategy Signal Generation

**Status:** ✅ Implemented

Three strategies evaluated per closed candle:

| Strategy | Trigger | Confidence | Active Window |
|---|---|---|---|
| Trend Following | Strong trend + relVol > 1.2 | 0.85 | All day |
| Mean Reversion | Sideways + RSI extreme | 0.70 | 10:00–14:30 IST only |
| Volatility Breakout | High volatility + relVol > 2.0 | 0.90 | 09:15–10:00, 14:30–15:00 IST only |

Highest-confidence strategy wins when multiple fire on the same symbol.

**Key class:** `CandleAnalyzer`

**UI surfaces:**
- Signal history feed (last N signal candidates with pass/fail per gate)
- Per-strategy color coding (e.g. blue=trend, orange=mean-rev, purple=breakout)
- Strategy selector toggle (enable/disable per strategy without restart — Planned)

---

## F-06 · Multi-Timeframe Compound Filter

**Status:** ✅ Implemented

Before a signal reaches the risk layer it must pass 8 compound gates:

1. M5 recommendation is directional (BUY or SELL)
2. M15 agrees with M5
3. H1 does not actively oppose (HOLD is acceptable)
4. Combined confidence ≥ 0.70 (H1 agreement adds +5%)
5. Current time is before 15:00 IST
6. Symbol is not in 25-minute post-exit cooldown
7. No open position already exists for this symbol
8. (After this, risk gates apply — see F-08)

**Key class:** `StrategyEvaluator`

**UI surfaces:**
- Per-symbol compound signal gate trace (which gates passed / blocked)
- Cooldown countdown timer per symbol
- Timeframe vote display (M5: BUY | M15: BUY | H1: HOLD → PROCEED)

---

## F-07 · Position Sizing

**Status:** ✅ Implemented

Computed inside `RiskManager.preTradeCheck()`:

```
risk_budget    = capital × 2%
risk_per_unit  = |entry - stop_loss|
raw_qty        = floor(risk_budget / risk_per_unit)
lot_aligned    = floor(raw_qty / lot_size) × lot_size
exposure_cap   = floor((capital × 20%) / entry)
final_qty      = min(lot_aligned, 1000, exposure_cap)
```

Rejected if `risk_per_unit ≤ 0` or `final_qty ≤ 0`.

**UI surfaces:**
- Signal card showing: entry, SL, TP, quantity, R-multiple, risk amount in INR
- Capital allocation bar (current exposure vs 20% cap per symbol)

---

## F-08 · Pre-Trade Risk Gates

**Status:** ✅ Implemented

Seven sequential gates evaluated before any order reaches the broker:

| Gate | Condition | Action |
|---|---|---|
| 1 | Kill switch active | Reject |
| 2 | Daily profit locked | Reject |
| 3 | Daily realized PnL ≤ −5,000 INR | Activate kill switch + Reject |
| 4 | Time after 15:00 IST | Reject |
| 5 | Strategy has ≥ 3 consecutive losses | Reject |
| 6 | Symbol exposure ≥ 20% of capital | Reject |
| 7 | Position size calculation (quantity ≤ 0) | Reject |

**Key class:** `RiskManager`

**UI surfaces:**
- Risk dashboard: daily PnL meter, kill-switch status, profit-lock status
- Per-strategy consecutive loss counter with warning at 2/3
- Symbol exposure bar per active symbol
- Kill switch toggle button (activates manually; deactivates only via `resetDay`)

---

## F-09 · Live Order Execution (Fyers API)

**Status:** ✅ Implemented

Market orders placed via `FyersOrderPlacement.placeOrder()`. Side convention: 1=BUY, -1=SELL. Product type: INTRADAY.

Retry policy: up to 3 attempts, exponential backoff (500 / 1000 / 2000 ms). No retry on 4xx except 429.

**Key class:** `LiveOrderExecutor`

**UI surfaces:**
- Order log with timestamp, symbol, side, quantity, fill price, broker order ID
- Order status chip (FILLED / REJECTED / RETRYING)
- Retry count badge on in-flight orders

---

## F-10 · Paper Order Execution

**Status:** ✅ Implemented

Entry fill at current LTP from `TickStore`. Exit fill at trigger price (SL/TP exact). No API calls made.

**Key class:** `PaperOrderExecutor`

**UI surfaces:**
- Same order log as live (clearly labelled PAPER)
- Simulated P&L updated in real-time from live prices

---

## F-11 · Position Monitoring & Exit Triggers

**Status:** ✅ Implemented

Thread 4: scheduled every 1 second. For each open position:
- **Stop-loss hit** → exit order
- **Take-profit hit** → exit order
- **Trailing stop** → activate at +1% PnL; step 1% per additional 1% favourable move (monotonic)
- **Force square-off** → all positions closed at 15:15 IST
- **Manual exit** → `ExitReason.MANUAL` available

**Key class:** `PositionMonitor`

**UI surfaces:**
- Open positions table: symbol, direction, entry price, current price, unrealized PnL (INR + %), SL, TP, trailing stop level
- Colour-coded PnL (green / red)
- Exit reason chip on closed trades (SL / TP / TRAIL / EOD / MANUAL)
- Manual exit button per position (calls kill signal — Planned backend endpoint)

---

## F-12 · Trailing Stop

**Status:** ✅ Implemented

- Activation threshold: unrealized PnL ≥ +1% of entry
- Step: move stop to `hwm × (1 − 1%)` for LONG; `hwm × (1 + 1%)` for SHORT
- Monotonic constraint enforced in `OpenPosition.stepTrailingStop()`
- High-water mark updated each monitoring cycle

**UI surfaces:**
- Trailing stop level shown in the positions table (distinct from initial SL)
- Activation badge on position card
- High-water mark price displayed

---

## F-13 · Force Square-Off

**Status:** ✅ Implemented

At 15:15 IST (`RISK_MARKET_CLOSE_TIME`), `PositionMonitor` issues an `ExitReason.FORCE_SQUAREOFF` for every open position regardless of PnL.

**UI surfaces:**
- Countdown timer to EOD square-off (visible when positions are open after 14:30)
- FORCE_SQUAREOFF badge in trade history

---

## F-14 · Transaction Journal (NDJSON)

**Status:** ✅ Implemented

Append-only file: `data/journal/journal-YYYY-MM-DD-<mode>.ndjson`. Each event is one JSON line. Events: SIGNAL_GENERATED, SIGNAL_REJECTED, ORDER_ENTRY, ORDER_EXIT, TRADE_CLOSED.

**Key class:** `TradeJournal`

**UI surfaces:**
- Audit log viewer: searchable by correlationId, symbol, event type, time range
- Download raw NDJSON button for day's journal
- Signal rejection reasons table with counts (why did risk block entries today?)

---

## F-15 · Trade Record Lifecycle

**Status:** ✅ Implemented

`TradeRecord` tracks the full lifecycle of a trade: entry fill → live excursion updates → exit fill. Sealed fields on close: PnL, PnL%, R-multiple achieved, hold duration, MAE, MFE, exit reason.

**Key class:** `TradeRecord`

**UI surfaces:**
- Closed trade row: entry, exit, PnL, R, hold time, MAE, MFE, strategy, exit reason
- Trade detail modal with full audit trail (signal → risk → fill → exit)

---

## F-16 · Strategy Performance Analysis

**Status:** ✅ Implemented

`StrategyAnalyzer.analyze(trades)` produces a `Report` with:

| Metric | Description |
|---|---|
| Win rate | Winners / total |
| Profit factor | Gross profit / gross loss |
| Expectancy | (Win rate × avg win) − (loss rate × avg loss) |
| Sharpe ratio | Annualised per-trade Sharpe (÷ std dev × √252) |
| Max drawdown | Peak-to-trough as percentage |
| Avg R achieved | Average R-multiple across all trades |
| Avg hold time | Minutes |
| Max consecutive losses | Rolling count |
| Per-strategy breakdown | All metrics grouped by strategyId |
| Per-symbol breakdown | All metrics grouped by symbol |
| Equity curve | Running cumulative PnL after each trade |
| Suggestions | Rule-based actionable improvements |

**Key class:** `StrategyAnalyzer`

**UI surfaces:**
- Performance dashboard: all metrics as cards / KPIs
- Equity curve line chart (cumulative PnL over time)
- Win/loss bar chart (by strategy, by symbol)
- Suggestions panel (bullet list of auto-generated improvements)
- Profit factor gauge, Sharpe gauge
- Drawdown chart (trough visualization)

---

## F-17 · Health Monitoring

**Status:** ✅ Implemented

Thread 5: runs every 60 seconds. Checks:
- Tick freshness per symbol (stale > 30 s → WARN)
- Candle worker count vs expected
- Strategy evaluator queue depth (> 500 → WARN)
- Position monitor alive
- JVM heap (> 80% → WARN; > 90% → CRITICAL)

**Key class:** `HealthMonitor`

**UI surfaces:**
- System health panel: traffic-light per component (GREEN / YELLOW / RED)
- JVM heap gauge (used / max MB)
- WebSocket freshness per symbol
- Queue depth sparkline (last N readings)
- Alert feed (WARN / CRITICAL messages with timestamp)

---

## F-18 · Backtest Engine

**Status:** ✅ Implemented

Synchronous replay of a list of M5 `Candle` objects. Derives M15 (every 3 M5) and H1 (every 12 M5). Runs the same `CandleAnalyzer` → `StrategyEvaluator` → `RiskManager` pipeline. Entry at next-bar open ± slippage; SL/TP checked against candle high/low (conservative: SL wins if both breach same bar). Produces full `StrategyAnalyzer.Report`.

**Key class:** `BacktestEngine`

**UI surfaces:**
- Backtest wizard: symbol selector, date range, slippage input, run button
- Progress indicator during replay
- Full report display (same as F-16 performance dashboard)
- Trade timeline: visualise each backtest trade on price chart

---

## F-19 · Kill Switch

**Status:** ✅ Implemented (manual API) | 📋 HTTP endpoint planned

Manual: `RiskManager.activateKillSwitch(reason)` — blocks all new entries immediately.
Automatic: triggered when daily loss limit is breached.
Reset: `RiskManager.resetDay()` — clears kill switch, profit lock, consecutive-loss counters.

**UI surfaces:**
- Kill switch toggle with confirmation dialog (red button, prominent placement)
- Kill switch reason log
- Day reset button (only available before market open or after square-off)

---

## F-20 · Session Summary Report

**Status:** ✅ Implemented

On JVM shutdown (clean or via shutdown hook), `TradingEngine.analyzeSession()` generates and logs a full `StrategyAnalyzer.Report` if any trades occurred.

**UI surfaces:**
- End-of-day summary modal (auto-shown after 15:30 IST)
- Exportable PDF / JSON report (Planned)

---

## F-21 · Configuration Management

**Status:** ✅ Implemented (file-based) | 📋 Hot-reload planned

All parameters loaded from `.env`. `RiskConfig.fromEnvironment()` and `StrategyConfig.fromEnvironment()` parse and validate with fallback defaults.

**Key class:** `ConfigManager`

**UI surfaces:**
- Settings page: display current effective config values (read-only in MVP)
- Risk parameter editor with validation (requires hot-reload feature to take effect live)
- Symbol manager: add/remove symbols from FYERS_SYMBOLS

---

## F-22 · Multi-Symbol Support

**Status:** ✅ Implemented

All features are keyed by symbol. `FYERS_SYMBOLS` is a comma-separated list. The tick store, candle workers, and position monitor all operate concurrently across symbols.

**UI surfaces:**
- Symbol selector / watchlist panel
- Per-symbol data in all tables and charts
- Symbol filter on order log, trade history, and analysis views

---

## Planned Features (Not Yet Implemented)

| Feature | ID | Priority |
|---|---|---|
| Position reconciliation on startup | F-23 | P1 |
| OMS state machine (full order lifecycle states) | F-24 | P1 |
| Token auto-refresh | F-25 | P2 |
| ~~Emergency kill switch HTTP endpoint~~ | ~~F-26~~ | ✅ Implemented via REST API |
| Circuit breaker on broker API error rate | F-27 | P3 |
| Runtime config hot-reload | F-28 | P3 |
| Strategy auto-suspend tracker (intraday) | F-29 | P3 |
| Notification webhook (WARN/CRITICAL) | F-30 | P2 |
| Walk-forward backtest validation | F-31 | P3 |

---

## REST API (Implemented — Spring Boot on port 7777)

All endpoints are live via `EngineController`. Server starts automatically with `mvn spring-boot:run`.

| Method | Path | Description |
|---|---|---|
| GET | `/api/status` | Engine running state, mode, active symbols |
| GET | `/api/positions` | Open positions with real-time PnL |
| GET | `/api/trades` | Closed trade history |
| GET | `/api/metrics` | Session StrategyAnalyzer report |
| GET | `/api/risk` | Daily PnL, kill switch status, exposure |
| GET | `/api/health` | Component health checks |
| GET | `/api/ticks/{symbol}` | Latest LTP for a symbol |
| POST | `/api/kill?reason=...` | Activate kill switch |
| POST | `/api/reset` | Reset daily risk state |
| POST | `/api/exit/{correlationId}` | Manual exit a position |

**Key class:** `com.rj.web.EngineController`

## UI Architecture Notes

The backend is a Spring Boot server on port 7777. A UI layer communicates via:

2. **WebSocket push** (recommended for live data): push tick updates, position PnL changes, new signals, and health events to the UI without polling

3. **Journal file** (`data/journal/`): UI can read NDJSON directly for historical audit, or the REST API can serve it paginated

---

*Last updated: 2026-03-22*
