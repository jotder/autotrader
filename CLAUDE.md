# PersonalTradeAssistant — Foundational Agentic Mandate (v1.0)

High-performance algo trading platform. Java 25 · Spring Boot · Reliability-First.

---

## 1. Core Operating Philosophy
- **Separation of Concerns:** Tasks are distributed among specialized agents (PM, Architect, Engineer, Security, QA, DevOps, Strategist).
- **Reliability-First (TDD):** NO implementation without a corresponding test. 100% Mock-first approach for broker APIs.
- **Plan-Execute-Verify:** Every multi-step task MUST be planned (`enter_plan_mode`), executed surgically, and verified exhaustively.

---

## 2. Technical Stack (Locked)
- **Runtime:** Java 25 (Virtual Threads mandated for all I/O).
- **Framework:** Spring Boot 3.4.4.
- **Build:** Maven 3.9+ (NO Gradle).
- **Concurrency:** LMAX Disruptor for the hot path (< 1ms latency).
- **Time:** `Asia/Kolkata` (IST) mandated for all market and risk logic.

---

## 3. Foundational Mandates & Redlines
- **No Blocking:** NEVER block the `risk-tick-processor` thread.
- **Safety:** Orders MUST pass `RiskManager` pre-trade bounds.
- **Secrets:** NO credentials in logs or source. Use `APIs.env` (managed via `.gitignore`).
- **Anomaly Response:** Detected stress (drawdown/latency) → AUTO-FLATTEN all positions → manual restart.

---

## 4. Skill Definition Standard (Mandatory)
All skills must follow this schema for unambiguous agentic execution:

```yaml
id: unique-skill-id
agent: responsible-agent
description: Precise technical objective
execution:
  input:
    type: object
    schema: {} # Required context, files, or parameters
  output:
    type: object
    schema: {} # Expected artifacts or state changes (e.g., acceptance_criteria)
validation: Concrete command or test to verify success
```

---

## 5. Role Orchestration
Reference specialized mandates in `.agents/rules/` for granular guidance:
- `@pm.md`: Project management and backlog.
- `@researcher.md`: Domain analysis and requirement synthesis.
- `@architect.md`: System design and concurrency.
- `@ai-strategist.md`: Alpha discovery and backtesting.
- `@ui-ux.md`: Interface standards and component mapping.
- `@engineer.md`: Implementation and clean code.
- `@security.md`: Audit and financial safety.
- `@qa.md`: TDD and edge-case validation.
- `@devops.md`: CI/CD and observability.
- `@performance-analyst.md`: P&L metrics and slippage analysis.
- `@compliance.md`: Regulatory and audit trail verification.

---
*Mandate Effective: Friday, March 27, 2026*
