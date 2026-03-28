# Operational Guide

This document explains how to run, monitor, and manage the **PersonalTradeAssistant** system.

## 1. Starting the System

To fully operate the system, you need to run both the Backend Engine and the Web UI.

### Start the Backend Engine
From the root directory:
```bash
mvn spring-boot:run
```
- **Port**: 7777 (default)
- **API Base**: `http://localhost:7777/api`

### Start the Web UI
From the `web-ui` directory:
```bash
ng serve
```
- **Port**: 4200 (default)
- **Access**: Open [http://localhost:4200](http://localhost:4200) in your browser.

## 2. Execution Modes

The system's behavior is controlled by the `APP_ENV` variable in your `.env` file:

- **Backtest**: `APP_ENV=backtest`. Replays historical data for strategy validation. No live connection.
- **Paper**: `APP_ENV=paper` (Default). Connects to live market data but simulates order fills. Safe for testing strategies in real-time.
- **Live**: `APP_ENV=live`. Executes real trades via Fyers API. **Requires passing all checks in `docs/GO_LIVE_CHECKLIST.md`**.

## 3. Monitoring & Health

### Health Check
Verify the status of all components:
```bash
curl http://localhost:7777/api/health
```

### Engine Status
Check current engine state, active symbols, and mode:
```bash
curl http://localhost:7777/api/status
```

### Live Positions
View all open positions and their live PnL:
```bash
curl http://localhost:7777/api/positions
```

## 4. Risk Management & Safety

### Kill Switch (Manual)
To immediately stop all trading and flatten all open positions:
```bash
curl -X POST http://localhost:7777/api/kill
```
*Note: This will move the system to a HALTED state. A manual restart is required to resume trading.*

### Daily Risk Reset
The system automatically stops trading if the daily loss limit is reached. To reset this state (only for development/paper testing):
```bash
curl -X POST http://localhost:7777/api/reset
```

### Square-off
Intraday positions are automatically forced to square-off at **15:15 IST**.

## 5. Daily Routine

1. **Morning (09:00 IST)**: Start the backend engine and verify WebSocket connection health.
2. **Pre-Market (09:10 IST)**: Check strategy enable/disable flags in `config/strategies/*.yaml`.
3. **Market Open (09:15 IST)**: Monitor the Dashboard for initial signals.
4. **Market Close (15:30 IST)**: Review trade journals in `data/journal/` and audit logs.

---
For detailed user-facing features and configuration, see [USER_GUIDE.md](./USER_GUIDE.md).
