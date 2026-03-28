# PersonalTradeAssistant API Documentation

Version: 1.0  
Base URL: `/api`

---

## 1. Engine & Status

### GET `/api/status`
Returns the current engine running state and active symbols.

### GET `/api/health`
Returns system health metrics (thread status, queue sizes).

### GET `/api/metrics`
Returns session-level performance report (PnL, Win Rate, etc.).

### GET `/api/risk`
Returns current risk management state including daily PnL and drawdown status.

---

## 2. Symbols & Master Data

### GET `/api/symbols`
Lists all symbols currently tracked by the engine, grouped by category.

### GET `/api/symbol-master`
Search or lookup symbols in the Fyers master data.
- Query Params:
    - `q`: Search query (ticker or name)
    - `ticker`: Exact ticker lookup (e.g., `NSE:SBIN-EQ`)
    - `underlying`: Filter by underlying (e.g., `NIFTY`)
    - `exchange`, `segment`: Filter by IDs

### GET `/api/symbol/parse`
Parses a raw symbol string into its components (exchange, ticker, expiry, strike, etc.).
- Query Params: `s=SYMBOL`

### GET `/api/dimensions`
Returns all lookup tables (Exchanges, Segments, Order Types, etc.).

### GET `/api/dimensions/{table}`
Returns a specific lookup table.

---

## 3. Position & Order Management

### GET `/api/positions`
Returns all currently open positions.

### GET `/api/trades`
Returns history of closed trades for the current session.

### GET `/api/orders`
Returns active and recently completed orders from the OMS tracker.

### POST `/api/exit/{correlationId}`
Requests a manual exit for an open position.

### GET `/api/reconciliation`
Returns results of the last position reconciliation between engine and broker.

---

## 4. Candle Database (M1 Data)

### GET `/api/candle-db/symbols`
Lists all symbols that have downloaded M1 data available.

### GET `/api/candle-db/summary`
Returns a summary of all downloaded data (Symbol, Date Range, Total Days).

### GET `/api/candle-db/{symbol}/dates`
Lists all dates for which M1 data is stored for a specific symbol.

### GET `/api/candle-db/{symbol}?date=YYYY-MM-DD`
Loads raw M1 candles for a symbol and date.

### POST `/api/candle-db/download`
Starts an asynchronous job to download M1 data from the broker.
- Body: `{ "symbols": ["S1", "S2"], "from": "YYYY-MM-DD", "to": "YYYY-MM-DD" }`

### GET `/api/candle-db/downloads`
Lists all recent download jobs and their statuses.

### GET `/api/candle-db/download/{jobId}`
Returns the status and progress of a specific download job.

---

## 5. Backtesting & Analysis

### POST `/api/backtest`
Runs a backtest on downloaded M1 data.
- Body: `{ "symbol": "S1", "from": "YYYY-MM-DD", "to": "YYYY-MM-DD" }`

### GET `/api/profile/{symbol}?from=...&to=...`
Generates a statistical profile (Volatility, ATR, Volume) for a symbol using historical data.

---

## 6. Strategy Configuration

### GET `/api/strategies`
Lists all strategies with their versioning info and configurations.

### GET `/api/strategies/{id}`
Returns details for a specific strategy.

### POST `/api/strategies/{id}/draft`
Creates a draft configuration for a strategy (cloned from active or defaults).

### PUT `/api/strategies/{id}/draft`
Updates the draft configuration.

### POST `/api/strategies/{id}/promote`
Promotes the draft configuration to ACTIVE, archiving the previous version.

### PUT `/api/strategies/{id}/toggle`
Enables/Disables a strategy.

### POST `/api/strategies/{id}/duplicate`
Clones a strategy to a new ID.
- Body: `{ "newId": "string" }`

### GET `/api/strategies/defaults`
Returns the global default strategy configuration.

### POST `/api/strategies/validate`
Validates a strategy configuration against schema and business rules.

---

## 7. System Controls

### POST `/api/kill`
Activates the manual kill switch, blocking all new entries and halting execution.

### POST `/api/reset`
Resets the daily risk state (Realized PnL, Drawdown Peak) to allow trading to resume.

### POST `/api/token/refresh`
Triggers an immediate refresh of the broker access token.

### GET `/api/token/status`
Returns the status of the automated token refresh scheduler.

### POST `/api/emergency-flatten`
Forced square-off of ALL open positions and activation of Anomaly Mode.

### POST `/api/anomaly/acknowledge`
Clears active anomaly flags after manual investigation.

### GET `/api/circuit-breaker/status`
Returns the state of the broker API circuit breaker.

### POST `/api/circuit-breaker/reset`
Force-closes the circuit breaker to resume broker communication.
