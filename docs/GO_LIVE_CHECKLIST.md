# Go-Live Checklist

Use this checklist before enabling live capital.

## 1. Build and Runtime Baseline

- [ ] JDK 26+ installed and verified on runtime host
- [ ] `mvn clean test` passes
- [ ] Artifact built from tagged commit
- [ ] Dependency versions pinned and reviewed

## 2. Configuration and Secrets

- [ ] `.env` contains all required keys and no placeholder values
- [ ] Secrets are not present in source control
- [ ] Separate credentials for non-prod and prod
- [ ] Active symbols and strategy settings reviewed

## 3. Strategy and Risk Validation

- [ ] Backtest or simulation results reviewed for active strategies
- [ ] Position sizing formula verified for long and short
- [ ] Kill switches validated (daily loss, exposure, cut-off times)
- [ ] Trailing-stop and square-off logic tested

## 4. Broker Integration Readiness

- [ ] Auth flow tested end-to-end
- [ ] Token refresh flow implemented and tested (not a stub) — or manual restart procedure documented and rehearsed
- [ ] Order placement/modify/cancel tested in low-risk mode
- [ ] Order update stream reconciles with internal state
- [ ] Duplicate-order prevention verified

## 5. Data Reliability

- [ ] Historical data payload parsing verified
- [ ] Simulation/fallback mode confirmed disabled (live auth token in use, real candles being fetched)
- [ ] WebSocket callbacks wired end-to-end: live ticks update candle cache and trigger analysis cycle
- [ ] Market-data freshness checks implemented
- [ ] Stale-feed behavior blocks new entries
- [ ] Timezone handling verified as `Asia/Kolkata`

## 6. Persistence and Audit

- [ ] Persistence layer implemented (orders, fills, PnL, rejects) — not stubs
- [ ] End-of-day PnL and daily summary can be retrieved from storage
- [ ] Audit records for all approved and rejected signals are being written

## 7. Observability and Ops

- [ ] Structured logs include signal, risk, and order correlation IDs
- [ ] Alerts configured for auth failures, API 429 spikes, and risk breaches
- [ ] Notification delivery verified (not stub/log-only)
- [ ] Operations runbook reviewed
- [ ] On-call response flow tested

## 8. Controlled Rollout

- [ ] Paper-trading burn-in completed for at least 10 sessions
- [ ] Start with reduced position size cap
- [ ] Daily manual review of all trades during initial rollout window
- [ ] Explicit go/no-go signoff recorded

## 9. Post-Go-Live Safeguards

- [ ] Daily PnL, reject reasons, and incident summary archived
- [ ] Weekly strategy drift review scheduled
- [ ] Monthly risk-threshold calibration scheduled
