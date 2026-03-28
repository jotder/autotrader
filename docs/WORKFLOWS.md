# Antigravity Workflows — PersonalTradeAssistant

Guided playbooks for common engineering tasks in this platform.

---

## 1. Implement Trading Strategy (`implement-trading-strategy`)
Use this workflow when adding a new strategy to `config/strategies/`.

### Phase 1: Planning
- [ ] Define entry/exit logic and required indicators.
- [ ] Create/Update strategy YAML schema in `config/strategy_format.yaml`.
- [ ] Enter Plan Mode (`enter_plan_mode`) to design the `StrategyEvaluator` integration.

### Phase 2: Logic & Indicators
- [ ] Implement `CandleAnalyzer` periods/thresholds.
- [ ] Write Unit Tests in `src/test/java/com/rj/engine/` using `ta4j` mocks.

### Phase 3: Validation (Backtest)
- [ ] Run `BacktestEngine` with historical CSV data.
- [ ] **Statistical Analysis:** Verify Win Rate, Profit Factor, and Max Drawdown.
- [ ] Export results to `data/backtest_results/`.

### Phase 4: Paper Trade
- [ ] Deploy to Paper mode.
- [ ] Verify `OMS` state machine correctly handles SUBMITTED → FILLED.

---

## 2. Broker API Integration (`broker-api-integration`)
Use this when extending Fyers API support (e.g., new order types, historical data).

### Phase 1: Audit
- [ ] Verify endpoint spec in `api_doc/FYERS APIS.md`.
- [ ] Check rate limits and expected response format.

### Phase 2: Mocking
- [ ] Create mock JSON response in `src/test/resources/mocks/`.
- [ ] Implement Mockito behavior in `src/test/java/fyers/`.

### Phase 3: Implementation
- [ ] Implement on Virtual Thread.
- [ ] Add SLF4J logging for all raw payloads (DEBUG level only).
- [ ] Update `BrokerCircuitBreaker` to include the new endpoint.

---

## 3. Security & Resilience Audit (`security-audit`)
Use this workflow before any major release or go-live step.

### Phase 1: Secrets Scan
- [ ] Grep for `TOKEN`, `SECRET`, `PIN` in source and logs.
- [ ] Verify `.env` exclusion.

### Phase 2: Financial Safety
- [ ] Verify `RiskManager` pre-trade bounds.
- [ ] Test Anomaly Detector: Simulate feed staleness → check Auto-Flatten.

### Phase 3: Concurrency
- [ ] Load test `TickDisruptor` ring buffer.
- [ ] Audit `TickStore` for race conditions during high-volume ticks.

---

## 4. Outcome Analysis (`outcome-analysis`)
Use this to analyze trade journals and generate statistical reports.

### Phase 1: Log Parsing
- [ ] Read `data/journal/journal-*.ndjson`.
- [ ] Reconstruct trade lifecycles (Signal → Entry → Exit).

### Phase 2: Stats Generation
- [ ] Calculate expectancy, Sharpe ratio, and MAE/MFE (Maximum Adverse/Favorable Excursion).
- [ ] Generate summary report in Markdown.

---
*Future: AI Agentic Strategy Orchestration (Phase 3)*
