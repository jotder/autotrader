# PersonalTradeAssistant (AutoTrader)

A professional-grade multi-asset algorithmic trading system for Indian markets.

## 🚀 Quick Start

### 1. Setup Environment
```bash
# Copy and fill credentials
cp .env.example .env
```

### 2. Start Backend Engine
```bash
mvn spring-boot:run
```

### 3. Start Web UI
```bash
cd web-ui
npm install
npm start
```
Access the dashboard at [http://localhost:4200](http://localhost:4200).

## 📚 Documentation

- [**INSTALL.md**](./INSTALL.md) — Detailed setup instructions.
- [**OPERATION.md**](./OPERATION.md) — How to run and manage the system.
- [**USER_GUIDE.md**](./USER_GUIDE.md) — Feature overview and configuration.
- [**docs/PRD.md**](./docs/PRD.md) — Product requirements and roadmap.

## 🛠 Tech Stack

- **Backend**: Java 25, Spring Boot 3.4.4, LMAX Disruptor.
- **Frontend**: Angular 19, Material Design, Signals.
- **Broker**: Fyers API v3.

## 🛡️ Safety First

- **Execution Modes**: Supports `backtest`, `paper` (default), and `live`.
- **Risk Gates**: 7 sequential pre-trade checks.
- **Kill Switch**: Immediate position flattening and entry blocking.

---
*Status: Paper trading active. Multi-asset F&O in development.*
