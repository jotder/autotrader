# User Guide — PersonalTradeAssistant

PersonalTradeAssistant (AutoTrader) is a professional-grade multi-asset algorithmic trading system designed for Indian markets (NSE, BSE, MCX, CDS) using the Fyers API v3.

## 1. System Overview

The system operates as a single JVM process that manages multiple concurrent trading strategies. It is designed with institutional-level risk controls and high-performance execution.

### Key Capabilities:
- **Market Data**: Real-time tick ingestion via WebSocket.
- **Analysis**: OHLCV candle aggregation and technical indicator computation (EMA, RSI, ATR).
- **Execution**: Automated order placement with pre-trade risk validation.
- **Monitoring**: Live P&L tracking, position management, and health monitoring.

## 2. Configuring Symbols

To manage the universe of symbols you wish to trade, modify `config/symbols.yaml`.

```yaml
symbols:
  - id: NSE:SBIN-EQ
    exchange: NSE
    type: EQUITY
  - id: NSE:NIFTY25APR21000CE
    exchange: NSE
    type: OPTION
```

## 3. Configuring Strategies

Strategies are configured via YAML files in `config/strategies/`.

### Example: `intraday.yaml`
Each strategy can be enabled/disabled and tuned individually:
- **Timeframe**: M5, M15, H1.
- **Indicators**: EMA periods, RSI thresholds.
- **Risk Overrides**: Max positions per symbol, stop-loss multipliers.

## 4. Using the Web Dashboard

The Web UI provides a centralized control center for your trading operations:

- **Dashboard**: Real-time view of engine status, active signals, and live P&L.
- **Controls**: Manual kill switch and risk reset.
- **Backtest**: Interface to run and visualize historical backtests.
- **Knowledge Base**: Access to system documentation and architecture maps.

## 5. Risk & Safety Features

Safety is the highest priority in this system:
- **Risk Gates**: Every order must pass 7 sequential risk checks (e.g., balance, max exposure, drawdown).
- **Auto-Flatten**: Detected anomalies or severe drawdown trigger an automatic square-off of all positions.
- **EOD Square-off**: All intraday positions are closed at 15:15 IST automatically.

## 6. Audit & Logs

All system activities are recorded for transparency and debugging:
- **Trade Journals**: NDJSON formatted logs in `data/journal/` containing every signal, risk decision, and order event.
- **Application Logs**: Standard output and file logs in `logs/`.

---
### Related Documentation
- [INSTALL.md](./INSTALL.md): How to set up the system.
- [OPERATION.md](./OPERATION.md): How to run and manage daily operations.
- [docs/USER_GUIDE.md](./docs/USER_GUIDE.md): Deep-dive into configuration and advanced features.
