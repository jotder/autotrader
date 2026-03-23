# AutoTrader — Architecture & Contracts

Personal multi-asset algo trading system. Java · Fyers API v3 · NSE/BSE/MCX/CDS · Single JVM.

**Status:** Paper trading only until `docs/GO_LIVE_CHECKLIST.md` is 100% cleared.

## Documentation Map

| File | What | Sync rule |
|---|---|---|
| `CLAUDE.md` | Architecture, contracts, coding rules | Update every session |
| `docs/PRD.md` | Requirements with phase tags + status | Update with every feature |
| `docs/FEATURES.md` | Feature catalog + API + UI surfaces | Update with every feature |
| `docs/USER_GUIDE.md` | End-user guide (config, operations, troubleshooting) | Update with every feature |
| `docs/GO_LIVE_CHECKLIST.md` | Production readiness checklist | Update toward go-live |
| `docs/README.md` | Quick-start for humans | Update on major changes |

**Sync rule:** When a feature changes → update `PRD.md` + `FEATURES.md` + `CLAUDE.md` in the same session. Run `/sync-docs` to verify.

---

## Tech Stack (locked)

| Component | Choice | Version |
|---|---|---|
| Language | Java | 25 (`maven.compiler.release=25`) |
| Build | Maven | 3.9+ — no Gradle |
| Web/REST | Spring Boot | 3.4.4 (embedded Tomcat, port 7777) |
| JSON | Jackson | Spring Boot BOM |
| Logging | SLF4J + Logback | Spring Boot BOM |
| HTTP/WS | `java.net.http` | built-in |
| TA | ta4j | 0.15 |
| Tick pipeline | LMAX Disruptor | 3.4.4 |
| Config | dotenv-java + YAML (strategy configs) | 3.1.0 + SnakeYAML |
| Broker SDK | `com.tts.in:fyersjavasdk` | 1.0 (local `repo/`) |
| Tests | JUnit Jupiter | 5.10.2 |

---

## Architecture

```
                         ┌──────────────────────────────────────────────────┐
                         │                  FYERS BROKER                    │
                         │    WebSocket (ticks)    REST (history/orders)    │
                         └─────────┬──────────────────────┬────────────────┘
                                   │                      │
                    ┌──────────────▼──────────┐    ┌──────▼──────────┐
                    │   FyersSocketListener    │    │  FyersDataApi   │
                    │   (virtual thread)       │    │  FyersOrders    │
                    └──────┬──────────┬────────┘    └─────────────────┘
                           │          │
              ┌────────────▼┐    ┌────▼─────────────────┐
              │  TickStore   │    │  LivePriceCache       │
              │  (per-symbol │    │  (latest LTP/symbol)  │
              │   TickBuffer)│    └────┬──────────────────┘
              └──────┬───────┘         │
                     │            ┌────▼──────────────┐
    ┌────────────────▼─┐          │  TickDisruptor     │  4096-slot ring buffer
    │ CandleService     │          │  (lock-free CAS)   │  < 1 ms tick-to-action
    │ (1 vthread/symbol)│          └────┬──────────────┘
    └────────┬──────────┘               │
             │                    ┌─────▼──────────────┐
    ┌────────▼──────────┐         │  RiskManager        │  SL/TP/trail per tick
    │  CandleAnalyzer    │         │  .onEvent()         │  (risk-tick-processor)
    │  EMA/RSI/ATR/VWAP  │         └────┬──────────────┘
    └────────┬──────────┘               │
             │                    ┌─────▼──────────────┐
    ┌────────▼──────────┐         │  OMS                │
    │  StrategyEvaluator │         │  .processExitOrder()│
    │  N strategies      │         └────────────────────┘
    └────────┬──────────┘
             │
    ┌────────▼──────────┐     ┌─────────────────────┐
    │  RiskManager       │────▶│  IOrderExecutor      │
    │  .preTradeCheck()  │     │  Paper│Backtest│Live │
    └────────────────────┘     └──────────┬──────────┘
                                          │
                               ┌──────────▼──────────┐
                               │  TradeJournal        │
                               │  (NDJSON audit log)  │
                               └─────────────────────┘
```

### Data Pipelines

**Entry:** `WS tick → TickStore → CandleService → CandleAnalyzer → StrategyEvaluator → RiskManager.preTradeCheck() → OMS.placeOrder() → TradeJournal`

**Exit (live):** `WS tick → LivePriceCache → TickDisruptor → RiskManager.onEvent() [<1ms] → SL/TP/trail → OMS.processExitOrder()`

**Exit (safety net):** `ScheduledExecutor(10s) → RiskManager.monitorPortfolio() → square-off / SL check`

---

## Package Structure

```
com.rj
├── config/    ConfigManager, RiskConfig, StrategyConfig, IConfiguration
├── engine/    TradingEngine, CandleService, CandleAnalyzer, StrategyEvaluator,
│              RiskManager, PositionMonitor, BacktestEngine, *OrderExecutor,
│              TradeJournal, StrategyAnalyzer, HealthMonitor
└── model/     Tick, TickBuffer, TickStore, Candle, Signal, TradeSignal, TradeRecord,
               OrderEntry, OrderFill, CandleRecommendation, ExecutionMode, Timeframe

fyers/         FyersSocketListener, FyersDataApi, FyersOrderPlacement, FyersOrders,
               FyersPositions, FyersFund, TokenGenerator, FyersBrokerConfig, ...

com.rj.web/           EngineController (REST API — 10 endpoints)
com.rj.web.dto/       StatusResponse, RiskResponse, TickResponse, ActionResponse
```

**Planned additions:**
```
config/strategies/     intraday.yaml, positional.yaml, options.yaml
com.rj.config/         YamlStrategyLoader, ConfigFileWatcher, ConfigValidator
```

---

## Thread Model

| Thread | Type | Latency | Job |
|---|---|---|---|
| `fyers-ws-feed` | Virtual | — | WS → TickStore + TickDisruptor |
| `candle-<SYM>` | Virtual (1/sym) | — | Tick snapshot → OHLCV → analysis → strategy |
| `risk-tick-processor` | Disruptor consumer | **< 1 ms** | SL/TP/trail per tick |
| `risk-scheduler` | ScheduledExecutor | 10s | Safety-net, square-off |
| `data-scheduler` | ScheduledExecutor | 5 min | Historical candle refresh |
| `monitor` | ScheduledExecutor | 60s | Health checks |
| `config-watcher` | Virtual (planned) | — | YAML file change detection |
| `shutdown-hook` | JVM hook | — | Drain Disruptor ring buffer |

**Rules:** Virtual threads for I/O. ScheduledExecutor for periodic. **Never block `risk-tick-processor`.**

---

## Key Contracts

### Time — `Asia/Kolkata` everywhere
- Candle boundaries, risk cutoffs, market windows, weekend checks — all IST
- No `LocalDateTime.now()` without zone. Use `ZonedDateTime.now(IST)`

### Reliability — capital first
- Every broker call: retry 3×, exponential backoff with jitter. Never retry 4xx (except 429)
- Every order decision: audited end-to-end (signal → risk → broker → response)
- Duplicate prevention: symbol-level lock + stable correlationId
- Disruptor: pre-allocated ring buffer, zero allocation on hot path
- **Anomaly response: close all → cash → manual restart required**

### Fyers API v3
- Base: `https://api-t1.fyers.in/api/v3`
- Auth: `SHA-256(appId:secretKey)` → `POST /validate-authcode` → token in memory only
- Header: `Authorization: <AppId>:<AccessToken>`
- Token expires ~8h. Auto-refresh planned. Currently manual `.env` + restart.
- Rate limit: 429 → pause non-critical, backoff, WARN. 3+ hits/day = user blocked
- WS: `LITE`/`FULL` mode. Disconnect → stale → block new signals
- History: `[ts, O, H, L, C, V]` format

### Configuration — layered
- `.env` → global params (secrets, capital, mode)
- `config/strategies/*.yaml` → per-strategy tuning (indicators, thresholds, risk overrides)
- YAML overrides `.env` defaults for strategy-specific params
- Hot-reload via file watcher with validation + rollback

### Persistence — NDJSON
- `data/` directory, created at startup
- Journal: `data/journal/journal-DATE-MODE.ndjson`
- Atomic writes (`.tmp` → rename). 30-day retention

### Notifications
- Telegram (planned P2) for all alerts
- Webhook for current implementation
- Levels: INFO (log), WARN (log + webhook), CRITICAL (log + webhook + Telegram)

---

## Coding Rules

### Do
- SLF4J logging: INFO routine, WARN retry/degraded, ERROR exception, DEBUG payloads, TRACE latency
- Retry/backoff on ALL broker calls (3×, exponential, jitter)
- Stable correlationId: signal → risk → OMS → broker → audit
- `Asia/Kolkata` for ALL time logic
- `Thread.ofVirtual()` for I/O; `ScheduledExecutorService` for periodic
- Audit every signal (approved AND rejected)
- Secrets in `.env` only
- Strategy params in YAML (not hardcoded)
- Make all thresholds configurable

### Don't
- No real Fyers API calls in tests — mock everything
- No Gradle
- No `log4j-over-slf4j` (infinite loop with SDK's `slf4j-log4j12`)
- No raw tokens in logs
- No orders without risk checks
- No hardcoded secrets or magic numbers
- No blocking `risk-tick-processor`
- No partial candles to strategy engine

### New Feature Workflow
1. Design against architecture contracts (this file)
2. Define models in `com.rj.model`
3. Implement decoupled from OMS/Risk
4. Add SLF4J logging + exception handling
5. Write JUnit tests with mocked API responses
6. Make all thresholds configurable (YAML or .env)
7. Update PRD.md + FEATURES.md + CLAUDE.md (module status)

---

## Module Status

| Module | Status | Notes |
|---|---|---|
| Auth | MVP | No auto-refresh yet |
| Market Data (REST + WS) | MVP | Reconnect partial |
| TickStore / TickBuffer | Done | Thread-safe per-symbol |
| Candle Cache | MVP | |
| CandleService | Done | 1 vthread per symbol × timeframe |
| Live Price Cache | Done | Singleton LTP per symbol |
| Tick Pipeline (Disruptor) | Done | < 1 ms tick-to-action |
| CandleAnalyzer (TA) | MVP | EMA/RSI/ATR/RelVol |
| StrategyEvaluator | MVP | 3 strategies, compound filter |
| RiskManager | Done | Kill switch, sizing, Disruptor consumer |
| OMS (Paper + Backtest) | MVP | Retry/backoff, dedup guard |
| LiveOrderExecutor | Stub | Wire FyersOrderPlacement |
| PositionMonitor | Done | SL/TP/trail/square-off |
| TradeJournal | Done | NDJSON append-only |
| TradeRecord | Done | Full lifecycle, MAE/MFE |
| StrategyAnalyzer | Done | Win rate, Sharpe, drawdown |
| BacktestEngine | Done | M5 replay, derives M15/H1 |
| HealthMonitor | Done | WS staleness, heap, API errors |
| Notifications | Done | Webhook (Telegram planned P2) |
| Persistence (NDJSON) | Done | Atomic writes, 30-day retention |
| REST API (Spring Boot) | Done | 11 endpoints on port 7777 |
| Kill Switch HTTP | Done | `POST /api/kill` |
| **YAML Strategy Config** | **Done** | P1 — `YamlStrategyLoader` + `StrategyYamlConfig` + `StrategyRiskConfig` + `StrategyOrderConfig`; `loadWithDefaults()` merges `defaults.yaml`; `RiskManager.applyStrategyRiskOverride()` wired; `ConfigValidator` validates ranges/enums/required fields; `reloadWithRollback()` retains last-valid config; hot-reload planned |
| **Position Reconciler** | **Done** | P1 — startup diff: broker ↔ engine; adopt orphaned, remove stale, verify qty; LIVE mode only; `GET /api/reconciliation` |
| **OMS State Machine** | **Planned** | P1 — idempotent IDs |
| **Token Auto-Refresh** | **Planned** | P1 — background refresh |
| **Anomaly Protection** | **Planned** | P1 — auto close-all |
| **Circuit Breaker** | **Planned** | P1 — API error gating |
| **F&O Support** | **Planned** | P2 — multi-asset orders |
| **Telegram Alerts** | **Planned** | P2 — bot integration |
| **Web UI** | **Planned** | P2 — control center |
| **Data Lake** | **Planned** | P3 — hybrid tick storage |

---

## Claude Code Roles

| Command | Role | Purpose |
|---|---|---|
| `/pm` | Project Manager | Backlog, milestones, sprint planning |
| `/review` | Code Reviewer | Architecture compliance, thread safety, quality |
| `/test-plan` | QA Engineer | Test gaps, case generation, coverage |
| `/security` | Security Analyst | Secrets, input validation, CVEs |
| `/design` | Architect | Feature design against contracts |
| `/deploy-check` | DevOps | Build health, go-live audit, config |
| `/sync-docs` | Tech Writer | Sync PRD + FEATURES + CLAUDE.md |

---

## Operations Runbook

### Pre-market (before 09:10 IST)
Verify: clock sync, `.env` credentials, broker connectivity, token validity, strategy/risk config, disk space.

### Startup verification
Auth OK → historical data → analysis cycles logging → risk active → WS ticks (fresh < 60s) → alerts reachable. **Abort if any fail.**

### Intraday (every 15–30 min)
Feed freshness, API error rate, 429 frequency, risk utilization, order latency, memory/threads.

### Incident playbooks

| Incident | Action |
|---|---|
| Broker/API outage | Halt entries, keep exits, retry with backoff, flatten if persistent |
| Token expiry | Halt engine, square off manually, refresh `.env`, restart |
| WS disconnect | Mark stale, block signals, reconnect with backoff, verify ticks |
| Risk breach | Kill switch ON, reject entries, audit, manual reset required |
| Duplicate order | Freeze symbol, reconcile broker vs internal, resolve, unlock |
| Rapid drawdown | Kill switch, flatten, postmortem |
| **Anomaly detected** | **Auto: close all → cash → require manual restart** |

### End-of-day
Square-off 15:15 → verify no open positions → daily summary → archive logs.

---

*Last updated: 2026-03-23*
