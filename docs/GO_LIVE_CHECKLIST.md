# Go-Live Checklist — AutoTrader

> Must be 100% cleared before `APP_ENV=live`. Run `/deploy-check` to audit progress.

## 1. Build & Runtime

- [ ] JDK 25+ installed and verified on runtime host
- [ ] `mvn clean test` passes with zero failures
- [ ] Artifact built from tagged commit
- [ ] Dependency versions pinned and reviewed
- [ ] Spring Boot starts cleanly on port 7777

## 2. Configuration & Secrets

- [ ] `.env` contains all required keys — no placeholders
- [ ] Secrets not in source control (`.gitignore` verified)
- [ ] Separate credentials for paper vs live
- [ ] `config/strategies/*.yaml` reviewed — all thresholds validated
- [ ] YAML validation rejects bad config with rollback

## 3. Strategy & Risk Validation

- [ ] Backtest results reviewed for each active strategy
- [ ] Position sizing formula verified for long and short
- [ ] Kill switches validated: daily loss, exposure, time cutoff
- [ ] Trailing stop + force square-off tested
- [ ] Per-strategy risk overrides in YAML verified
- [ ] Anomaly auto-protection tested (close all → cash → manual restart)
- [ ] Portfolio-level drawdown limit configured

## 4. Broker Integration

- [ ] Auth flow tested end-to-end
- [ ] Token auto-refresh working (or manual restart procedure documented)
- [ ] Order placement tested in paper mode with live data
- [ ] Order lifecycle reconciles with internal state
- [ ] Duplicate-order prevention verified
- [ ] Rate-limit handling tested (429 backoff)

## 5. Data Reliability

- [ ] Historical data parsing verified (no synthetic/fallback candles)
- [ ] WebSocket ticks → candle cache → analysis pipeline end-to-end
- [ ] Market-data freshness checks block new signals when stale
- [ ] Timezone `Asia/Kolkata` verified throughout
- [ ] All indicator seeding complete before first signal

## 6. Persistence & Audit

- [ ] NDJSON journal writing for all event types
- [ ] End-of-day PnL retrievable from journal
- [ ] Every approved AND rejected signal has audit record
- [ ] 30-day retention policy active

## 7. Observability & Ops

- [ ] Structured logs with correlation IDs (signal → risk → order)
- [ ] Health monitor running: tick freshness, heap, queue depth
- [ ] Webhook notifications verified (not stub/log-only)
- [ ] Operations runbook reviewed (pre-market, intraday, incidents, EOD)

## 8. Controlled Rollout

- [ ] Paper trading burn-in: minimum 10 full sessions
- [ ] Paper results match backtest profile (no drift)
- [ ] Start live with reduced position size cap (50% of target)
- [ ] Daily manual review of all trades during first 2 weeks
- [ ] Explicit go/no-go signoff recorded

## 9. Post-Go-Live

- [ ] Daily: PnL, reject reasons, incident summary archived
- [ ] Weekly: strategy drift review
- [ ] Monthly: risk threshold calibration
- [ ] Auto-start configured (system boot → app start before 09:10 IST)

---

*Last updated: 2026-03-23*
