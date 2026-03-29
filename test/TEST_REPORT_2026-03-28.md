# Test Report — 2026-03-28

**Project:** PersonalTradeAssistant (AutoTrader)
**Java:** 25 | Spring Boot: 3.4.4 | Maven: 3.6.3
**Angular:** 19.2.20 | TypeScript: 5.7.2 | Node: 20.18.0
**Run time:** 19:04 IST (after market hours — relevant to failures below)
**Status:** ❌ FAIL — 10 unit test failures, 2 Angular warnings

---

## Summary

| Check | Result | Details |
|---|---|---|
| Java main compile | ✅ PASS | 119 source files, 0 errors |
| Java test compile | ✅ PASS | 41 test files, 0 errors |
| Java unit tests (22 classes, 256 total) | ❌ FAIL | 246 passed / **10 failed** / 0 skipped |
| TypeScript strict type check | ✅ PASS | 0 errors (strict + noImplicitOverride + strictTemplates) |
| Angular production build | ⚠️ WARN | Built successfully — 2 budget warnings, 1 template lint warning |
| Angular Karma (`ng test`) | ❌ ERROR | Crashes — no `.spec.ts` files found (TS18003) |

---

## Section 1: Java Compilation — Main Sources

**Command:** `mvn compile -e`
**Log:** `docs/compile-main.log`

### Result: ✅ PASS

Compiled **119 source files** with `javac [debug parameters release 25]`. Build time: 10.1s.

### Warnings (JVM / Maven toolchain — not code issues)
```
WARNING: java.lang.System::load called by org.fusesource.hawtjni.runtime.Library (jansi-1.17.1.jar)
  → Maven 3.6.3 is old; upgrade to 3.9+ to silence native-access warnings on Java 25.

WARNING: sun.misc.Unsafe::objectFieldOffset called by guava-25.1-android.jar
  → Spring Boot BOM pulls Guava 25.1-android; consider upgrading Maven to 3.9+ which bundles a newer Guava.
```

These are **Maven toolchain warnings**, not application code issues.

---

## Section 2: Java Compilation — Test Sources

**Command:** `mvn test-compile -e`
**Log:** `docs/compile-test.log`

### Result: ✅ PASS

Compiled **41 source files** (22 real JUnit test classes + 19 broker API scripts) to `target/test-classes`. Build time: 4.3s.

### Notes on Broker API Script Files
The 19 files in `src/test/java/com/rj/` (root package) use Java 25 **unnamed class** syntax (`void main()` at file scope). They compile successfully under `--release 25` but are **not JUnit tests** — Maven Surefire silently skips them (no `@Test` annotations, no `@Suite`). They require a live `.env` with valid Fyers API credentials and are excluded from automated CI.

---

## Section 3: Java Unit Tests — Full Suite (22 classes, 256 tests)

**Command:** `mvn test -e`
**Log:** `docs/test-full.log`
**Surefire XML:** `target/surefire-reports/`

### Overall Result: ❌ FAIL — 10 failures in 2 classes

### Test Counts by Package

| Package | Classes | Tests Run | Passed | Failed | Skipped | Errors |
|---|---|---|---|---|---|---|
| com.rj.config | 8 | 145 | 145 | 0 | 0 | 0 |
| com.rj.engine | 12 | 95 | 85 | **10** | 0 | 0 |
| com.rj.model | 1 | 12 | 12 | 0 | 0 | 0 |
| fyers (unit) | 1 | 4 | 4 | 0 | 0 | 0 |
| **Total** | **22** | **256** | **246** | **10** | **0** | **0** |

---

### Failed Tests

#### CLASS 1: `com.rj.engine.FnoRiskSizingTest` — 4 failures / 5 tests

**Root cause:** `RiskManager.preTradeCheck()` applies **Gate 4: time cutoff** using `ZonedDateTime.now(Asia/Kolkata)`. Tests ran at **19:04 IST**, which is after `noNewTradesAfter = 15:00`. The gate fires and rejects every signal before quantity sizing is ever reached. Tests expecting `result.approved() == true` get `false`.

| Test Method | Line | Assertion | Expected | Actual |
|---|---|---|---|---|
| `equitySignalSizesInShares` | 32 | `assertTrue(result.approved())` | `true` | `false` |
| `futureSignalSizesInLots` | 56 | `assertTrue(result.approved())` | `true` | `false` |
| `futureMinimumOneLot` | 83 | `assertTrue(result.approved())` | `true` | `false` |
| `futureSignalMultipleLots` | 108 | `assertTrue(result.approved())` | `true` | `false` |

**What these tests cover:** F&O lot-size rounding in `RiskManager` (introduced in AT-013). Critical logic: equity qty sizing, futures lot-alignment (lotSize=25/50/75), minimum-1-lot enforcement, exposure capping. **The sizing logic itself is untestable as written because time gating runs first.**

---

#### CLASS 2: `com.rj.engine.RiskManagerStrategyOverrideTest` — 6 failures / 7 tests

**Root cause:** Same as above. `RiskConfig.defaults()` sets `noNewTradesAfter = LocalTime.of(15, 0)`. Tests ran at 19:04 IST → Gate 4 rejects all signals → `approved()` is `false`, `quantity()` is `0`.

| Test Method | Line | Assertion | Expected | Actual |
|---|---|---|---|---|
| `applyStrategyRiskOverride_setsOverrideUsedInPreTradeCheck` | 48 | `assertTrue(result.approved())` | `true` | `false` |
| `applyStrategyRiskOverride_higherRiskPct_producesMoreQuantity` | 65–68 | `assertTrue(withOverride.quantity() > withGlobal.quantity())` | `true` | `false` |
| `applyStrategyRiskOverride_maxQtyCap_respected` | 80 | `assertTrue(result.approved())` | `true` | `false` |
| `applyStrategyRiskOverride_maxExposurePct_respected` | 101 | `assertTrue(result.approved())` | `true` | `false` |
| `removeStrategyRiskOverride_reverts_toGlobalConfig` | 121 | `assertEquals(5, withOverride.quantity())` | `5` | `0` |
| `consecutiveLossLimit_fromOverride_suspendsStrategyEarlier` | 147 | `assertFalse(result.approved())` fails indirectly | — | Gate 4 fires before consecutive-loss gate |

**What these tests cover:** Per-strategy risk overrides via `StrategyRiskConfig` (AT-002 / YAML strategy config). Critical for verifying that YAML hot-reload changes actually propagate to order sizing decisions.

---

### Fix Required

**File:** `src/main/java/com/rj/engine/RiskManager.java`
**File:** `src/main/java/com/rj/config/RiskConfig.java`

`RiskManager` must accept an injectable clock instead of calling `ZonedDateTime.now()` directly:

```java
// RiskConfig — add a clock supplier (or pass to preTradeCheck)
private final Supplier<ZonedDateTime> clock;

// RiskManager.preTradeCheck — current code (line ~96):
ZonedDateTime now = ZonedDateTime.now(riskConfig.getExchangeZone());

// Should become (injected clock):
ZonedDateTime now = clock.get();
```

Tests then set:
```java
// Always "market open" — 10:30 IST on a weekday
Supplier<ZonedDateTime> testClock = () -> ZonedDateTime.of(2026, 3, 28, 10, 30, 0, 0,
        ZoneId.of("Asia/Kolkata"));
RiskManager rm = new RiskManager(testRiskConfig(), testClock);
```

The production default remains `() -> ZonedDateTime.now(riskConfig.getExchangeZone())`.

---

### Note: Safe Test Run Pattern Mismatch

The planned safe-run command (`-Dtest="com.rj.config.*,com.rj.engine.*,..."`) matched **only** `fyers.TokenRefreshSchedulerTest` (4 tests) due to how Surefire resolves wildcards for sub-package patterns on Windows. The full `mvn test` run (no `-Dtest` filter) correctly discovered and executed all 256 tests. For CI, use exclusions (`-Dexclude`) or profile-based activation rather than package inclusion patterns.

---

## Section 4: Broker API Scripts — Excluded from CI

**Status:** Not executed — not JUnit tests, require live Fyers credentials.

These 19 files compile and sit in `src/test/java/com/rj/` (root package). Surefire silently skips them (no `@Test` methods). They are Java 25 unnamed-class programs (`void main()`) that call live Fyers API v3 endpoints.

| File | Purpose |
|---|---|
| `TokenGenTest1.java` | Token generation (auth code flow) |
| `GetProfileTest.java` | Profile API |
| `DataApiTest.java` | Historical candle data |
| `OrderPlacementTest.java` | Place + cancel order |
| `OrderManagementTest.java` | OMS lifecycle |
| `OrdersTest.java` | Order history |
| `HoldingsTest.java` | Holdings API |
| `PositionsTest.java` | Positions API |
| `FundTest.java` | Fund details |
| `GTTTest.java` | Good-Till-Triggered orders |
| `LogoutTest.java` | Logout API |
| `MarketStatusTest.java` | Market status |
| `PriceAlertsTest.java` | Price alert CRUD |
| `ReportsTest.java` | Trade reports |
| `SmartExitTest.java` | Smart exit logic |
| `SmartOrdersTest.java` | Smart bracket orders |
| `TradeByTagTest.java` | Tag-based order lookup |
| `TransactionInfoTest.java` | Transaction log |
| `WebSocketTest.java` | Live WS feed (requires market hours) |

**Recommendation:** Move to `src/it/java/com/rj/` with Maven Failsafe plugin, activated via `-Pit` profile (see R-001).

---

## Section 5: TypeScript Type Check

**Command:** `npx tsc --noEmit --project tsconfig.app.json`
**Log:** `docs/tsc-check.log`

### Result: ✅ PASS

Zero TypeScript errors across 28 source files (17 components, 4 services, 7 shared models/utils) compiled with `strict`, `noImplicitOverride`, `noImplicitReturns`, `noFallthroughCasesInSwitch`, and `strictTemplates` all enabled.

---

## Section 6: Angular Production Build

**Command:** `npx ng build --configuration production`
**Log:** `docs/ng-build.log`
**Output:** `web-ui/dist/web-ui/`

### Result: ⚠️ BUILT WITH WARNINGS (no errors)

Bundle generation completed in 16.6s. No compiler or template errors.

### Bundle Size Report

| Chunk | Name | Raw Size | Transferred |
|---|---|---|---|
| `main-5ZOSHH4L.js` | main | 351.02 kB | 74.82 kB |
| `chunk-CNVDGTFO.js` | — | 145.97 kB | 43.90 kB |
| `polyfills-B6TNHZQ6.js` | polyfills | 34.58 kB | 11.32 kB |
| `styles-XOPYX75L.css` | styles | 10.20 kB | 1.91 kB |
| **Initial total** | | **541.77 kB** | **131.94 kB** |
| `chunk-4OUT3YLQ.js` | browser (lazy) | 67.59 kB | 17.71 kB |

### Warnings

**[WARN-A1] Bundle budget exceeded**
```
▲ [WARNING] bundle initial exceeded maximum budget.
  Budget 500.00 kB was not met by 41.77 kB with a total of 541.77 kB.
```
Initial bundle is **541.77 kB raw** (131.94 kB gzipped). Exceeds the 500 kB warning threshold by ~42 kB. Below the 1 MB error threshold. The 11 inline page components contribute heavily — lazy-loading more routes would reduce initial load.

**[WARN-A2] Component style budget exceeded (trivial)**
```
▲ [WARNING] backtest.component.ts exceeded maximum budget.
  Budget 4.00 kB was not met by 3 bytes with a total of 4.00 kB.
```
`backtest.component.ts` stylesheet is 3 bytes over the 4 kB component-style budget. Trivially fixable by removing a comment or a blank line.

**[WARN-A3] Template lint — unnecessary optional chain**
```
▲ [WARNING] NG8107: The left side of this optional chain operation does not include
  'null' or 'undefined' in its type; the '?.' operator can be replaced with '.'
  → src/app/pages/backtest/backtest.component.ts:83
    <span class="mono text-muted">{{ d.symbols?.join(', ') }}</span>
```
TypeScript knows `d.symbols` is typed as non-nullable at this point. Replace `?.join` with `.join`. Cosmetic but should be fixed to stay clean under strict mode.

---

## Section 7: Angular Karma Tests

**Command:** `npx ng test --watch=false --browsers=ChromeHeadless`
**Log:** `docs/ng-test.log`

### Result: ❌ ERROR (expected behavior — but harder than anticipated)

`ng test` does not silently exit with 0 tests. It **throws an error and crashes**:

```
Error: TS18003: No inputs were found in config file 'tsconfig.spec.json'.
  Specified 'include' paths were '["src/**/*.spec.ts","src/**/*.d.ts"]'
  and 'exclude' paths were '[...]'.
```

This is because `tsconfig.spec.json` finds zero `.spec.ts` files, and TypeScript treats an empty file set as an error. The `ng test` command is **unusable** in the current state — it cannot be run in CI without failing the pipeline with a non-zero exit code.

**Impact:** Any CI step that runs `ng test` will report failure, obscuring real test failures.

**Fix:** Either add at least one `.spec.ts` file (see R-002), or add `"noEmit": true` + `"allowJs": true` as a workaround, or remove the `test` script from `package.json` until tests are written.

---

## Section 8: Issues Found

### 8.1 BLOCKING Issues (must fix before go-live)

---

**[ISSUE-001]** Category: Java Unit Test | Affects: `RiskManager.preTradeCheck()`
**File:** `src/main/java/com/rj/engine/RiskManager.java` ~line 96
**Failing tests:** `FnoRiskSizingTest` (4 tests), `RiskManagerStrategyOverrideTest` (6 tests)
**Description:** `RiskManager.preTradeCheck()` calls `ZonedDateTime.now(Asia/Kolkata)` at Gate 4 (time cutoff check). This makes the entire F&O lot-sizing and strategy override logic **untestable outside market hours** (09:15–15:00 IST). 10 of 256 tests fail every time tests are run outside this window.
**Error:**
```
org.opentest4j.AssertionFailedError: expected: <true> but was: <false>
  at FnoRiskSizingTest.equitySignalSizesInShares(FnoRiskSizingTest.java:32)
```
**Fix:** Inject a `Supplier<ZonedDateTime>` clock into `RiskManager`. Default to `() -> ZonedDateTime.now(zone)` in production. Tests pass a fixed IST time during market hours.

---

**[ISSUE-002]** Category: Angular CI | Component: `ng test`
**File:** `web-ui/tsconfig.spec.json`, `web-ui/angular.json`
**Description:** `ng test` crashes with `TS18003: No inputs were found` because no `.spec.ts` files exist. Any CI pipeline invoking `npm test` / `ng test` will exit non-zero, blocking deployment.
**Error:**
```
Error: TS18003: No inputs were found in config file 'tsconfig.spec.json'.
  Specified 'include' paths were '["src/**/*.spec.ts","src/**/*.d.ts"]'
```
**Fix (short-term):** Add a single placeholder spec file:
```typescript
// src/app/app.component.spec.ts
describe('AppComponent', () => { it('should be defined', () => { expect(true).toBe(true); }); });
```
**Fix (proper):** Write unit tests for the 3 core services (`api.service.ts`, `mock-api.service.ts`, `polling.service.ts`) — see R-002.

---

### 8.2 NON-BLOCKING Issues (should fix)

---

**[ISSUE-003]** Category: Angular Build Warning | Component: Backtest page
**File:** `web-ui/src/app/pages/backtest/backtest.component.ts:83`
**Description:** Optional chain `?.` used where TypeScript knows the type is non-nullable. Angular compiler warning `NG8107`.
**Error:** `The left side of this optional chain operation does not include 'null' or 'undefined'`
**Fix:** Change `d.symbols?.join(', ')` to `d.symbols.join(', ')`.

---

**[ISSUE-004]** Category: Angular Build Warning | Component: Initial bundle
**Description:** Initial bundle is 541.77 kB raw (131.94 kB gzipped), exceeding the 500 kB budget warning by ~42 kB. Not an error yet but trending toward the 1 MB error threshold as more pages are added.
**Fix:** Lazy-load page routes. In `app.routes.ts`, convert page routes from static imports to `loadComponent: () => import(...)` — Angular will split each page into its own chunk, reducing the initial load.

---

**[ISSUE-005]** Category: Angular Build Warning | Component: Backtest stylesheet
**Description:** `backtest.component.ts` component SCSS is 4.003 kB, 3 bytes over the 4 kB per-component-style budget.
**Fix:** Remove 3+ bytes of whitespace/comments from the component's inline style. Trivial.

---

**[ISSUE-006]** Category: Maven CI | Toolchain
**Description:** Maven 3.6.3 is installed but `CLAUDE.md` specifies Maven 3.9+. On Java 25, Maven 3.6.3 emits `Unsafe::objectFieldOffset` deprecation warnings and `System::load` restricted-method warnings on every invocation. These clutter build output.
**Fix:** Upgrade Maven to 3.9.9+ (download from https://maven.apache.org/download.cgi).

---

### 8.3 Warnings (informational)

**[WARN-001]** `npm audit` reports **15 vulnerabilities** (7 moderate, 8 high) in Angular dev toolchain packages (`tar`, `glob`, `rimraf`, `inflight`). These are in `devDependencies` (build tools only) and do not affect the production bundle. Run `npm audit fix` to auto-resolve, then verify the build still passes.

**[WARN-002]** `TokenRefreshSchedulerTest` logs two `ERROR` lines during normal test execution:
```
ERROR fyers.TokenRefreshScheduler -- [TokenRefresh] FYERS_APP_ID or FYERS_SECRET_KEY not set
```
These are **expected** (credentials not in test environment) and the 4 tests pass. However, ERROR-level logs from passing tests are misleading in CI output. The test should use `assumeTrue(env.contains("FYERS_APP_ID"))` or lower the log level to WARN for missing-credentials scenarios.

**[WARN-003]** The `-Dtest` package wildcard pattern (`com.rj.config.*`) used in the planned safe-test command matched zero classes on Windows with Maven 3.6.3 — only the explicitly named `fyers.TokenRefreshSchedulerTest` ran. Use `mvn test` without filtering (the broker scripts are already silently skipped by Surefire) or switch to `@Tag`-based filtering.

---

## Section 9: Recommendations

### R-001: Inject clock into `RiskManager` — IMMEDIATE
**Priority:** HIGH (unblocks 10 failing tests + F&O sizing correctness)
Inject `Supplier<ZonedDateTime>` into `RiskManager`. Production uses `ZonedDateTime.now()`. Tests pass a fixed `10:30 IST` time. This also enables future scenarios like backtesting time-windows without hacking the system clock.

### R-002: Add Angular unit tests for core services
**Priority:** HIGH (unblocks `ng test` in CI)
Write `.spec.ts` for `api.service.ts`, `mock-api.service.ts`, `polling.service.ts` using `HttpClientTestingModule`. Remove `skipTests: true` for the `service` schematic in `angular.json`.

### R-003: Lazy-load Angular page routes
**Priority:** MEDIUM (bundle size)
Convert all 11 page routes in `app.routes.ts` to `loadComponent: () => import('./pages/...')`. This should reduce the initial bundle by ~250–300 kB, bringing it well under the 500 kB budget.

### R-004: Migrate broker API scripts to integration test profile
**Priority:** MEDIUM (CI hygiene)
Move the 19 unnamed-class files from `src/test/java/com/rj/` to `src/it/java/com/rj/`. Configure Maven Failsafe plugin with a `-Pit` profile. This makes the separation explicit and allows `mvn verify -Pit` to run integration tests in a dedicated CI stage.

### R-005: Add ESLint to Angular project
**Priority:** LOW (code quality)
Run `npx ng add @angular-eslint/schematics`. Enforces template best practices, accessibility, and consistent style across 17 components.

### R-006: Upgrade Maven to 3.9+
**Priority:** LOW (toolchain hygiene)
Eliminates ~5 lines of JVM native-access and `Unsafe` deprecation warnings on every Maven invocation.

### R-007: Split `environment.ts` for production
**Priority:** LOW (correctness for deployed app)
Add `web-ui/src/environments/environment.prod.ts` with `useMocks: false` and wire it via `fileReplacements` in `angular.json`'s production configuration. Currently `useMocks: true` is hardcoded in the only environment file — the production build serves mock data.

---

## Appendix A: Broker API Scripts (19 files — informational)

Not executed. See Section 4.

## Appendix B: Raw Log Files

| Log | Contents |
|---|---|
| `docs/compile-main.log` | `mvn compile -e` — 119 files, BUILD SUCCESS |
| `docs/compile-test.log` | `mvn test-compile -e` — 41 files, BUILD SUCCESS |
| `docs/test-safe.log` | Safe-run attempt (pattern mismatch — only 4 tests ran) |
| `docs/test-full.log` | `mvn test -e` — 256 tests, 10 failures |
| `docs/npm-install.log` | `npm install` — 951 packages, 15 audit vulns |
| `docs/tsc-check.log` | `tsc --noEmit` — 0 errors |
| `docs/ng-build.log` | `ng build --production` — built with 3 warnings |
| `docs/ng-test.log` | `ng test` — TS18003 crash (no spec files) |

---

*Report generated: 2026-03-28 19:10 IST*
*Tested by: Claude (automated)*
