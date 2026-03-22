# Personal Trade Assistant

Algorithmic trading system for Indian equity markets (NSE/BSE) via Fyers API v3. Java 26+, single JVM process.

**Status:** Active build — paper trading only.

## Quick Start

```bash
# 1. Configure
cp .env.example .env    # Fill FYERS_APP_ID, SECRET_KEY, REDIRECT_URI, AUTH_CODE, SYMBOLS

# 2. Build & test
mvn clean compile
mvn test

# 3. Run (paper mode by default)
mvn exec:java
```

## Prerequisites

- JDK 26+
- Maven 3.9+
- Fyers API app credentials ([myapi.fyers.in](https://myapi.fyers.in/docsv3))

## Auth

Auth code flow. If `FYERS_AUTH_CODE` is expired, the app logs a login URL — generate a fresh code, update `.env`, restart.

## Safety

- Paper mode (`APP_ENV=paper`) is default — no real orders placed
- Review `docs/GO_LIVE_CHECKLIST.md` before enabling live trading
- Kill switches: daily loss limit (5K INR), exposure caps, forced square-off at 15:15 IST

## Docs

| File | What |
|---|---|
| `CLAUDE.md` | Architecture, contracts, coding rules, module status |
| `docs/requirements.md` | All functional + non-functional requirements |
| `docs/features.md` | Feature catalog + UI surface specs |
| `docs/GO_LIVE_CHECKLIST.md` | Production readiness checklist |
| `api_doc/FYERS APIS.md` | Fyers API reference |
