# CLI: Script Data Download & Strategy Signal Generation — Design

**Date:** 2026-04-19
**Author:** Rahul (+ Claude)
**Status:** Design approved; implementation plan pending

---

## 1. Problem

We need two command-line entry points, runnable from IntelliJ or the terminal, to:

1. **Download** real M1 candle data for many symbols over a date range, into the existing `CandleDatabase`.
2. **Run a strategy** over downloaded data for a specific strategy configuration, emitting trade-worthy signals to logs and a CSV artifact.

These are scanning/analysis tools. They operate on the same data-adapter and strategy stack used in production, but they are not part of the live trading path. They must be kept strictly separate from each other: the downloader knows nothing about strategies; the strategy runner never calls the broker API.

## 2. Design Decisions (from brainstorming)

| Question                                | Decision                                                                    |
| --------------------------------------- | --------------------------------------------------------------------------- |
| CLI delivery style                      | Java 25 instance-main (`void main()`) test classes, like `TokenGenTest1`   |
| Symbol source (download CLI)            | `SymbolRegistry` filtered by `-Dcategory / -Dlimit / -Doffset`              |
| Symbol source (strategy CLI)            | Strategy YAML's `symbols:` list (CLI never overrides)                       |
| Date range (download CLI)               | `-Dfrom=YYYY-MM-DD -Dto=YYYY-MM-DD` (required)                              |
| Date range (strategy CLI)               | `-D` overrides → YAML `backtest:` → all available dates per symbol          |
| Strategy configuration                  | YAML (`config/strategies/<file>.yaml`), loaded via `YamlStrategyLoader`     |
| Output                                  | Log (console) + CSV artifact at `data/signals/run-<ts>.csv`                 |
| File structure                          | Two driver classes + shared helpers under `com.rj.cli`                      |
| Parallelism                             | Sequential (v1); can layer virtual-thread fan-out later                     |
| Definition of "signal"                  | Firing `Signal` per bar (non-HOLD directive); no position simulation        |

## 3. File Layout

All new files in `backend/src/test/java/com/rj/`:

```
cli/
  CliContext.java            # wired singletons (SymbolRegistry, CandleDatabase, IMarketDataAdapter, strategy map)
  SymbolSelector.java        # resolves -Dcategory=CM -Dlimit=100 -Doffset=0
  DateRange.java             # parses -Dfrom / -Dto, iterates business days, intersection helper
  StrategyFactory.java       # builds ITradeStrategy from StrategyYamlConfig
  SignalCsvWriter.java       # AutoCloseable CSV writer, flush-per-row
DownloadScriptDataTest.java  # ~30 LOC driver: download loop
RunStrategySignalsTest.java  # ~50 LOC driver: signal-scan loop
```

YAML addition to `backend/config/strategies/intraday.yaml` (and any sibling files): an **optional** per-strategy block:

```yaml
<strategy_name>:
  # ... existing fields ...
  backtest:
    from: 2026-04-01   # optional
    to:   2026-04-10   # optional
```

## 4. Shared Helpers (`com.rj.cli`)

### `SymbolSelector`
```java
static List<String> select(SymbolRegistry reg, String category, int limit, int offset)
```
- `category` → `MarketCategory.valueOf(...)`. Default `CM` if unset.
- Applies `skip(offset).limit(limit)` on `reg.symbolsFor(cat)` (already exists).
- `limit <= 0` means "no cap".
- Empty result → `IllegalStateException` (fail-fast, no silent empty runs).

### `DateRange`
```java
record DateRange(LocalDate from, LocalDate to) {
    static DateRange fromSystemProps();                                   // -Dfrom / -Dto (both required)
    static DateRange resolve(StrategyYamlConfig.BacktestBlock yaml);      // -D > YAML > empty
    boolean isEmpty();
    List<LocalDate> businessDays();                                       // from..to, Mon-Fri
    List<LocalDate> businessDaysIntersect(List<LocalDate> available);
}
```
- `from > to` → `IllegalArgumentException`.
- Parses `yyyy-MM-dd`.
- Partial YAML `backtest` block (only `from` or only `to` set) → `IllegalArgumentException`. Either both or neither.
- Weekend skip here; holiday skip delegated to downloader/reader (empty day = no-op).
- `StrategyYamlConfig.BacktestBlock` is a new nested class to be added: `LocalDate from`, `LocalDate to`, both nullable.

### `CliContext`
```java
final SymbolRegistry symbols;
final CandleDatabase candleDb;
final IMarketDataAdapter marketData;       // real FyersBrokerAdapter
final Map<String, StrategyYamlConfig> strategies;
static CliContext bootstrap();
```
- Manual wiring (no Spring): builds `ConfigManager` → `FyersBrokerAdapter(config)`; `CandleDatabase(Path.of(config.get("candle.db.dir")))`; `SymbolRegistry.load(Path.of("config/symbols.yaml"))`; `YamlStrategyLoader().load(...)`.
- Fails fast with clear message if Fyers token missing, symbol YAML absent, or strategy YAML path invalid.

### `StrategyFactory`
```java
static ITradeStrategy from(StrategyYamlConfig cfg)
```
- Wraps the existing strategy construction logic already used in production paths. Single place for YAML → strategy-instance translation so both live and CLI paths stay consistent.

### `SignalCsvWriter` (AutoCloseable)
Columns:
```
symbol,date,bar_timestamp,timeframe,side,entry,sl,tp,confidence,reason
```
- Path: `data/signals/run-<yyyyMMdd-HHmmss>.csv` (IST).
- Header on construction; `flush()` after each row (crash-safe for long scans).

## 5. `DownloadScriptDataTest`

**Inputs:** `-Dcategory` (default `CM`), `-Dlimit` (default 0 = all), `-Doffset` (default 0), `-Dfrom`, `-Dto`.

**Flow:**
```java
void main() {
    CliContext ctx = CliContext.bootstrap();
    String category = System.getProperty("category", "CM");
    int limit  = Integer.parseInt(System.getProperty("limit",  "0"));
    int offset = Integer.parseInt(System.getProperty("offset", "0"));
    List<String> symbols = SymbolSelector.select(ctx.symbols, category, limit, offset);
    DateRange range = DateRange.fromSystemProps();
    CandleDownloader dl = new CandleDownloader(ctx.marketData, ctx.candleDb);

    int ok = 0, skipped = 0;
    for (String sym : symbols) {
        int n = dl.download(sym, range.from(), range.to());
        if (n == 0) skipped++; else ok++;
        System.out.printf("  %-25s %d day(s) downloaded%n", sym, n);
    }
    System.out.printf("Done: %d symbols, %d fresh, %d skipped%n", symbols.size(), ok, skipped);
}
```

**Behavior:**
- Sequential loop over symbols. Inner day loop (with rate-limit delay) is in `CandleDownloader`.
- Existing days are auto-skipped by `CandleDownloader.exists(...)`.
- Weekends/holidays return empty from Fyers → no-op, no exception.
- Per-symbol exceptions logged by `CandleDownloader`; outer loop continues.

**Invocation:**
```
mvn test -Dtest=DownloadScriptDataTest \
  -Dcategory=CM -Dlimit=100 \
  -Dfrom=2026-04-01 -Dto=2026-04-10
```

**Never touches strategies.**

## 6. `RunStrategySignalsTest`

**Inputs:** `-Dstrategy.file` (default `config/strategies/intraday.yaml`), `-Dstrategy.name` (required), optional `-Dfrom` / `-Dto`.

**Date precedence (first hit wins):**
1. `-Dfrom` / `-Dto` (both required together if used)
2. `backtest.from` / `backtest.to` in YAML
3. Neither → per-symbol, use `ctx.candleDb.availableDates(sym)` — run everything we have.

**Flow:**
```java
void main() {
    CliContext ctx = CliContext.bootstrap();
    String name = Objects.requireNonNull(System.getProperty("strategy.name"),
        "-Dstrategy.name is required; available: " + ctx.strategies.keySet());
    StrategyYamlConfig cfg = ctx.strategies.get(name);
    if (cfg == null) throw new IllegalArgumentException("unknown strategy: " + name);

    List<String> symbols = cfg.getSymbols();
    Timeframe tf = Timeframe.valueOf(cfg.getTimeframe());   // YAML stores as String
    DateRange range = DateRange.resolve(cfg.getBacktest());

    try (SignalCsvWriter out = SignalCsvWriter.open()) {
        for (String sym : symbols) {
            List<LocalDate> avail = ctx.candleDb.availableDates(sym);
            List<LocalDate> days = range.isEmpty() ? avail : range.businessDaysIntersect(avail);
            if (days.isEmpty()) { log.warn("no data: {}", sym); continue; }

            ITradeStrategy strategy = StrategyFactory.from(cfg);
            for (LocalDate d : days) {
                List<Candle> m1   = ctx.candleDb.load(sym, d);
                List<Candle> bars = CandleAggregator.to(tf, m1);
                runEvaluate(strategy, bars, sym, d, out::write);
            }
        }
    }
}
```

**`runEvaluate` helper:** feeds bars one-by-one to `strategy.evaluate(...)`/`CandleAnalyzer`. On every non-HOLD directive, emits one `Signal` row. No position simulation, no SL/TP realization — just "would this bar have fired."

**Dependency to extract:** `CandleAggregator` does not yet exist as a utility. `BacktestEngine` has a private `aggregate(List<Candle> bars, Timeframe tf)` method plus buffered rolling aggregation. Plan: extract this into `com.rj.engine.CandleAggregator` (public utility) and have `BacktestEngine` delegate. Signature: `static List<Candle> to(Timeframe tf, List<Candle> m1)` — aggregates contiguous M1 bars into M5/M15/H1 based on IST bar boundaries.

**Error posture:**
- Missing strategy name → fail-fast with list of available strategies.
- Symbol with zero downloaded data → warn + continue.
- Per-bar evaluation exception → warn + skip that bar; symbol continues.
- CSV flushed per row → partial runs recoverable after crash.

**Invocation:**
```
mvn test -Dtest=RunStrategySignalsTest -Dstrategy.name=trend_following
mvn test -Dtest=RunStrategySignalsTest -Dstrategy.name=trend_following \
  -Dfrom=2026-04-01 -Dto=2026-04-10
```

**Never calls `IMarketDataAdapter`.**

## 7. Data Flow Summary

```
download CLI:   Fyers REST ── CandleDownloader ── CandleDatabase (CSV on disk)
strategy CLI:   CandleDatabase ── Aggregator(tf) ── Strategy.evaluate ── SignalCsvWriter + log
```

No shared state between the two runs beyond the on-disk `CandleDatabase`.

## 8. Testing Plan

- **Unit tests** (JUnit + Mockito):
  - `SymbolSelectorTest` — category filter, offset, limit=0, empty-result exception.
  - `DateRangeTest` — parse, business-day iteration, `from > to`, intersection with available dates, precedence resolution.
  - `StrategyFactoryTest` — YAML config → strategy instance parity (indicators, thresholds).
- **Smoke run:** both CLIs against 2 symbols × 3 days before scaling to 100.
- **No live-API tests in CI:** the download CLI reaches Fyers; it runs locally only.

## 9. Required Pre-Work in `main/`

These are small extractions needed before the CLIs can be thin:

1. **`StrategyYamlConfig.BacktestBlock`** — new nested class (`LocalDate from`, `LocalDate to`, both optional); getters/setters; absent block returns null from `getBacktest()`.
2. **`CandleAggregator`** (new `com.rj.engine` utility) — public `static List<Candle> to(Timeframe tf, List<Candle> m1)`. Extract the rolling-window logic currently embedded in `BacktestEngine`; delegate from `BacktestEngine`.
3. **`StrategyFactory`** lives in `src/test/java/com/rj/cli/` but may call a small factory method on an existing class in `main/` if one exists; otherwise inline the mapping there and leave `main/` alone.

## 10. Out of Scope (v1)

- Parallelism / virtual-thread fan-out.
- Per-symbol `backtest.symbols` override (CLI always uses YAML `symbols:`).
- Full PnL / trade simulation (that is `BacktestEngine`, already exists).
- JSON/parquet output formats.
- Fyers token refresh inside the CLI (assumed refreshed out-of-band by `TokenRefreshScheduler` or `TokenGenTest1`).

## 11. Success Criteria

- `DownloadScriptDataTest` with `-Dcategory=CM -Dlimit=100 -Dfrom=2026-04-01 -Dto=2026-04-10` populates `CandleDatabase` for ≥95% of valid symbols (skipping holidays/missing entries).
- `RunStrategySignalsTest -Dstrategy.name=trend_following` produces a CSV at `data/signals/run-*.csv` with one row per non-HOLD directive, plus a matching log line per row.
- Both CLIs exit 0 on success and non-zero on fatal config errors (missing file, unknown strategy, bad date).
- Neither CLI imports from the other's concern: download has no strategy imports; signals CLI has no `IMarketDataAdapter` or `CandleDownloader` imports.
