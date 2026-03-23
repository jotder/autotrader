# AutoTrader — User Guide

> Complete guide for configuring, running, and operating the trading system.
> Keep this updated with every feature change.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Installation & Setup](#2-installation--setup)
3. [Configuration](#3-configuration)
4. [Strategy Configuration (YAML)](#4-strategy-configuration-yaml)
5. [Running the System](#5-running-the-system)
6. [Execution Modes](#6-execution-modes)
7. [REST API Reference](#7-rest-api-reference)
8. [Risk Management](#8-risk-management)
9. [Strategy Management](#9-strategy-management)
10. [Monitoring & Health](#10-monitoring--health)
11. [Backtesting](#11-backtesting)
12. [Daily Operations](#12-daily-operations)
13. [Troubleshooting](#13-troubleshooting)
14. [Going Live](#14-going-live)

---

## 1. System Overview

AutoTrader is a personal multi-asset algorithmic trading system for Indian markets. It connects to the Fyers broker API to receive market data and execute trades autonomously.

**What it does:**
- Receives real-time ticks via WebSocket
- Aggregates into OHLCV candles (M1, M5, M15, H1)
- Computes technical indicators (EMA, RSI, ATR, etc.)
- Evaluates multiple strategies per candle close
- Applies risk gates before every order
- Executes trades (paper or live) with full audit trail
- Monitors positions for SL/TP/trailing exits
- Forces square-off at end of day

**Key safety features:**
- Kill switch (manual + automatic on daily loss limit)
- Anomaly auto-protection (close all → cash → manual restart)
- 7 sequential risk gates before every order
- Force square-off at 15:15 IST
- Full NDJSON audit trail for every signal and order

---

## 2. Installation & Setup

### Prerequisites

| Requirement | Version |
|---|---|
| JDK | 25+ |
| Maven | 3.9+ |
| Fyers API app | [myapi.fyers.in](https://myapi.fyers.in/docsv3) |

### Steps

```bash
# Clone
git clone https://github.com/jotder/autotrader.git
cd autotrader

# Configure (see Section 3)
cp .env.example .env

# Build
mvn clean compile

# Test
mvn test

# Run (paper mode)
mvn spring-boot:run
```

The app starts on **port 7777**. Verify at `http://localhost:7777/api/status`.

---

## 3. Configuration

AutoTrader uses a **layered configuration** system:

### Layer 1: `.env` — Global Configuration

Contains secrets, capital settings, mode, and base risk parameters.

```properties
# === Required ===
FYERS_APP_ID=your_app_id
FYERS_SECRET_KEY=your_secret
FYERS_REDIRECT_URI=https://your-redirect-uri
FYERS_AUTH_CODE=your_auth_code
FYERS_SYMBOLS=NSE:SBIN-EQ,NSE:RELIANCE-EQ,NSE:TCS-EQ
APP_ENV=paper                    # backtest | paper | live
LOG_LEVEL=INFO

# === Optional ===
FYERS_PIN=1234
FYERS_WS_MODE=LITE               # LITE or FULL
FYERS_WS_RECONNECT_ATTEMPTS=5
NOTIFICATION_WEBHOOK_URL=https://your-webhook

# === Risk Defaults (overridable per-strategy in YAML) ===
RISK_INITIAL_CAPITAL_INR=2500000
RISK_MAX_PER_TRADE_PCT=2.0
RISK_MAX_DAILY_LOSS_INR=5000
RISK_MAX_DAILY_PROFIT_INR=15000
RISK_MAX_EXPOSURE_PER_SYMBOL_PCT=20.0
RISK_MAX_QTY_PER_ORDER=1000
RISK_MAX_CONSECUTIVE_LOSSES=3
RISK_STOP_LOSS_ATR_MULTIPLIER=2.0
RISK_TAKE_PROFIT_R_MULTIPLIER=2.0
RISK_TRAIL_ACTIVATION_PCT=1.0
RISK_TRAIL_STEP_PCT=1.0
RISK_INSTRUMENT_LOT_SIZE=1
RISK_NO_NEW_TRADES_AFTER=15:00
RISK_MARKET_CLOSE_TIME=15:15
RISK_EXCHANGE_ZONE=Asia/Kolkata
```

### Layer 2: `config/strategies/*.yaml` — Strategy Configuration (Planned)

Per-strategy tuning files grouped by trading style. See [Section 4](#4-strategy-configuration-yaml).

### Priority Order
1. Strategy YAML values override `.env` defaults for strategy-specific params
2. `.env` remains source of truth for global params (capital, mode, secrets)
3. On restart, YAML files are re-read as source of truth

---

## 4. Strategy Configuration (YAML)

> **Status:** Planned for P1. This section describes the target design.

### File Structure

```
config/
└── strategies/
    ├── intraday.yaml       # Day trading strategies
    ├── positional.yaml     # Swing/positional strategies
    └── options.yaml        # F&O strategies (P2)
```

### Schema

```yaml
# config/strategies/intraday.yaml

strategies:

  trend_following:
    enabled: true
    description: "Trend following with EMA crossover + volume filter"
    symbols: ["NSE:SBIN-EQ", "NSE:RELIANCE-EQ"]
    timeframe: M5
    active_hours:
      start: "09:15"
      end: "15:00"
    cooldown_minutes: 25
    max_trades_per_day: 10

    # Indicator parameters
    indicators:
      ema_fast: 20
      ema_slow: 50
      rsi_period: 14
      atr_period: 14
      rel_vol_period: 20
      min_candles: 21

    # Entry conditions
    entry:
      min_confidence: 0.85
      rel_vol_threshold: 1.2
      trend_strength: STRONG_BULLISH

    # Risk parameters (override global .env defaults)
    risk:
      risk_per_trade_pct: 2.0
      sl_atr_multiplier: 2.0
      tp_r_multiple: 2.0
      trailing_activation_pct: 1.0
      trailing_step_pct: 1.0
      max_exposure_pct: 20.0
      max_qty: 1000
      max_consecutive_losses: 3

    # Order parameters
    order:
      type: MARKET              # MARKET | LIMIT
      slippage_tolerance: 0.05  # % for backtest
      product_type: INTRADAY    # INTRADAY | CNC | MARGIN

  mean_reversion:
    enabled: true
    description: "RSI mean reversion in sideways markets"
    symbols: ["NSE:SBIN-EQ", "NSE:RELIANCE-EQ"]
    timeframe: M5
    active_hours:
      start: "10:00"
      end: "14:30"
    cooldown_minutes: 25
    max_trades_per_day: 8

    indicators:
      rsi_period: 14
      atr_period: 14
      rel_vol_period: 20
      min_candles: 21

    entry:
      min_confidence: 0.70
      rsi_oversold: 30
      rsi_overbought: 70
      market_regime: SIDEWAYS

    risk:
      risk_per_trade_pct: 1.5
      sl_atr_multiplier: 1.5
      tp_r_multiple: 1.5
      max_consecutive_losses: 3

    order:
      type: MARKET
      product_type: INTRADAY

  volatility_breakout:
    enabled: true
    description: "High volatility breakout at market open/close"
    symbols: ["NSE:SBIN-EQ", "NSE:NIFTY50-EQ"]
    timeframe: M5
    active_hours:
      # Multiple windows supported
      windows:
        - { start: "09:15", end: "10:00" }
        - { start: "14:30", end: "15:00" }
    cooldown_minutes: 30
    max_trades_per_day: 4

    indicators:
      atr_period: 14
      rel_vol_period: 20
      min_candles: 21

    entry:
      min_confidence: 0.90
      rel_vol_threshold: 2.0

    risk:
      risk_per_trade_pct: 2.0
      sl_atr_multiplier: 2.5
      tp_r_multiple: 3.0

    order:
      type: MARKET
      product_type: INTRADAY
```

### How to Add a New Strategy

1. Open the appropriate YAML file (or create a new one for a new trading style)
2. Add a new strategy block with a unique name
3. Configure all parameters (indicators, entry, risk, order)
4. Set `enabled: true`
5. The system detects the change and loads it (hot-reload) or on next restart

### How to Disable a Strategy

Set `enabled: false` in the YAML — the strategy will stop evaluating on the next config reload cycle. Existing open positions from that strategy will still be monitored and exited normally.

### How to Tune a Strategy

Modify any parameter in the YAML file. Changes are detected and applied:
- **File edit → hot-reload** (file watcher detects change, validates, applies)
- **UI edit → saves to YAML** (web UI writes changes to YAML, applies immediately)
- **Restart** (YAML files re-read on startup)

Invalid configurations are rejected with a WARN log, and the previous valid config continues.

---

## 5. Running the System

### Start

```bash
# Paper mode (default)
mvn spring-boot:run

# Explicit mode
APP_ENV=paper mvn spring-boot:run
APP_ENV=backtest mvn spring-boot:run
APP_ENV=live mvn spring-boot:run     # Only after GO_LIVE_CHECKLIST cleared!
```

### Verify Startup

Check these endpoints after start:

```bash
curl http://localhost:7777/api/status    # Should show mode + active symbols
curl http://localhost:7777/api/health    # All components GREEN
```

### Stop

```bash
# Graceful shutdown (recommended)
curl -X POST http://localhost:7777/actuator/shutdown

# Or Ctrl+C in terminal (triggers shutdown hook)
```

The shutdown hook:
1. Drains the Disruptor ring buffer
2. Generates session summary report
3. Flushes journal to disk

### Auto-Start (Planned)

Configure system startup to launch before market open (09:10 IST). The app handles pre-market initialization automatically.

---

## 6. Execution Modes

### Backtest Mode (`APP_ENV=backtest`)

Replays historical M5 candles synchronously. No API calls. Generates full analytics report.

```bash
# Runs backtest and exits with report
APP_ENV=backtest mvn spring-boot:run
```

**Behaviour:**
- Derives M15 (3×M5) and H1 (12×M5) candles automatically
- Fills at next-bar open ± configurable slippage
- SL/TP checked against candle high/low (conservative: SL wins if both hit)
- Same risk gates as live (kill switch, consecutive losses, etc.)

### Paper Mode (`APP_ENV=paper`)

Forward test on live market data. Fills simulated at live LTP. Full pipeline runs but no orders are sent to the broker.

**Use for:** Validating strategies, testing config changes, burn-in before live.

### Live Mode (`APP_ENV=live`)

Real money. Market orders via Fyers API. **Only enable after completing `docs/GO_LIVE_CHECKLIST.md`.**

---

## 7. REST API Reference

Base URL: `http://localhost:7777`

### Read Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/status` | Engine state: mode, running, active symbols, uptime |
| GET | `/api/positions` | Open positions with live PnL, SL, TP, trailing levels |
| GET | `/api/trades` | Closed trade history with full lifecycle data |
| GET | `/api/metrics` | Session analytics: win rate, Sharpe, drawdown, equity curve |
| GET | `/api/risk` | Daily PnL, kill switch status, exposure per symbol |
| GET | `/api/health` | Component health checks (tick freshness, heap, queue depth) |
| GET | `/api/ticks/{symbol}` | Latest LTP for a specific symbol |

### Action Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/kill?reason=...` | Activate kill switch (blocks all new entries) |
| POST | `/api/reset` | Reset daily risk state (kill switch, profit lock, counters) |
| POST | `/api/exit/{correlationId}` | Manually exit a specific position |

### Example Usage

```bash
# Check system status
curl http://localhost:7777/api/status | jq

# View open positions
curl http://localhost:7777/api/positions | jq

# Emergency kill switch
curl -X POST "http://localhost:7777/api/kill?reason=manual%20intervention"

# Exit a specific position
curl -X POST http://localhost:7777/api/exit/SBIN-BUY-1679900000

# Reset for new day
curl -X POST http://localhost:7777/api/reset
```

---

## 8. Risk Management

### Risk Gates (7 sequential checks before every order)

| # | Gate | What Happens |
|---|---|---|
| 1 | Kill switch active? | Reject — no new entries allowed |
| 2 | Daily profit locked? | Reject — target profit reached |
| 3 | Daily PnL ≤ loss limit? | Activate kill switch + Reject |
| 4 | After time cutoff? | Reject — too close to market close |
| 5 | Strategy consecutive losses ≥ limit? | Reject — strategy suspended for day |
| 6 | Symbol exposure ≥ max %? | Reject — concentration limit |
| 7 | Computed quantity = 0? | Reject — can't size position |

### Position Sizing Formula

```
risk_budget    = capital × risk_per_trade_pct
risk_per_unit  = |entry_price - stop_loss|
raw_qty        = floor(risk_budget / risk_per_unit)
lot_aligned    = floor(raw_qty / lot_size) × lot_size
exposure_cap   = floor((capital × max_exposure_pct) / entry_price)
final_qty      = min(lot_aligned, max_qty, exposure_cap)
```

### Exit Triggers

| Trigger | Description |
|---|---|
| Stop-Loss | Price hits SL level (entry ± ATR multiplier) |
| Take-Profit | Price hits TP level (entry ± R-multiple × risk) |
| Trailing Stop | Activates at +N% gain, steps with price (monotonic) |
| Force Square-Off | 15:15 IST — all positions closed regardless |
| Manual | Via `POST /api/exit/{correlationId}` |
| Anomaly | System detects anomaly → close all → cash |

### Kill Switch

- **Automatic:** Daily loss exceeds limit → all entries blocked
- **Manual:** `POST /api/kill?reason=...` → all entries blocked
- **Reset:** `POST /api/reset` → clears kill switch, profit lock, loss counters
- **Caution:** Only reset before market open or after square-off

---

## 9. Strategy Management

### Current Strategies

| Strategy | Style | Active Window | Key Signal |
|---|---|---|---|
| Trend Following | Momentum | All day | Strong trend + volume > 1.2× |
| Mean Reversion | Counter-trend | 10:00–14:30 | RSI extreme + sideways market |
| Volatility Breakout | Momentum | Opening + closing sessions | High volatility + volume > 2.0× |

### Multi-Timeframe Filter

Every signal must pass a compound filter:
1. **M5** candle generates directional signal
2. **M15** candle agrees with direction
3. **H1** candle does not oppose (HOLD is acceptable)
4. Combined confidence meets threshold (H1 agree = +5% boost)

### Adding New Strategies (Target Design)

1. **Create strategy class** implementing the pluggable strategy interface
2. **Add YAML config block** in the appropriate file
3. **Set `enabled: true`** — strategy loads on next cycle
4. **Backtest first** → paper trade → then enable in live

### Dropping / Deactivating Strategies

Set `enabled: false` in YAML. The strategy stops evaluating, but existing open positions continue to be monitored and will exit normally per their SL/TP/trail rules.

---

## 10. Monitoring & Health

### Health Checks (every 60 seconds)

| Component | GREEN | YELLOW | RED |
|---|---|---|---|
| Tick freshness | < 30s per symbol | 30–60s | > 60s or disconnected |
| Candle workers | All alive | — | Missing workers |
| Queue depth | < 100 | 100–500 | > 500 |
| Position monitor | Running | — | Stopped |
| JVM heap | < 80% | 80–90% | > 90% |

### Monitoring Endpoints

```bash
# Full health report
curl http://localhost:7777/api/health | jq

# Quick risk check
curl http://localhost:7777/api/risk | jq

# Current positions
curl http://localhost:7777/api/positions | jq
```

### Journal Files

All events are logged to NDJSON files in `data/journal/`:

```
data/journal/journal-2026-03-23-paper.ndjson
```

Events logged: SIGNAL_GENERATED, SIGNAL_REJECTED, ORDER_ENTRY, ORDER_EXIT, TRADE_CLOSED

Each event includes: timestamp, type, mode, correlationId, and context-specific data.

---

## 11. Backtesting

### Running a Backtest

```bash
APP_ENV=backtest mvn spring-boot:run
```

### What Happens

1. Historical M5 candles are loaded
2. M15 and H1 candles derived automatically
3. Same pipeline runs: analysis → strategy → risk → execution
4. Conservative fill: SL wins if both SL and TP hit same bar
5. Full report generated on completion

### Reading Results

The report includes:
- Win rate, profit factor, expectancy
- Sharpe ratio (annualized)
- Max drawdown (peak-to-trough %)
- Average R-multiple achieved
- Per-strategy and per-symbol breakdowns
- Equity curve data
- Auto-generated improvement suggestions

---

## 12. Daily Operations

### Pre-Market Checklist (before 09:10 IST)

- [ ] System clock synced
- [ ] `.env` credentials valid (token not expired)
- [ ] Strategy YAML config reviewed
- [ ] Disk space > 1 GB free
- [ ] Network connectivity to Fyers OK

### Market Hours Monitoring (every 15–30 min)

- Check `GET /api/health` — all GREEN
- Check `GET /api/risk` — PnL within limits
- Check `GET /api/positions` — positions reasonable
- Verify Telegram alerts arriving (when configured)

### End of Day (after 15:15 IST)

1. Verify force square-off completed (no open positions)
2. Review `GET /api/metrics` — session performance
3. Check `GET /api/trades` — all trades accounted for
4. Archive journal files if needed
5. Note any anomalies for strategy tuning

### Incident Response

| Situation | Action |
|---|---|
| Token expired | Stop engine → refresh `.env` → restart |
| WebSocket disconnected | System auto-blocks signals. Wait for reconnect or restart |
| Kill switch triggered | Review reason. If loss limit: done for day. If manual: resolve and reset |
| Anomaly detected | System auto-closes all. Investigate before manual restart |
| No ticks received | Check Fyers status page. Restart if needed |

---

## 13. Troubleshooting

### App won't start

```bash
# Check Java version
java -version   # Must be 25+

# Check config
cat .env        # Verify all required keys present

# Check port
netstat -an | grep 7777   # Port must be free
```

### No ticks received

1. Check `GET /api/health` — tick freshness
2. Verify FYERS_SYMBOLS format: `NSE:SBIN-EQ,NSE:RELIANCE-EQ`
3. Check if market is open (09:15–15:30 IST, weekdays)
4. Verify auth token is valid

### Trades not executing

1. Check `GET /api/risk` — is kill switch ON?
2. Check journal for SIGNAL_REJECTED events
3. Verify strategy is `enabled: true` in YAML
4. Check if consecutive loss limit reached
5. Confirm time is within strategy active hours

### High memory usage

1. Check `GET /api/health` — JVM heap %
2. Reduce number of symbols in FYERS_SYMBOLS
3. Check if tick buffers are being pruned properly
4. Restart with increased heap: `MAVEN_OPTS="-Xmx2g" mvn spring-boot:run`

---

## 14. Going Live

### Prerequisites

1. Complete **every item** in `docs/GO_LIVE_CHECKLIST.md`
2. Run `/deploy-check` for automated audit
3. Minimum 10 full paper trading sessions completed
4. Paper results align with backtest expectations

### Go-Live Steps

1. Set `APP_ENV=live` in `.env`
2. Start with **reduced position size** (50% of target max_qty)
3. Monitor every trade manually for first 2 weeks
4. Gradually increase to full position sizes
5. Set up daily/weekly review cadence

### Emergency Procedures

```bash
# Emergency: kill all entries immediately
curl -X POST "http://localhost:7777/api/kill?reason=emergency"

# Emergency: exit specific position
curl -X POST http://localhost:7777/api/exit/{correlationId}

# Nuclear option: stop the entire system
# Ctrl+C in terminal (shutdown hook will drain and close gracefully)
```

---

*Last updated: 2026-03-23*
