# AutoTrader

Personal multi-asset algorithmic trading system for Indian markets (NSE/BSE/MCX/CDS) via Fyers API v3. Java 25+, Spring Boot, single JVM process.

**Status:** Active build — paper trading only.

## Quick Start

```bash
# 1. Configure
cp .env.example .env    # Fill: FYERS_APP_ID, SECRET_KEY, REDIRECT_URI, AUTH_CODE, SYMBOLS

# 2. Build & test
mvn clean compile
mvn test

# 3. Run (paper mode, port 7777)
mvn spring-boot:run
```

## Prerequisites

- JDK 25+
- Maven 3.9+
- Fyers API credentials ([myapi.fyers.in](https://myapi.fyers.in/docsv3))

## Configuration

| Layer | File | What |
|---|---|---|
| Global | `.env` | Secrets, capital, mode, base risk params |
| Strategy | `config/strategies/*.yaml` | Per-strategy indicators, thresholds, risk overrides |

## Safety

- Paper mode (`APP_ENV=paper`) is default — no real orders
- Review `docs/GO_LIVE_CHECKLIST.md` before live trading
- Kill switches: daily loss limit, exposure caps, forced square-off at 15:15 IST
- Anomaly protection: auto close-all → cash → require manual restart

## API (port 7777)

| Endpoint | Description |
|---|---|
| `GET /api/status` | Engine state, mode, symbols |
| `GET /api/positions` | Open positions with live PnL |
| `GET /api/trades` | Closed trade history |
| `GET /api/metrics` | Performance analytics |
| `GET /api/risk` | Risk state, kill switch |
| `GET /api/health` | Component health |
| `POST /api/kill` | Activate kill switch |
| `POST /api/reset` | Reset daily risk |

## Docs

| File | What |
|---|---|
| `CLAUDE.md` | Architecture, contracts, coding rules |
| `docs/PRD.md` | All requirements with phase tags |
| `docs/FEATURES.md` | Feature catalog + API + UI specs |
| `docs/USER_GUIDE.md` | Full user guide (config, operations, troubleshooting) |
| `docs/GO_LIVE_CHECKLIST.md` | Production readiness checklist |

---

*Last updated: 2026-03-23*
