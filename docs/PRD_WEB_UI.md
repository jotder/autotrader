# PRD: AutoTrader Web UI — Control Center

**Version:** 1.0
**Author:** Claude Code + RJ
**Date:** 2026-03-25
**Status:** Draft

---

## 1. Problem Statement

The AutoTrader system exposes 19 REST endpoints but has no visual interface. The operator (solo trader) must use curl/Postman to monitor status, check positions, run backtests, and manage risk. This creates friction during market hours when quick decisions matter and increases the chance of missed anomalies.

## 2. Vision

A single-page dark-themed control center that supports the **complete strategy lifecycle** — design → backtest → paper trade → go-live → observe → retire — with per-symbol execution mode, unified trade journal, and cross-mode performance comparison.

## 2.1 User Workflow — Strategy Lifecycle

```
① DESIGN           ② BACKTEST          ③ PAPER TRADE       ④ GO LIVE           ⑤ OBSERVE & ADAPT
┌──────────┐      ┌──────────┐        ┌──────────┐       ┌──────────┐        ┌──────────┐
│Configure │─────▶│Run on    │───────▶│Run with  │──────▶│Real      │───────▶│Compare   │
│strategy  │      │historical│        │simulated │       │orders    │        │BT vs PT  │
│params    │      │data      │        │fills     │       │per-symbol│        │vs Live   │
└──────────┘      └──────────┘        └──────────┘       └──────────┘        └──────────┘
     │                 │                    │                  │                    │
     ▼                 ▼                    ▼                  ▼                    ▼
 [Strategy     [Review metrics      [Compare to BT     [Go-live gate:     [Detect drift,
  Config UI]    Iterate ↺]           Iterate ↺]         tech + strategy    strategy decay
                                                         + operator ✓]     → retire]
```

**Key workflow principles:**
- **Per-symbol execution mode:** Each symbol independently in PAPER or LIVE. Gradual rollout.
- **Go-live validation gate:** Programmatic checklist (technical + strategy + operator) before LIVE.
- **Unified trade journal:** All trades tagged by mode (BACKTEST/PAPER/LIVE). Cross-mode comparison.
- **Continuous observation:** Backtest-to-live drift detection. Strategy decay monitoring.

## 2.2 Backend Changes Required for Workflow

These are built incrementally as each UI milestone needs them (UI shell first, backend as needed):

| Change | What | Needed By |
|--------|------|-----------|
| **Per-symbol execution mode** | `SymbolRegistry` carries mode per symbol; `TradingEngine` routes to correct executor | M3 (Paper Trade) |
| **Signal history buffer** | Store last N signals (approved + rejected) with reasons | M3 (Paper Trade) |
| **Unified journal mode tag** | Single journal file; `TradeRecord` mode field queryable | M4 (Journal) |
| **Go-live validation gate** | Programmatic checklist: token, WS, reconciliation, strategy metrics, operator confirm | M5 (Strategy Config) |
| **Cross-mode comparison** | `StrategyAnalyzer` compares same strategy across BT/PT/LIVE | M6 (Backtest) |
| **Strategy CRUD API** | List, get, update, duplicate, toggle, promote strategies | M5 (Strategy Config) |
| **Config CRUD API** | Risk, symbols, env read/write endpoints | M7 (Config Editor) |

## 3. Design Principles

| # | Principle | Application |
|---|-----------|-------------|
| 1 | **Useful** | Every screen solves a real operational need — no decorative pages |
| 2 | **Usable** | Alerts-first layout; actions within 1 click; keyboard shortcuts for emergency controls |
| 3 | **Findable** | Persistent sidebar nav; global status bar; no nested menus deeper than 2 levels |
| 4 | **Credible** | Real-time data with visible "last updated" timestamps; error states shown honestly |
| 5 | **Desirable** | Dark theme, data-dense, Bloomberg-inspired; monospace numbers; color-coded PnL |
| 6 | **Accessible** | WCAG AA contrast ratios; keyboard-navigable; screen reader labels on controls |
| 7 | **Valuable** | Reduces reaction time from minutes (curl) to seconds (visual); prevents missed anomalies |
| 8 | **Simple** | Config-driven with sensible defaults; zero setup beyond `ng serve`; no auth required |

## 4. Users & Context

| Attribute | Value |
|-----------|-------|
| User | Solo trader (single user) |
| Access | Localhost only (no auth) |
| Device | Desktop 1080p+ |
| Browser | Chrome/Edge (latest) |
| Concurrent sessions | 1 |

## 5. Technology

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Framework | Angular 19 (standalone components) | User preference; strong typing matches Java backend |
| UI Library | Angular Material | Mature, dark theme support, data table components |
| Charts | None in v1 | Tables and numbers first; charts in v2 |
| HTTP | Angular HttpClient | Built-in; interceptors for error handling |
| State | RxJS + Signals | Angular-native reactivity; no extra state library |
| Polling | RxJS `interval(5000)` + `switchMap` | Configurable refresh; auto-pause on blur |
| Build | Angular CLI | Standard tooling |
| Serve (dev) | `ng serve --proxy-config proxy.conf.json` | Proxy to Spring Boot :7777 |
| Serve (prod) | Spring Boot static resources | Copy `dist/` to `src/main/resources/static/` |
| Project location | `web-ui/` directory in repo root |

## 6. Information Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  GLOBAL STATUS BAR (always visible)                         │
│  [Mode: PAPER] [Running: ✓] [Daily PnL: +₹2,340]          │
│  [Kill Switch: OFF] [Anomaly: CLEAR] [Circuit: CLOSED]     │
│  [Last Updated: 12:34:05 IST]                              │
├──────────┬──────────────────────────────────────────────────┤
│ SIDEBAR  │  CONTENT AREA                                    │
│          │                                                  │
│ Dashboard │  (varies by route)                              │
│ Paper    │                                                  │
│  Trade   │                                                  │
│ Positions│                                                  │
│ Journal  │                                                  │
│ Backtest │                                                  │
│ Strateg- │                                                  │
│  ies     │                                                  │
│ Config   │                                                  │
│ Knowledge│                                                  │
│ Controls │                                                  │
│          │                                                  │
└──────────┴──────────────────────────────────────────────────┘
```

## 7. Screens

### 7.1 Dashboard (home — `/`)

**Purpose:** Alerts-first overview. "Is anything wrong? What's happening today?"

**Layout:** Alert banner (top) + 3-column grid of status cards.

| Section | Content | Data Source |
|---------|---------|-------------|
| **Alert Banner** | Active anomaly, kill switch, circuit breaker OPEN — red/amber banner with action buttons | `/api/anomaly/status`, `/api/risk`, `/api/circuit-breaker/status` |
| **System Status** | Mode (PAPER/LIVE), running state, uptime, active symbols count | `/api/status` |
| **Risk Summary** | Daily PnL (₹), daily loss limit usage (bar), profit lock status, consecutive losses | `/api/risk` |
| **Health** | WS feed freshness, heap usage, API error rate, candle worker status | `/api/health` |
| **Circuit Breaker** | State (CLOSED/OPEN/HALF_OPEN), consecutive failures, daily 429 count | `/api/circuit-breaker/status` |
| **Token Status** | Token valid, last refresh time, next refresh due | `/api/token/status` |

**Behavior:**
- Polls every 5 seconds
- Alert banner appears/disappears reactively
- PnL number: green for profit, red for loss, monospace font
- Health indicators: green/amber/red dots

### 7.2 Positions (`/positions`)

**Purpose:** See what's open, manage exits.

| Section | Content | Data Source |
|---------|---------|-------------|
| **Open Positions** | Table: symbol, direction, qty, entry price, current SL, TP, unrealized PnL, hold time, trailing status | `/api/positions` |
| **Active Orders** | Table: clientOrderId, symbol, state, submitted time | `/api/orders` |

**Actions per position row:**
- **Exit** button → calls `POST /api/exit/{correlationId}` with confirmation dialog
- PnL column: green/red color coding

**Global actions (sticky bottom bar):**
- **Emergency Flatten** button (red) → `POST /api/emergency-flatten`
- Confirmation: "Close ALL positions? This cannot be undone."

### 7.3 Trade Journal (`/journal`)

**Purpose:** Review today's trades, search historical trades.

| Section | Content | Data Source |
|---------|---------|-------------|
| **Today's Trades** | Table: time, symbol, direction, entry, exit, qty, PnL, R-multiple, strategy, exit reason | `/api/trades` |
| **Filters** | Date picker, symbol dropdown, strategy dropdown, win/loss toggle | Client-side filter on loaded data |
| **Summary Row** | Total trades, win rate, total PnL, avg R-multiple | Computed client-side |

**Behavior:**
- Default view: today's trades
- Date picker loads historical journal data
- Sortable columns (PnL, time, symbol)
- Export to CSV button

### 7.4 Backtest (`/backtest`)

**Purpose:** Full research mode — download data, run backtests, iterate.

**Layout:** Left panel (controls) + Right panel (results).

| Section | Content |
|---------|---------|
| **Data Manager** | Available symbols list (`/api/candle-db/symbols`); Download form: symbols + date range → `POST /api/candle-db/download`; Active downloads progress (`/api/candle-db/downloads`) |
| **Backtest Form** | Symbol picker (from available data), date range, run button → `POST /api/backtest` |
| **Results** | Metrics table (win rate, Sharpe, drawdown, profit factor, expectancy); Per-strategy breakdown; Per-symbol breakdown; Suggestions list; Equity curve (v2 — text table for v1) |

**Research workflow:**
1. Check available data → download if needed → monitor download progress
2. Select symbol + date range → Run backtest
3. Review results → Adjust strategy config → Re-run
4. Compare results across runs (session history, last 5 runs kept in memory)

### 7.5 Config Editor (`/config`)

**Purpose:** View and edit all configuration without touching files.

| Tab | Content | Persistence |
|-----|---------|-------------|
| **Strategy** | YAML editor per strategy file; fields: indicators, thresholds, risk overrides | Save → writes to `config/strategies/*.yaml` (hot-reload picks it up) |
| **Risk** | Form: daily loss limit, risk per trade %, max qty, trailing params, lot size | Save → updates `.env` via API |
| **Symbols** | Active symbols list; add/remove symbols | Save → updates `config/symbols.yaml` |
| **Environment** | Non-secret `.env` params: mode, port, log level | Save → updates `.env` via API |

**Safety:**
- Secrets (tokens, keys) are NEVER displayed or editable
- Validation before save (same rules as `ConfigValidator`)
- "Reset to defaults" button per section
- Change confirmation dialog

**New REST endpoints needed:**
- `GET /api/config/strategies` → current strategy YAML content
- `PUT /api/config/strategies/{id}` → update strategy YAML
- `GET /api/config/risk` → current risk params
- `PUT /api/config/risk` → update risk params
- `GET /api/config/symbols` → active symbols
- `PUT /api/config/symbols` → update symbols
- `GET /api/config/env` → non-secret env params
- `PUT /api/config/env` → update env params

### 7.6 Controls (`/controls`)

**Purpose:** All system control actions in one place.

| Action | Button Style | Endpoint | Confirmation |
|--------|-------------|----------|--------------|
| Kill Switch ON | Red | `POST /api/kill` | "Halt all new entries?" |
| Emergency Flatten | Red, large | `POST /api/emergency-flatten` | "Close ALL positions immediately?" |
| Acknowledge Anomaly | Amber | `POST /api/anomaly/acknowledge` | "Clear anomaly mode?" |
| Reset Day | Blue | `POST /api/reset` | "Reset all daily risk counters?" |
| Force Close Circuit Breaker | Blue | `POST /api/circuit-breaker/reset` | "Force circuit breaker to CLOSED?" |
| Refresh Token | Blue | `POST /api/token/refresh` | None |

**Layout:** Grid of action cards, each with: icon, label, description, button, last-used timestamp.

### 7.7 Paper Trade (`/paper-trade`)

**Purpose:** Monitor and interact with the paper trading session in real time. Dedicated view for the active trading day — what the system is doing right now.

**Layout:** Live feed (left) + Active state (right).

| Section | Content | Data Source |
|---------|---------|-------------|
| **Signal Feed** | Scrolling list of recent signals (approved + rejected) with timestamp, symbol, direction, confidence, strategy, approval/rejection reason | New: `/api/signals/recent` |
| **Active Positions** | Compact table: symbol, direction, qty, entry, current price, unrealized PnL, SL distance, trailing status | `/api/positions` + `/api/ticks/{symbol}` |
| **Session Stats** | Today's metrics: trades taken, win/loss count, realized PnL, max drawdown, best/worst trade | `/api/risk` + `/api/trades` |
| **Risk Utilization** | Visual bars: daily loss used (% of limit), exposure per symbol (% of cap), consecutive losses per strategy | `/api/risk` |
| **Recent Exits** | Last 5 exits with PnL, reason, hold time | `/api/trades` (latest 5) |

**Behavior:**
- Polls every 5 seconds
- Signal feed auto-scrolls, newest on top, max 50 entries
- Positions table highlights: green row for profit, red for loss
- Risk bars change color as utilization increases (green → amber → red)
- Click a position row → expand to show entry details, SL/TP levels, trailing history
- Manual exit button per position (same as Positions screen)

**New REST endpoints needed:**
- `GET /api/signals/recent?limit=50` → recent signals (approved + rejected) with reasons

### 7.8 Strategy Configuration (`/strategies`)

**Purpose:** Visual strategy management — view, edit, compare, and tune strategy parameters. More structured than the raw Config editor; designed for iterative strategy development.

**Layout:** Strategy list (left sidebar) + Strategy detail (right panel).

| Section | Content |
|---------|---------|
| **Strategy List** | All configured strategies with name, active/inactive badge, symbol count, win rate (if available) |
| **Strategy Detail** | Tabbed view per strategy: Parameters, Risk Overrides, Performance, Comparison |

**Detail Tabs:**

| Tab | Content |
|-----|---------|
| **Parameters** | Form-based editor for strategy-specific params: indicator periods (EMA fast/slow, RSI, ATR), entry thresholds (confidence, trend strength), active hours, cooldown, timeframes |
| **Risk Overrides** | Per-strategy risk config: risk per trade %, max qty, max exposure, SL/TP ATR multipliers, trailing activation/step, max consecutive losses |
| **Performance** | If backtest data available: win rate, profit factor, Sharpe, max drawdown, total trades, avg R-multiple. Link to "Run Backtest" for this strategy |
| **Comparison** | Side-by-side param comparison between 2 strategies (select from dropdown). Highlights differences |

**Actions:**
- **Save** → writes to `config/strategies/{id}.yaml`, hot-reload picks it up
- **Duplicate** → clone strategy with new name for A/B testing
- **Enable/Disable** → toggle strategy active state
- **Reset to defaults** → restore from `config/strategies/defaults.yaml`
- **Run Backtest** → navigate to Backtest screen pre-filled with this strategy's symbols

**Validation:**
- Indicator periods: positive integers
- Thresholds: within defined ranges (from `ConfigValidator`)
- Risk overrides: within global risk limits
- Real-time validation as you type (debounced)
- Invalid fields highlighted red with error message

**New REST endpoints needed:**
- `GET /api/strategies` → list all strategies with summary
- `GET /api/strategies/{id}` → full strategy config
- `PUT /api/strategies/{id}` → update strategy
- `POST /api/strategies/{id}/duplicate` → clone strategy
- `PUT /api/strategies/{id}/toggle` → enable/disable

### 7.9 Knowledge Base (`/knowledge`)

**Purpose:** In-app reference for strategy methods, risk management, and best practices. Static content rendered from markdown, searchable, always available during trading or research.

**Layout:** 3-tab sidebar (Strategy Guides, Risk Management, Best Practices) + content panel.

| Tab | Sections | Content Type |
|-----|----------|-------------|
| **Strategy Guides** | Trend following methods; Market analysis techniques; Entry/exit signals; Position sizing guides | Markdown reference with examples, indicator explanations, signal logic |
| **Risk Management** | Risk per trade guidelines; Stop-loss strategies; Portfolio management; Drawdown protection | Rules, formulas, thresholds with links to current config values |
| **Best Practices** | Strategy selection; Performance metrics; Market timing; Position sizing | Checklists, decision trees, metric interpretation guides |

**Behavior:**
- Content stored as markdown files in `web-ui/src/assets/knowledge/`
- Searchable via client-side full-text filter
- Links to relevant config sections (e.g., "Risk per trade" links to Config → Risk)
- Links to relevant backtest metrics (e.g., "Sharpe ratio" explanation links to Backtest results)
- Collapsible sections for dense content
- Read-only (content updated by developer, not via UI)

**Content examples:**
- "Trend Following" → EMA crossover logic, RSI thresholds used in the system, ATR-based stops
- "Risk Per Trade" → 2% rule explanation, how `RiskManager.preTradeCheck()` sizes positions, lot-size rounding for F&O
- "Performance Metrics" → How to interpret win rate, Sharpe, drawdown, profit factor, R-multiple from backtest reports

## 8. Global UX Patterns

### 8.1 Status Bar
- Always visible at top of every page
- Shows: mode, running state, daily PnL, kill switch, anomaly, circuit breaker
- Clicking any status item navigates to the relevant screen
- Pulses red when anomaly or kill switch is active

### 8.2 Polling & Freshness
- All data screens poll every 5 seconds (configurable in `environment.ts`)
- "Last updated: HH:mm:ss" shown on every data panel
- Stale indicator (amber) if no update for 15+ seconds
- Auto-pause polling when browser tab is not visible (Page Visibility API)

### 8.3 Confirmations
- All destructive actions require a confirmation dialog
- Dialog shows: action name, impact description, confirm/cancel buttons
- Emergency flatten: requires typing "FLATTEN" to confirm

### 8.4 Error Handling
- API errors show inline toast notification (bottom-right)
- Connection lost: persistent amber banner "Backend unreachable — retrying..."
- Auto-retry on transient errors (3 attempts)

### 8.5 Keyboard Shortcuts
| Key | Action |
|-----|--------|
| `K` | Toggle kill switch |
| `D` | Go to dashboard |
| `P` | Go to positions |
| `B` | Go to backtest |
| `R` | Force refresh current page |

### 8.6 Visual Language
- **Colors:** Dark background (#1a1a2e or Material dark), green (#4caf50) for profit, red (#f44336) for loss, amber (#ff9800) for warnings
- **Typography:** Monospace (Roboto Mono) for all numbers/prices; Sans-serif (Roboto) for labels
- **Density:** Compact Material density; minimal whitespace; data-first

## 9. Build Milestones

### Milestone 1: Foundation (Sprint 1)
**Goal:** Angular project scaffolded, routing, layout shell, status bar, dark theme.

| Task | Deliverable |
|------|-------------|
| 1.1 | `ng new web-ui` with Angular 19, standalone components, Angular Material |
| 1.2 | Dark theme setup (Material custom theme, CSS variables) |
| 1.3 | App shell: sidebar nav + status bar + content outlet |
| 1.4 | Proxy config to Spring Boot :7777 |
| 1.5 | API service layer (`ApiService`) with HttpClient + error interceptor |
| 1.6 | Polling service (`PollingService`) with configurable interval + pause-on-blur |
| 1.7 | CORS config in Spring Boot for dev |

**Exit criteria:** `ng serve` shows dark-themed shell with sidebar, status bar polls `/api/status` and shows mode + running state.

### Milestone 2: Dashboard (Sprint 2)
**Goal:** Alerts-first dashboard with all status cards.

| Task | Deliverable |
|------|-------------|
| 2.1 | Alert banner component (anomaly, kill switch, circuit breaker alerts) |
| 2.2 | Status card components (system, risk, health, circuit breaker, token) |
| 2.3 | Dashboard page composing alert banner + status grid |
| 2.4 | PnL formatting (green/red, INR symbol, monospace) |
| 2.5 | Health indicator dots (green/amber/red) |

**Exit criteria:** Dashboard shows live system status, alerts appear/disappear, PnL color-coded.

### Milestone 3: Paper Trade & Positions (Sprint 3)
**Goal:** Live paper trading monitor + position management + control actions.

**Backend work (built in this sprint):**
- Per-symbol execution mode in `SymbolRegistry` + executor routing in `TradingEngine`
- Signal history buffer: `SignalStore` (ring buffer, last 100 signals)
- `GET /api/signals/recent?limit=50` endpoint

| Task | Deliverable |
|------|-------------|
| 3.1 | **Backend:** Per-symbol execution mode (SymbolRegistry + TradingEngine routing) |
| 3.2 | **Backend:** SignalStore ring buffer + `/api/signals/recent` endpoint |
| 3.3 | Paper Trade screen: signal feed, active positions, session stats, risk utilization |
| 3.4 | Positions table with Material data table (sortable, compact) |
| 3.5 | Manual exit button per position with confirmation |
| 3.6 | Orders table (active + recent) |
| 3.7 | Controls page with action cards + confirmation dialogs |
| 3.8 | Emergency flatten with "type FLATTEN" confirmation |
| 3.9 | Keyboard shortcuts service |

**Exit criteria:** Can monitor live paper trading (signals, positions, risk); per-symbol mode displayed; exit individual positions; flatten all; toggle kill switch — all from UI.

### Milestone 4: Trade Journal (Sprint 4)
**Goal:** Trade history with filtering and export.

| Task | Deliverable |
|------|-------------|
| 4.1 | Journal table (today's trades) |
| 4.2 | Date picker + symbol/strategy filters |
| 4.3 | Summary row (computed metrics) |
| 4.4 | CSV export |
| 4.5 | New backend endpoint: `GET /api/trades?from=&to=&symbol=&strategy=` |

**Exit criteria:** Can view today's trades, filter by date/symbol, export to CSV.

### Milestone 5: Strategy Configuration (Sprint 5)
**Goal:** Visual strategy management with lifecycle — design, promote, retire.

**Backend work (built in this sprint):**
- Strategy CRUD API: `GET/PUT /api/strategies/*` (list, get, update, duplicate, toggle)
- Go-live validation gate: `GET /api/go-live-check/{symbol}` → checklist with pass/fail per item
- Per-symbol mode switch: `PUT /api/symbols/{symbol}/mode` (PAPER/LIVE)

| Task | Deliverable |
|------|-------------|
| 5.1 | **Backend:** Strategy CRUD endpoints |
| 5.2 | **Backend:** Go-live validation gate (`GoLiveChecker` — tech + strategy checks) |
| 5.3 | **Backend:** Per-symbol mode switch endpoint |
| 5.4 | Strategy list with active/inactive + mode badges (PAPER/LIVE per symbol) |
| 5.5 | Strategy detail: Parameters tab (form-based editing) |
| 5.6 | Strategy detail: Risk Overrides tab |
| 5.7 | Strategy detail: Performance tab (link to backtest) |
| 5.8 | Strategy detail: Comparison tab (side-by-side diff) |
| 5.9 | Mode switch UI: PAPER → LIVE button per symbol with go-live gate dialog |
| 5.10 | Duplicate, enable/disable, reset-to-defaults actions |
| 5.11 | Real-time validation with `ConfigValidator` rules |

**Exit criteria:** Can view strategies, edit params, promote symbols PAPER→LIVE (with validation gate), compare strategies, duplicate for A/B testing.

### Milestone 6: Backtest & Cross-Mode Analysis (Sprint 6)
**Goal:** Full backtest workflow + cross-mode performance comparison.

**Backend work (built in this sprint):**
- Cross-mode comparison: `GET /api/analysis/compare?strategy={id}&modes=BT,PT,LIVE` → side-by-side metrics
- Unified journal query: `GET /api/trades?mode=&strategy=&symbol=&from=&to=`

| Task | Deliverable |
|------|-------------|
| 6.1 | **Backend:** Cross-mode comparison endpoint |
| 6.2 | **Backend:** Unified journal query with mode filter |
| 6.3 | Data manager panel (available symbols, download form, progress tracking) |
| 6.4 | Backtest form (symbol picker, date range) |
| 6.5 | Results display (metrics table, per-strategy, per-symbol, suggestions) |
| 6.6 | Session history (last 5 runs, compare) |
| 6.7 | **Cross-mode comparison view:** Backtest vs Paper vs Live metrics side-by-side |
| 6.8 | **Drift detection indicators:** highlight metrics that diverge >20% from backtest |
| 6.9 | "Run Backtest" link from Strategy Performance tab |

**Exit criteria:** Can download data, run backtests, compare results across BT/PT/LIVE, detect drift.

### Milestone 7: Config Editor (Sprint 7)
**Goal:** Full configuration management from UI (risk, symbols, env — strategy editing is in M5).

| Task | Deliverable |
|------|-------------|
| 6.1 | New backend endpoints: `GET/PUT /api/config/*` (8 endpoints) |
| 6.2 | Strategy config editor (YAML-aware form or raw editor) |
| 6.3 | Risk config form |
| 6.4 | Symbols list editor |
| 6.5 | Environment params editor (non-secrets) |
| 6.6 | Validation + error display |
| 6.7 | Reset to defaults |

**Exit criteria:** Can view and edit all configs from UI; changes persist via hot-reload.

### Milestone 8: Polish (Sprint 8)
**Goal:** Production-ready quality.

| Task | Deliverable |
|------|-------------|
| 7.1 | Error handling: connection-lost banner, toast notifications |
| 7.2 | Loading states (skeletons / spinners) |
| 7.3 | Production build: copy `dist/` to Spring Boot static resources |
| 7.4 | Accessibility audit (contrast, keyboard nav, labels) |
| 7.5 | Performance: lazy-loaded routes, OnPush change detection |
| 7.6 | README with setup instructions |

**Exit criteria:** Production build served by Spring Boot; all screens handle loading/error/empty states.

### Milestone 9: Knowledge Base (Sprint 9)
**Goal:** In-app reference for strategies, risk management, and best practices.

| Task | Deliverable |
|------|-------------|
| 8.1 | Knowledge base page with 3-tab layout (Strategy, Risk, Best Practices) |
| 8.2 | Markdown content files for Strategy Guides (trend following, analysis, entry/exit, sizing) |
| 8.3 | Markdown content files for Risk Management (risk per trade, stop-loss, portfolio, drawdown) |
| 8.4 | Markdown content files for Best Practices (strategy selection, metrics, timing, sizing) |
| 8.5 | Client-side search/filter across all content |
| 8.6 | Cross-links to Config and Backtest screens |

**Exit criteria:** All 12 knowledge base articles written, searchable, and linking to relevant system screens.

## 10. New Backend Endpoints Required

| Endpoint | Method | Milestone | Purpose |
|----------|--------|-----------|---------|
| `/api/trades` | GET | M4 | Trade journal with date/symbol/strategy query params |
| `/api/config/strategies` | GET | M6 | List all strategy configs |
| `/api/config/strategies/{id}` | PUT | M6 | Update strategy YAML |
| `/api/config/risk` | GET | M6 | Current risk parameters |
| `/api/config/risk` | PUT | M6 | Update risk parameters |
| `/api/config/symbols` | GET | M6 | Active symbols list |
| `/api/config/symbols` | PUT | M6 | Update symbols |
| `/api/config/env` | GET | M6 | Non-secret env params |
| `/api/config/env` | PUT | M6 | Update env params |
| `/api/signals/recent` | GET | M3 | Recent signals (approved + rejected) with reasons |
| `/api/strategies` | GET | M5 | List all strategies with summary |
| `/api/strategies/{id}` | GET | M5 | Full strategy config |
| `/api/strategies/{id}` | PUT | M5 | Update strategy config |
| `/api/strategies/{id}/duplicate` | POST | M5 | Clone strategy |
| `/api/strategies/{id}/toggle` | PUT | M5 | Enable/disable strategy |
| `/api/go-live-check/{symbol}` | GET | M5 | Go-live validation checklist (tech + strategy) |
| `/api/symbols/{symbol}/mode` | PUT | M5 | Switch symbol execution mode (PAPER/LIVE) |
| `/api/analysis/compare` | GET | M6 | Cross-mode metrics comparison (BT vs PT vs LIVE) |

## 11. Configuration (environment.ts)

```typescript
export const environment = {
  apiBaseUrl: '/api',          // proxied in dev, same-origin in prod
  pollingIntervalMs: 5000,     // 5 second refresh
  staleThresholdMs: 15000,     // 15 seconds = stale warning
  backtestHistorySize: 5,      // keep last 5 backtest runs
  confirmFlattenWord: 'FLATTEN' // typed confirmation for emergency flatten
};
```

## 12. Non-Goals (v1)

- No authentication / authorization
- No mobile responsive layout
- No WebSocket real-time push (polling only)
- No candlestick charts or price charts
- No multi-user collaboration
- No deployment beyond localhost
- No dark/light theme toggle (dark only)

## 13. Success Metrics

| Metric | Target |
|--------|--------|
| Time to check system health | < 2 seconds (open browser → see alerts) |
| Time to flatten all positions | < 5 seconds (1 click + type FLATTEN + confirm) |
| Time to run a backtest | < 30 seconds (select → run → see results) |
| Page load time | < 1 second (production build) |

## 14. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Config editor saves invalid YAML | System crash or bad trades | Backend validates before saving; hot-reload has rollback |
| Polling overloads Spring Boot | Slow API for trading engine | Pause on blur; configurable interval; lightweight endpoints |
| Angular build size | Slow initial load | Lazy-loaded routes; tree-shaking; production AOT build |
| CORS misconfiguration in prod | UI can't reach API | Same-origin in prod (static resources served by Spring Boot) |

---

*Last updated: 2026-03-25*
