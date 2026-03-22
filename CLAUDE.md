# Fyers Algo Trading System

Personal algo trading system in Java. Fyers API v3 for NSE/BSE. Single JVM process.

**Status:** Paper trading only until `docs/GO_LIVE_CHECKLIST.md` is 100% cleared.

## Documentation Map

| File | What | Who maintains |
|---|---|---|
| `CLAUDE.md` | Architecture, contracts, coding rules | Updated every session |
| `docs/requirements.md` | Functional + non-functional requirements with status | Updated with every feature |
| `docs/features.md` | Feature catalog + UI surface specs | Updated with every feature |
| `docs/GO_LIVE_CHECKLIST.md` | Production readiness checklist | Updated toward go-live |
| `docs/README.md` | Quick-start for humans | Updated on major changes |

**Sync rule:** When a feature changes, update `requirements.md` + `features.md` + `CLAUDE.md` in the same session. Run `/sync-docs` to verify.

---

## Tech Stack (locked)

| Component | Choice | Version |
|---|---|---|
| Language | Java | 25 (`maven.compiler.release=25`) |
| Build | Maven | 3.9+ — no Gradle |
| Web/REST | Spring Boot | 3.4.4 (embedded Tomcat on port 7777) |
| JSON | Jackson | managed by Spring Boot BOM |
| Logging | SLF4J + Logback | managed by Spring Boot BOM |
| HTTP/WS | `java.net.http` | built-in — no OkHttp |
| TA | ta4j | 0.15 |
| Tick pipeline | LMAX Disruptor | 3.4.4 |
| Config | dotenv-java | 3.1.0 |
| Broker SDK | `com.tts.in:fyersjavasdk` | 1.0 (local repo at `repo/`) |
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
    │  3 strategies      │         └────────────────────┘
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

### Data Flow (3 pipelines)

**Entry:** `WS tick → TickStore → CandleService → CandleAnalyzer → StrategyEvaluator → RiskManager.preTradeCheck() → OMS.placeOrder() → TradeJournal`

**Exit (live):** `WS tick → LivePriceCache → TickDisruptor → RiskManager.onEvent() [<1ms] → SL/TP/trail → OMS.processExitOrder()`

**Exit (safety net):** `ScheduledExecutor(10s) → RiskManager.monitorPortfolio() → square-off / SL check via LivePriceCache`

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

---

## Thread Model

| Thread | Type | Latency budget | Job |
|---|---|---|---|
| `fyers-ws-feed` | Virtual | — | WS receive → TickStore + TickDisruptor |
| `candle-<SYM>` | Virtual (1/symbol) | — | Tick snapshot → OHLCV → analysis → strategy |
| `risk-tick-processor` | Disruptor consumer | **< 1 ms** | SL/TP/trail per tick |
| `risk-scheduler` | ScheduledExecutor | 10 s | Safety-net monitor, square-off |
| `data-scheduler` | ScheduledExecutor | 5 min | Historical candle refresh |
| `monitor` | ScheduledExecutor | 60 s | Health checks |
| `shutdown-hook` | JVM hook | — | Drain Disruptor ring buffer |

**Rules:** Virtual threads for I/O. ScheduledExecutor for periodic. **Never block `risk-tick-processor`.**

---

## Disruptor Ring Buffer

Ring size `4096` (power-of-2, ~80 KB, L3-friendly). `SINGLE` producer. `SleepingWaitStrategy`. Paper mode: `publish()` is a no-op. Shutdown hook drains before exit.

---

## Key Contracts

### Time — `Asia/Kolkata` everywhere
- Candle boundaries, risk cutoffs, market windows, weekend checks — all IST.
- No `LocalDateTime.now()` without zone. Use `ZonedDateTime.now(IST)`.

### Reliability — capital first
- Every broker call: retry 3x, exponential backoff with jitter. Never retry 4xx (except 429).
- Every order decision: audited end-to-end (signal → risk → broker → response).
- Duplicate prevention: symbol-level lock + stable correlationId per signal fingerprint.
- Disruptor: pre-allocated ring buffer, zero allocation on hot path.

### Fyers API v3
- Base: `https://api-t1.fyers.in/api/v3`
- Auth: `SHA-256(appId:secretKey)` → `POST /validate-authcode` → token in memory only.
- Header: `Authorization: <AppId>:<AccessToken>`
- Token expires ~8h. **No auto-refresh yet** — manual `.env` update + restart.
- Rate limit: on 429 → pause non-critical, backoff, WARN. 3+ rate-limit hits/day = user blocked.
- WS: `LITE`/`FULL` mode. On disconnect → stale context → block new signals.
- History: `[ts, O, H, L, C, V]` format. Fallback generates synthetic candles when auth is invalid — **must disable before live**.

### Persistence — NDJSON
- `data/` directory, created at startup. One `.ndjson` per record type per day.
- Atomic writes (`.tmp` → rename). 30-day retention. Journal at `data/journal/journal-DATE-MODE.ndjson`.

### Notifications
- `INFO`: log only. `WARN`: log + optional webhook. `CRITICAL`: log + webhook (required).
- Config: `NOTIFICATION_WEBHOOK_URL`.

---

## Startup Sequence

```
 1. ConfigManager.loadConfiguration()        — fail fast on missing keys
 2. PersistenceManager.getInstance()         — create data/ dirs
 3. AuthModuleImpl.login()                   — acquire token
 4. OrderManager.setAuth()                   — inject token
 5. PositionReconciler.reconcile()           — diff memory vs broker (planned)
 6. LiveDataManager.start()                  — WS connect + data scheduler
 7. TradeManager + RiskManager wired
 8. TickDisruptor.start(riskManager)         — ring buffer BEFORE risk scheduler
 9. RiskManager.start()                      — 10s safety-net scheduler
10. JVM shutdown hook                        — TickDisruptor.shutdown()
11. Monitor scheduler                        — 1-min health checks
12. Startup health log                       — abort if any module fails
```

---

## Configuration

**Required:** `FYERS_APP_ID`, `FYERS_SECRET_KEY`, `FYERS_REDIRECT_URI`, `FYERS_AUTH_CODE`, `FYERS_SYMBOLS`, `APP_ENV` (backtest|paper|live), `LOG_LEVEL`

**Optional:** `FYERS_PIN`, `NOTIFICATION_WEBHOOK_URL`, `FYERS_WS_MODE` (LITE/FULL), `FYERS_WS_RECONNECT_ATTEMPTS`, `FYERS_WS_SUBSCRIBE_DEPTH`, `FYERS_HISTORY_RESOLUTION` (5/15/60/D), `FYERS_HISTORY_LOOKBACK_DAYS`, `STRATEGY_COOLDOWN_CANDLES`

**Risk params:** All overridable via `.env` — see `docs/requirements.md` Section 4 for full defaults table.

---

## Coding Rules

### Do
- SLF4J logging: `INFO` routine, `WARN` retry/degraded, `ERROR` exception, `DEBUG` payloads, `TRACE` latency
- Retry/backoff on ALL broker calls (3x, exponential, jitter)
- Stable correlationId: signal → risk → OMS → broker → audit
- `Asia/Kolkata` for ALL time logic
- `Thread.ofVirtual()` for I/O; `ScheduledExecutorService` for periodic
- Audit every signal (approved AND rejected)
- Secrets in `.env` only

### Don't
- No real Fyers API calls in tests — mock everything
- No Gradle
- No `log4j-over-slf4j` (infinite loop with SDK's `slf4j-log4j12`)
- No raw tokens in logs
- No orders without risk checks
- No hardcoded secrets
- No blocking `risk-tick-processor`
- No partial candles to strategy engine

### New Feature Workflow
1. Design against architecture contracts (this file)
2. Define models in `com.rj.model`
3. Implement decoupled from OMS/Risk
4. Add SLF4J logging + exception handling
5. Write JUnit tests with mocked API responses
6. Update module status + `requirements.md` + `features.md`

---

## Module Status

| Module | Status | Notes |
|---|---|---|
| Auth | MVP | No auto-refresh |
| Market Data (REST + WS) | MVP | |
| TickStore / TickBuffer | Done | Thread-safe per-symbol ring |
| Candle Cache | MVP | |
| CandleService (aggregation) | Done | 1 vthread per symbol x timeframe |
| Live Price Cache | Done | Singleton LTP per symbol |
| Tick Pipeline (Disruptor) | Done | < 1 ms tick-to-action |
| CandleAnalyzer (TA) | MVP | EMA/RSI/ATR/VWAP/RelVol |
| StrategyEvaluator | MVP | 3 strategies, multi-TF compound filter |
| RiskManager | Done | Kill switches, sizing, Disruptor consumer |
| OMS (Paper + Backtest) | MVP | Retry/backoff, duplicate guard |
| LiveOrderExecutor | Stub | Wire FyersOrderPlacement |
| PositionMonitor | Done | SL/TP/trail/square-off |
| TradeJournal | Done | NDJSON append-only |
| TradeRecord | Done | Full lifecycle, MAE/MFE |
| StrategyAnalyzer | Done | Win rate, Sharpe, drawdown, per-strategy |
| BacktestEngine | Done | M5 replay, derives M15/H1 |
| HealthMonitor | Done | WS staleness, heap, API errors |
| Notifications | Done | INFO/WARN/CRITICAL + webhook |
| Persistence (NDJSON) | Done | Atomic writes, 30-day retention |
| Position Reconciler | **Planned** | Next milestone |
| OMS State Machine | **Planned** | Idempotent clientOrderId |
| Token Auto-Refresh | **Planned** | |
| REST API (Spring Boot) | Done | 10 endpoints on port 7777 |
| Kill Switch HTTP | Done | `POST localhost:7777/api/kill` |
| Circuit Breaker | **Planned** | API error rate gating |
| Config Hot Reload | **Planned** | Runtime refresh + rollback |
| Strategy Tracker | **Planned** | Auto-suspend degraded strategies |

---

## Operations Runbook

### Pre-market (before 09:10 IST)
Verify: clock sync, `.env` credentials, broker connectivity, token validity, strategy/risk config, disk space.

### Startup verification
Auth OK → historical data fetched → analysis cycles logging → risk module active → WS receiving ticks (fresh within 60s) → alerts reachable. **Abort if any fail.**

### Intraday (every 15-30 min)
Feed freshness, API error rate, 429 frequency, risk utilization, order latency, memory/threads.

### Incident playbooks

| Incident | Action |
|---|---|
| Broker/API outage | Halt entries, keep exits, retry with backoff, flatten if persistent |
| Token expiry | Halt engine, square off manually if needed, refresh `.env`, restart |
| WS disconnect | Mark stale, block signals, reconnect with backoff, verify ticks |
| Risk breach | Kill switch ON, reject entries, audit, manual reset required |
| Duplicate order | Freeze symbol, reconcile broker vs internal, resolve, unlock |
| Rapid drawdown | Kill switch, flatten, collect evidence for postmortem |

### End-of-day
Square-off at 15:15 → verify no open positions → persist daily summary → archive logs.

### Post-incident
Timeline → root cause → financial impact → corrective actions → tests added.

---

## Claude Code Commands

| Command | Role | Purpose |
|---|---|---|
| `/pm` | Project Manager | Backlog, milestones, sprint planning |
| `/review` | Code Reviewer | Architecture compliance, thread safety, quality |
| `/test-plan` | QA Engineer | Test gaps, case generation, coverage |
| `/security` | Security Analyst | Secrets, input validation, CVEs |
| `/design` | Architect | Feature design against contracts |
| `/deploy-check` | DevOps | Build health, go-live audit, config |
| `/sync-docs` | Tech Writer | Sync requirements + features + CLAUDE.md |

**Autonomy:** Plan first for non-trivial changes. Use `EnterPlanMode` for features and multi-file edits.

---

## Web UI (Planned)

Web dashboard consuming backend services. Technology TBD. Design rules:
- Return DTOs suitable for JSON serialization from service methods
- Keep engine internals behind clean service interfaces
- `docs/features.md` catalogs all UI surfaces and REST/WS API specs
