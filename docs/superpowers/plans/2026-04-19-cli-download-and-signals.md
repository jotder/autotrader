# CLI: Download + Strategy Signals — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship two script-style test-class CLIs — `DownloadScriptDataTest` (real Fyers M1 candles → `CandleDatabase`) and `RunStrategySignalsTest` (YAML-configured strategy over downloaded data → firing signals to log + CSV) — with strict separation between them.

**Architecture:** Driver classes are Java 25 instance-main tests (~30–50 LOC each). All heavy lifting sits in small, tested helpers under `com.rj.cli` (test tree) plus two new utilities in `com.rj.engine` (`CandleAggregator`, `SignalScanner`). No Spring context — `CliContext` manually wires `ConfigManager → FyersBrokerAdapter`, `CandleDatabase`, `SymbolRegistry`, and loads strategies via `YamlStrategyLoader`.

**Tech Stack:** Java 25 (instance-main), Spring Boot 3.4.4 (Maven), JUnit 5 + Mockito, AssertJ, SnakeYAML, SLF4J.

**Spec:** `docs/superpowers/specs/2026-04-19-cli-download-and-signals-design.md`

---

## File Structure

**New (main tree):**
- `backend/src/main/java/com/rj/engine/CandleAggregator.java` — M1 → M5/M15/H1 aggregation utility
- `backend/src/main/java/com/rj/engine/SignalScanner.java` — feeds M1 → analyzers → strategy, collects `TradeSignal`s

**Modified (main tree):**
- `backend/src/main/java/com/rj/config/StrategyYamlConfig.java` — add optional `BacktestBlock` nested class + getter/setter

**New (test tree):**
- `backend/src/test/java/com/rj/cli/SymbolSelector.java` + `SymbolSelectorTest.java`
- `backend/src/test/java/com/rj/cli/DateRange.java` + `DateRangeTest.java`
- `backend/src/test/java/com/rj/cli/StrategyFactory.java` + `StrategyFactoryTest.java`
- `backend/src/test/java/com/rj/cli/SignalCsvWriter.java` + `SignalCsvWriterTest.java`
- `backend/src/test/java/com/rj/cli/CliContext.java` (no unit test; exercised by driver smokes)
- `backend/src/test/java/com/rj/DownloadScriptDataTest.java`
- `backend/src/test/java/com/rj/RunStrategySignalsTest.java`
- `backend/src/test/java/com/rj/engine/CandleAggregatorTest.java`
- `backend/src/test/java/com/rj/engine/SignalScannerTest.java`
- `backend/src/test/java/com/rj/config/StrategyYamlConfigBacktestBlockTest.java`

---

## Task 1: Add `BacktestBlock` to `StrategyYamlConfig`

**Files:**
- Modify: `backend/src/main/java/com/rj/config/StrategyYamlConfig.java`
- Test: `backend/src/test/java/com/rj/config/StrategyYamlConfigBacktestBlockTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.rj.config;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class StrategyYamlConfigBacktestBlockTest {

    @Test
    void defaultBacktestBlock_isNull() {
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        assertThat(cfg.getBacktest()).isNull();
    }

    @Test
    void setBacktest_roundTrips() {
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        StrategyYamlConfig.BacktestBlock bt = new StrategyYamlConfig.BacktestBlock();
        bt.setFrom(LocalDate.of(2026, 4, 1));
        bt.setTo(LocalDate.of(2026, 4, 10));
        cfg.setBacktest(bt);

        assertThat(cfg.getBacktest()).isNotNull();
        assertThat(cfg.getBacktest().getFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(cfg.getBacktest().getTo()).isEqualTo(LocalDate.of(2026, 4, 10));
    }

    @Test
    void backtestBlock_bothFieldsNullable() {
        StrategyYamlConfig.BacktestBlock bt = new StrategyYamlConfig.BacktestBlock();
        assertThat(bt.getFrom()).isNull();
        assertThat(bt.getTo()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
cd backend && mvn -q test -Dtest=StrategyYamlConfigBacktestBlockTest
```
Expected: compile error — `BacktestBlock` does not exist, `getBacktest` unresolved.

- [ ] **Step 3: Add the nested class and accessor**

In `StrategyYamlConfig.java`, add a field near the other fields:
```java
private BacktestBlock backtest;   // optional; null when not declared in YAML
```

Add getter/setter near the others:
```java
public BacktestBlock getBacktest() { return backtest; }
public void setBacktest(BacktestBlock backtest) { this.backtest = backtest; }
```

Add the nested class at the end of the file (above closing brace):
```java
public static class BacktestBlock {
    private java.time.LocalDate from;
    private java.time.LocalDate to;

    public java.time.LocalDate getFrom() { return from; }
    public void setFrom(java.time.LocalDate from) { this.from = from; }

    public java.time.LocalDate getTo() { return to; }
    public void setTo(java.time.LocalDate to) { this.to = to; }
}
```

- [ ] **Step 4: Run test to verify it passes**

```
cd backend && mvn -q test -Dtest=StrategyYamlConfigBacktestBlockTest
```
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/rj/config/StrategyYamlConfig.java \
        backend/src/test/java/com/rj/config/StrategyYamlConfigBacktestBlockTest.java
git commit -m "feat(config): add optional BacktestBlock to StrategyYamlConfig"
```

---

## Task 2: Verify `YamlStrategyLoader` picks up `backtest:` block

**Files:**
- Modify (if needed): `backend/src/main/java/com/rj/config/YamlStrategyLoader.java`
- Test: `backend/src/test/java/com/rj/config/YamlStrategyLoaderTest.java` (existing — add one new test)

- [ ] **Step 1: Write the failing test** (append to existing `YamlStrategyLoaderTest`)

```java
@Test
void load_parsesOptionalBacktestBlock(@TempDir Path tmp) throws Exception {
    Path yaml = tmp.resolve("intraday.yaml");
    Files.writeString(yaml, """
        strategies:
          trend_following:
            enabled: true
            symbols: ["NSE:SBIN-EQ"]
            timeframe: M5
            backtest:
              from: 2026-04-01
              to: 2026-04-10
        """);

    Map<String, StrategyYamlConfig> cfgs = new YamlStrategyLoader().load(yaml);
    StrategyYamlConfig tf = cfgs.get("trend_following");
    assertThat(tf.getBacktest()).isNotNull();
    assertThat(tf.getBacktest().getFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(tf.getBacktest().getTo()).isEqualTo(LocalDate.of(2026, 4, 10));
}

@Test
void load_missingBacktestBlock_staysNull(@TempDir Path tmp) throws Exception {
    Path yaml = tmp.resolve("intraday.yaml");
    Files.writeString(yaml, """
        strategies:
          trend_following:
            enabled: true
            symbols: ["NSE:SBIN-EQ"]
            timeframe: M5
        """);

    StrategyYamlConfig tf = new YamlStrategyLoader().load(yaml).get("trend_following");
    assertThat(tf.getBacktest()).isNull();
}
```

- [ ] **Step 2: Run the tests**

```
cd backend && mvn -q test -Dtest=YamlStrategyLoaderTest
```

Expected: may PASS if SnakeYAML already binds the nested structure + LocalDate. If it FAILS on date binding, continue.

- [ ] **Step 3: Fix the loader (only if Step 2 failed)**

In `YamlStrategyLoader.load(...)`, after parsing the raw map for a strategy entry, extract the `backtest` sub-map and populate the block manually:
```java
Object btRaw = entryMap.get("backtest");
if (btRaw instanceof Map<?,?> btMap) {
    var bt = new StrategyYamlConfig.BacktestBlock();
    Object f = btMap.get("from");
    Object t = btMap.get("to");
    if (f != null) bt.setFrom(parseDate(f));
    if (t != null) bt.setTo(parseDate(t));
    cfg.setBacktest(bt);
}
```
With a small private helper:
```java
private static LocalDate parseDate(Object v) {
    if (v instanceof LocalDate d) return d;
    if (v instanceof java.util.Date d) return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    return LocalDate.parse(v.toString());
}
```

- [ ] **Step 4: Re-run tests**

```
cd backend && mvn -q test -Dtest=YamlStrategyLoaderTest
```
Expected: PASS (all tests, including the two new ones).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/rj/config/YamlStrategyLoader.java \
        backend/src/test/java/com/rj/config/YamlStrategyLoaderTest.java
git commit -m "feat(config): parse optional backtest: block from strategy YAML"
```

---

## Task 3: Extract `CandleAggregator` utility

**Files:**
- Create: `backend/src/main/java/com/rj/engine/CandleAggregator.java`
- Test: `backend/src/test/java/com/rj/engine/CandleAggregatorTest.java`
- Modify (optional): `backend/src/main/java/com/rj/engine/BacktestEngine.java` — delegate existing private `aggregate()` to the new utility. **Leave for a later commit; do NOT change `BacktestEngine` behavior in this task.**

**Design:** Given a list of contiguous M1 `Candle`s (sorted by timestamp, IST), `CandleAggregator.to(tf, m1)` returns a list of higher-timeframe bars. Bar boundaries align to wall-clock in IST:
- M5: groups of 5 M1 bars aligned to `:00, :05, :10, …`
- M15: aligned to `:00, :15, :30, :45`
- H1: aligned to top-of-hour

Partial trailing bars (fewer than N M1s for a full N-minute window) are dropped — the scanner only sees closed bars.

- [ ] **Step 1: Write the failing test**

```java
package com.rj.engine;

import com.rj.model.Candle;
import com.rj.model.Timeframe;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandleAggregatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Candle bar(int hh, int mm, double o, double h, double l, double c, long v) {
        ZonedDateTime ts = ZonedDateTime.of(2026, 4, 1, hh, mm, 0, 0, IST);
        return new Candle(ts, o, h, l, c, v);
    }

    @Test
    void aggregate_m1ToM5_groupsEveryFiveBars() {
        List<Candle> m1 = new ArrayList<>();
        for (int m = 15; m < 25; m++) {
            m1.add(bar(9, m, 100 + m, 105 + m, 95 + m, 102 + m, 1000));
        }
        List<Candle> m5 = CandleAggregator.to(Timeframe.M5, m1);

        assertThat(m5).hasSize(2);
        assertThat(m5.get(0).open).isEqualTo(115.0);    // open of 09:15
        assertThat(m5.get(0).close).isEqualTo(121.0);   // close of 09:19
        assertThat(m5.get(0).volume).isEqualTo(5000);
        assertThat(m5.get(0).timestamp.getMinute()).isEqualTo(15);
    }

    @Test
    void aggregate_m1ToM15_alignsToWallClock() {
        List<Candle> m1 = new ArrayList<>();
        for (int m = 15; m < 30; m++) {
            m1.add(bar(9, m, 100, 101, 99, 100.5, 10));
        }
        List<Candle> m15 = CandleAggregator.to(Timeframe.M15, m1);

        assertThat(m15).hasSize(1);
        assertThat(m15.get(0).timestamp.getMinute()).isEqualTo(15);
        assertThat(m15.get(0).volume).isEqualTo(150);
    }

    @Test
    void aggregate_dropsPartialTrailingBar() {
        List<Candle> m1 = new ArrayList<>();
        // Only 3 bars into a 5-minute window → no M5 emitted.
        m1.add(bar(9, 15, 100, 101, 99, 100, 10));
        m1.add(bar(9, 16, 100, 101, 99, 100, 10));
        m1.add(bar(9, 17, 100, 101, 99, 100, 10));
        assertThat(CandleAggregator.to(Timeframe.M5, m1)).isEmpty();
    }

    @Test
    void aggregate_m1ToM1_returnsInputUnchanged() {
        List<Candle> m1 = List.of(bar(9, 15, 100, 101, 99, 100, 10));
        assertThat(CandleAggregator.to(Timeframe.M1, m1)).isEqualTo(m1);
    }
}
```

*Note:* confirm the `Candle` public fields / constructor signature before pasting (the tests above assume `new Candle(ts, o, h, l, c, volume)`). If `Candle` uses builders or getters, adapt.

- [ ] **Step 2: Run test — verify it fails**

```
cd backend && mvn -q test -Dtest=CandleAggregatorTest
```
Expected: compile error — `CandleAggregator` does not exist.

- [ ] **Step 3: Implement the aggregator**

Create `backend/src/main/java/com/rj/engine/CandleAggregator.java`:
```java
package com.rj.engine;

import com.rj.model.Candle;
import com.rj.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Aggregates M1 candles into a higher timeframe. Bars align to IST wall-clock boundaries. */
public final class CandleAggregator {
    private CandleAggregator() {}

    public static List<Candle> to(Timeframe tf, List<Candle> m1) {
        if (tf == Timeframe.M1) return List.copyOf(m1);
        int windowMinutes = switch (tf) {
            case M5 -> 5;
            case M15 -> 15;
            case H1 -> 60;
            default -> throw new IllegalArgumentException("unsupported tf: " + tf);
        };

        List<Candle> out = new ArrayList<>();
        List<Candle> buf = new ArrayList<>(windowMinutes);
        int currentBucket = -1;

        for (Candle c : m1) {
            int bucket = bucketFor(c.timestamp, windowMinutes);
            if (bucket != currentBucket && !buf.isEmpty()) {
                if (buf.size() == windowMinutes) out.add(combine(buf));
                buf.clear();
            }
            currentBucket = bucket;
            buf.add(c);
        }
        if (buf.size() == windowMinutes) out.add(combine(buf));
        return out;
    }

    private static int bucketFor(ZonedDateTime ts, int windowMinutes) {
        int minuteOfDay = ts.getHour() * 60 + ts.getMinute();
        return minuteOfDay / windowMinutes;
    }

    private static Candle combine(List<Candle> bars) {
        ZonedDateTime ts = bars.getFirst().timestamp;
        double open = bars.getFirst().open;
        double close = bars.getLast().close;
        double high = bars.stream().mapToDouble(b -> b.high).max().orElse(open);
        double low = bars.stream().mapToDouble(b -> b.low).min().orElse(open);
        long vol = bars.stream().mapToLong(b -> (long) b.volume).sum();
        return new Candle(ts, open, high, low, close, vol);
    }
}
```

If the `Candle` constructor differs from `(ZonedDateTime, double, double, double, double, long)`, adjust the `combine` / test harness accordingly.

- [ ] **Step 4: Run test — verify it passes**

```
cd backend && mvn -q test -Dtest=CandleAggregatorTest
```
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/rj/engine/CandleAggregator.java \
        backend/src/test/java/com/rj/engine/CandleAggregatorTest.java
git commit -m "feat(engine): add CandleAggregator utility for M1 → M5/M15/H1"
```

---

## Task 4: `SignalScanner` — new main-tree scanner

**Files:**
- Create: `backend/src/main/java/com/rj/engine/SignalScanner.java`
- Test: `backend/src/test/java/com/rj/engine/SignalScannerTest.java`

**Design:** `SignalScanner` takes a symbol + primary `Timeframe` + `ITradeStrategy`. Its `scan(List<Candle> m1, LocalDate day)` method:
1. Aggregates M1 → primary timeframe using `CandleAggregator`.
2. Also maintains M15 and H1 aggregation in parallel (fed from the same M1 stream) when the primary is M5.
3. For each primary bar close, updates a per-timeframe `CandleAnalyzer`, reads the latest `CandleRecommendation` per timeframe, and calls `strategy.evaluate(symbol, recs)`.
4. Collects every non-empty `Optional<TradeSignal>` into `List<SignalEvent>` where `SignalEvent(TradeSignal signal, ZonedDateTime barTimestamp)`.

- [ ] **Step 1: Write the failing test**

```java
package com.rj.engine;

import com.rj.model.*;
import com.rj.strategy.ITradeStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SignalScannerTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void scan_emitsOneEventPerFiringBar() {
        List<Candle> m1 = new ArrayList<>();
        for (int h = 9; h < 11; h++) {
            for (int m = (h == 9 ? 15 : 0); m < 60; m++) {
                m1.add(new Candle(ZonedDateTime.of(2026, 4, 1, h, m, 0, 0, IST),
                        100, 101, 99, 100, 10));
            }
        }

        ITradeStrategy strategy = Mockito.mock(ITradeStrategy.class);
        when(strategy.getId()).thenReturn("test-strat");
        TradeSignal signal = TradeSignal.builder()
                .symbol("NSE:SBIN-EQ").correlationId("x").direction(Signal.BUY)
                .confidence(0.9).strategyId("test-strat")
                .suggestedEntry(100).suggestedStopLoss(99).suggestedTarget(102)
                .build();
        // Fire on every call → we should get one event per M5 bar close.
        when(strategy.evaluate(anyString(), any())).thenReturn(Optional.of(signal));

        SignalScanner scanner = new SignalScanner("NSE:SBIN-EQ", Timeframe.M5, strategy);
        List<SignalScanner.SignalEvent> events = scanner.scan(m1, LocalDate.of(2026, 4, 1));

        // Roughly: 45 bars in hour 1 (9:15-10:00) + 60 in hour 2 = 105 m1 → 21 full M5 bars → 21 events
        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(e -> assertThat(e.signal().getDirection()).isEqualTo(Signal.BUY));
    }

    @Test
    void scan_emitsNothingWhenStrategyAlwaysReturnsEmpty() {
        ITradeStrategy strategy = Mockito.mock(ITradeStrategy.class);
        when(strategy.evaluate(anyString(), any())).thenReturn(Optional.empty());

        List<Candle> m1 = List.of(new Candle(
                ZonedDateTime.of(2026, 4, 1, 9, 15, 0, 0, IST), 100, 101, 99, 100, 10));

        SignalScanner scanner = new SignalScanner("NSE:SBIN-EQ", Timeframe.M5, strategy);
        List<SignalScanner.SignalEvent> events = scanner.scan(m1, LocalDate.of(2026, 4, 1));

        assertThat(events).isEmpty();
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

```
cd backend && mvn -q test -Dtest=SignalScannerTest
```
Expected: compile error — `SignalScanner` not defined.

- [ ] **Step 3: Implement `SignalScanner`**

Model closely on `BacktestEngine`'s analyzer/aggregation loop, but without position / order / journal logic. Create `backend/src/main/java/com/rj/engine/SignalScanner.java`:
```java
package com.rj.engine;

import com.rj.model.*;
import com.rj.strategy.ITradeStrategy;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

public class SignalScanner {

    public record SignalEvent(TradeSignal signal, ZonedDateTime barTimestamp, LocalDate tradingDate) {}

    private final String symbol;
    private final Timeframe primary;
    private final ITradeStrategy strategy;
    private final CandleAnalyzer primaryAnalyzer;
    private final CandleAnalyzer m15Analyzer;
    private final CandleAnalyzer h1Analyzer;

    public SignalScanner(String symbol, Timeframe primary, ITradeStrategy strategy) {
        this.symbol = symbol;
        this.primary = primary;
        this.strategy = strategy;
        this.primaryAnalyzer = new CandleAnalyzer(symbol, primary);
        this.m15Analyzer = primary == Timeframe.M15 ? primaryAnalyzer : new CandleAnalyzer(symbol, Timeframe.M15);
        this.h1Analyzer = new CandleAnalyzer(symbol, Timeframe.H1);
    }

    public List<SignalEvent> scan(List<Candle> m1, LocalDate tradingDate) {
        List<Candle> primaryBars = CandleAggregator.to(primary, m1);
        List<Candle> m15Bars = CandleAggregator.to(Timeframe.M15, m1);
        List<Candle> h1Bars = CandleAggregator.to(Timeframe.H1, m1);

        Map<Timeframe, CandleRecommendation> latest = new EnumMap<>(Timeframe.class);
        List<SignalEvent> out = new ArrayList<>();

        int m15Idx = 0, h1Idx = 0;
        for (Candle bar : primaryBars) {
            // Advance higher-tf indexes up to current primary bar time.
            while (m15Idx < m15Bars.size() && !m15Bars.get(m15Idx).timestamp.isAfter(bar.timestamp)) {
                latest.put(Timeframe.M15, m15Analyzer.onCandle(m15Bars.get(m15Idx)));
                m15Idx++;
            }
            while (h1Idx < h1Bars.size() && !h1Bars.get(h1Idx).timestamp.isAfter(bar.timestamp)) {
                latest.put(Timeframe.H1, h1Analyzer.onCandle(h1Bars.get(h1Idx)));
                h1Idx++;
            }
            latest.put(primary, primaryAnalyzer.onCandle(bar));

            strategy.evaluate(symbol, latest).ifPresent(sig ->
                out.add(new SignalEvent(sig, bar.timestamp, tradingDate)));
        }
        return out;
    }
}
```

**Note:** the actual `CandleAnalyzer` API may use a different method name (e.g. `analyze(...)` instead of `onCandle(...)`). Before implementing, open `CandleAnalyzer.java` and use the real method that returns `CandleRecommendation` per bar. Adjust the scanner accordingly.

- [ ] **Step 4: Run test — verify it passes**

```
cd backend && mvn -q test -Dtest=SignalScannerTest
```
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/rj/engine/SignalScanner.java \
        backend/src/test/java/com/rj/engine/SignalScannerTest.java
git commit -m "feat(engine): add SignalScanner for signal-only backtest scanning"
```

---

## Task 5: `SymbolSelector` helper

**Files:**
- Create: `backend/src/test/java/com/rj/cli/SymbolSelector.java`
- Test: `backend/src/test/java/com/rj/cli/SymbolSelectorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.rj.cli;

import com.rj.config.MarketCategory;
import com.rj.config.SymbolRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class SymbolSelectorTest {

    @Test
    void select_limitZero_returnsAll() {
        SymbolRegistry reg = Mockito.mock(SymbolRegistry.class);
        when(reg.symbolsFor(MarketCategory.CM)).thenReturn(List.of("A", "B", "C"));

        assertThat(SymbolSelector.select(reg, "CM", 0, 0))
                .containsExactly("A", "B", "C");
    }

    @Test
    void select_offsetAndLimit_applied() {
        SymbolRegistry reg = Mockito.mock(SymbolRegistry.class);
        when(reg.symbolsFor(MarketCategory.CM))
                .thenReturn(List.of("A", "B", "C", "D", "E"));

        assertThat(SymbolSelector.select(reg, "CM", 2, 1))
                .containsExactly("B", "C");
    }

    @Test
    void select_emptyResult_throws() {
        SymbolRegistry reg = Mockito.mock(SymbolRegistry.class);
        when(reg.symbolsFor(MarketCategory.CM)).thenReturn(List.of());

        assertThatThrownBy(() -> SymbolSelector.select(reg, "CM", 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no symbols");
    }

    @Test
    void select_unknownCategory_throws() {
        SymbolRegistry reg = Mockito.mock(SymbolRegistry.class);
        assertThatThrownBy(() -> SymbolSelector.select(reg, "BOGUS", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run — fail**

```
cd backend && mvn -q test -Dtest=SymbolSelectorTest
```
Expected: compile error.

- [ ] **Step 3: Implement**

```java
package com.rj.cli;

import com.rj.config.MarketCategory;
import com.rj.config.SymbolRegistry;

import java.util.List;
import java.util.stream.Collectors;

public final class SymbolSelector {
    private SymbolSelector() {}

    public static List<String> select(SymbolRegistry reg, String category, int limit, int offset) {
        MarketCategory cat = MarketCategory.valueOf(category);
        List<String> all = reg.symbolsFor(cat);
        if (all == null || all.isEmpty()) {
            throw new IllegalStateException("no symbols for category: " + cat);
        }
        var stream = all.stream().skip(Math.max(0, offset));
        if (limit > 0) stream = stream.limit(limit);
        List<String> result = stream.collect(Collectors.toUnmodifiableList());
        if (result.isEmpty()) {
            throw new IllegalStateException("no symbols after offset=" + offset + " limit=" + limit);
        }
        return result;
    }
}
```

- [ ] **Step 4: Run — pass**

```
cd backend && mvn -q test -Dtest=SymbolSelectorTest
```
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/rj/cli/SymbolSelector.java \
        backend/src/test/java/com/rj/cli/SymbolSelectorTest.java
git commit -m "feat(cli): add SymbolSelector test helper"
```

---

## Task 6: `DateRange` helper

**Files:**
- Create: `backend/src/test/java/com/rj/cli/DateRange.java`
- Test: `backend/src/test/java/com/rj/cli/DateRangeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.rj.cli;

import com.rj.config.StrategyYamlConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DateRangeTest {

    @Test
    void businessDays_skipsWeekends() {
        DateRange r = new DateRange(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 7)); // Fri..Tue
        assertThat(r.businessDays()).containsExactly(
                LocalDate.of(2026, 4, 3),
                LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 4, 7));
    }

    @Test
    void constructor_rejectsFromAfterTo() {
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void businessDaysIntersect_onlyKeepsAvailable() {
        DateRange r = new DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 5));
        List<LocalDate> avail = List.of(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 6));
        assertThat(r.businessDaysIntersect(avail))
                .containsExactly(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3));
    }

    @Test
    void resolve_yamlBlockWithBothDates_used() {
        StrategyYamlConfig.BacktestBlock bt = new StrategyYamlConfig.BacktestBlock();
        bt.setFrom(LocalDate.of(2026, 4, 1));
        bt.setTo(LocalDate.of(2026, 4, 10));
        DateRange r = DateRange.resolve(bt);
        assertThat(r.isEmpty()).isFalse();
        assertThat(r.from()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void resolve_partialYamlBlock_throws() {
        StrategyYamlConfig.BacktestBlock bt = new StrategyYamlConfig.BacktestBlock();
        bt.setFrom(LocalDate.of(2026, 4, 1));
        assertThatThrownBy(() -> DateRange.resolve(bt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both from and to");
    }

    @Test
    void resolve_nullYamlBlock_returnsEmpty() {
        DateRange r = DateRange.resolve(null);
        assertThat(r.isEmpty()).isTrue();
    }
}
```

- [ ] **Step 2: Run — fail**

```
cd backend && mvn -q test -Dtest=DateRangeTest
```
Expected: compile error.

- [ ] **Step 3: Implement**

```java
package com.rj.cli;

import com.rj.config.StrategyYamlConfig;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from > to: " + from + " > " + to);
        }
    }

    public boolean isEmpty() { return from == null || to == null; }

    public static DateRange empty() { return new DateRange(null, null); }

    /** Read -Dfrom / -Dto; both required. */
    public static DateRange fromSystemProps() {
        String f = System.getProperty("from");
        String t = System.getProperty("to");
        if (f == null || t == null) {
            throw new IllegalArgumentException("-Dfrom=YYYY-MM-DD and -Dto=YYYY-MM-DD required");
        }
        return new DateRange(LocalDate.parse(f), LocalDate.parse(t));
    }

    /** -Dfrom/-Dto wins; else YAML; else empty. */
    public static DateRange resolve(StrategyYamlConfig.BacktestBlock yaml) {
        String f = System.getProperty("from");
        String t = System.getProperty("to");
        if (f != null && t != null) return new DateRange(LocalDate.parse(f), LocalDate.parse(t));
        if (f != null || t != null) throw new IllegalArgumentException("-Dfrom and -Dto must be set together");

        if (yaml == null) return empty();
        if ((yaml.getFrom() == null) != (yaml.getTo() == null)) {
            throw new IllegalArgumentException("backtest: block must set both from and to (or neither)");
        }
        if (yaml.getFrom() == null) return empty();
        return new DateRange(yaml.getFrom(), yaml.getTo());
    }

    public List<LocalDate> businessDays() {
        if (isEmpty()) return List.of();
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) out.add(d);
        }
        return out;
    }

    public List<LocalDate> businessDaysIntersect(List<LocalDate> available) {
        if (isEmpty()) return List.copyOf(available);
        var set = new java.util.HashSet<>(available);
        return businessDays().stream().filter(set::contains).toList();
    }
}
```

- [ ] **Step 4: Run — pass**

```
cd backend && mvn -q test -Dtest=DateRangeTest
```
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/rj/cli/DateRange.java \
        backend/src/test/java/com/rj/cli/DateRangeTest.java
git commit -m "feat(cli): add DateRange helper with YAML+system-prop resolution"
```

---

## Task 7: `StrategyFactory` helper

**Files:**
- Create: `backend/src/test/java/com/rj/cli/StrategyFactory.java`
- Test: `backend/src/test/java/com/rj/cli/StrategyFactoryTest.java`

**Design:** `StrategyFactory.from(StrategyYamlConfig cfg, String id)` returns a ready-to-run `ITradeStrategy`. For v1, always returns a `MultiTimeframeVotingStrategy` populated from YAML fields:
- `id` → the YAML map key (e.g. `trend_following`)
- `name` → id (simple)
- `minConfidence` → `cfg.getEntry().getMinConfidence()`
- `slAtrMultiplier` → `cfg.getRisk().getSlAtrMultiplier()`
- `tpRMultiple` → `cfg.getRisk().getTpRMultiple()`

- [ ] **Step 1: Write the failing test**

```java
package com.rj.cli;

import com.rj.config.StrategyYamlConfig;
import com.rj.strategy.ITradeStrategy;
import com.rj.strategy.MultiTimeframeVotingStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyFactoryTest {

    @Test
    void from_yamlConfig_returnsConfiguredStrategy() {
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        cfg.getEntry().setMinConfidence(0.85);
        cfg.getRisk().setSlAtrMultiplier(2.0);
        cfg.getRisk().setTpRMultiple(3.0);

        ITradeStrategy s = StrategyFactory.from(cfg, "trend_following");
        assertThat(s).isInstanceOf(MultiTimeframeVotingStrategy.class);
        assertThat(s.getId()).isEqualTo("trend_following");
    }
}
```

(If `Entry.setMinConfidence` / `Risk.setSlAtrMultiplier` / `setTpRMultiple` setters don't exist, look up the real names in `StrategyYamlConfig`'s nested classes and adjust.)

- [ ] **Step 2: Run — fail**

```
cd backend && mvn -q test -Dtest=StrategyFactoryTest
```

- [ ] **Step 3: Implement**

```java
package com.rj.cli;

import com.rj.config.StrategyYamlConfig;
import com.rj.strategy.ITradeStrategy;
import com.rj.strategy.MultiTimeframeVotingStrategy;

public final class StrategyFactory {
    private StrategyFactory() {}

    public static ITradeStrategy from(StrategyYamlConfig cfg, String id) {
        double minConf = cfg.getEntry().getMinConfidence();
        double sl = cfg.getRisk().getSlAtrMultiplier();
        double tp = cfg.getRisk().getTpRMultiple();
        return new MultiTimeframeVotingStrategy(id, id, minConf, sl, tp);
    }
}
```

- [ ] **Step 4: Run — pass**

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/rj/cli/StrategyFactory.java \
        backend/src/test/java/com/rj/cli/StrategyFactoryTest.java
git commit -m "feat(cli): add StrategyFactory wiring YAML config to strategy instance"
```

---

## Task 8: `SignalCsvWriter` helper

**Files:**
- Create: `backend/src/test/java/com/rj/cli/SignalCsvWriter.java`
- Test: `backend/src/test/java/com/rj/cli/SignalCsvWriterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.rj.cli;

import com.rj.engine.SignalScanner;
import com.rj.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignalCsvWriterTest {

    @Test
    void write_headerAndRow_written(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("signals.csv");
        TradeSignal sig = TradeSignal.builder()
                .symbol("NSE:SBIN-EQ").correlationId("x").direction(Signal.BUY)
                .confidence(0.9).strategyId("trend_following")
                .suggestedEntry(100).suggestedStopLoss(99).suggestedTarget(102)
                .reason("bullish break").build();
        var event = new SignalScanner.SignalEvent(sig,
                ZonedDateTime.of(2026, 4, 1, 9, 20, 0, 0, ZoneId.of("Asia/Kolkata")),
                LocalDate.of(2026, 4, 1));

        try (var w = SignalCsvWriter.open(out, Timeframe.M5)) {
            w.write(event);
        }

        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo(
                "symbol,date,bar_timestamp,timeframe,side,entry,sl,tp,confidence,reason");
        assertThat(lines.get(1)).contains("NSE:SBIN-EQ", "2026-04-01", "BUY",
                "100.0", "99.0", "102.0", "0.9", "bullish break");
    }
}
```

- [ ] **Step 2: Run — fail**

- [ ] **Step 3: Implement**

```java
package com.rj.cli;

import com.rj.engine.SignalScanner;
import com.rj.model.Timeframe;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class SignalCsvWriter implements AutoCloseable {

    private static final String HEADER =
            "symbol,date,bar_timestamp,timeframe,side,entry,sl,tp,confidence,reason";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final BufferedWriter writer;
    private final Timeframe timeframe;

    private SignalCsvWriter(BufferedWriter w, Timeframe tf) {
        this.writer = w;
        this.timeframe = tf;
    }

    public static SignalCsvWriter openDefault(Timeframe tf) {
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now(IST));
        Path dir = Path.of("data", "signals");
        try {
            Files.createDirectories(dir);
            return open(dir.resolve("run-" + ts + ".csv"), tf);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SignalCsvWriter open(Path path, Timeframe tf) throws IOException {
        BufferedWriter w = Files.newBufferedWriter(path);
        w.write(HEADER);
        w.newLine();
        w.flush();
        return new SignalCsvWriter(w, tf);
    }

    public void write(SignalScanner.SignalEvent ev) {
        var s = ev.signal();
        String reason = s.getReason() == null ? "" : s.getReason().replace(",", ";");
        String row = String.join(",",
                s.getSymbol(),
                ev.tradingDate().toString(),
                ev.barTimestamp().toString(),
                timeframe.name(),
                s.getDirection().name(),
                Double.toString(s.getSuggestedEntry()),
                Double.toString(s.getSuggestedStopLoss()),
                Double.toString(s.getSuggestedTarget()),
                Double.toString(s.getConfidence()),
                reason);
        try {
            writer.write(row);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override public void close() throws IOException { writer.close(); }
}
```

- [ ] **Step 4: Run — pass**

```
cd backend && mvn -q test -Dtest=SignalCsvWriterTest
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/rj/cli/SignalCsvWriter.java \
        backend/src/test/java/com/rj/cli/SignalCsvWriterTest.java
git commit -m "feat(cli): add SignalCsvWriter for run-artifact CSV output"
```

---

## Task 9: `CliContext` — assembly helper (no unit test)

**Files:**
- Create: `backend/src/test/java/com/rj/cli/CliContext.java`

**Design:** Bootstraps real adapters from `ConfigManager`. No mocks. Exercised by the driver smokes. Throws clear exceptions when config is missing so the drivers fail fast.

- [ ] **Step 1: Implement**

```java
package com.rj.cli;

import com.rj.broker.IMarketDataAdapter;
import com.rj.config.*;
import com.rj.engine.CandleDatabase;
import com.rj.fyers.FyersBrokerAdapter;

import java.nio.file.Path;
import java.util.Map;

public final class CliContext {

    public final ConfigManager config;
    public final SymbolRegistry symbols;
    public final CandleDatabase candleDb;
    public final IMarketDataAdapter marketData;
    public final Map<String, StrategyYamlConfig> strategies;

    private CliContext(ConfigManager cfg, SymbolRegistry sym, CandleDatabase db,
                       IMarketDataAdapter md, Map<String, StrategyYamlConfig> strats) {
        this.config = cfg;
        this.symbols = sym;
        this.candleDb = db;
        this.marketData = md;
        this.strategies = strats;
    }

    public static CliContext bootstrap() {
        ConfigManager config = ConfigManager.getInstance();   // existing singleton pattern
        Path symYaml = Path.of(config.getProperty("symbols.yaml", "config/symbols.yaml"));
        SymbolRegistry sym = SymbolRegistry.load(symYaml);

        Path dbDir = Path.of(config.getProperty("candle.db.dir", "data/candles"));
        CandleDatabase db = new CandleDatabase(dbDir);

        IMarketDataAdapter md = new FyersBrokerAdapter(config);

        String stratFile = System.getProperty("strategy.file", "config/strategies/intraday.yaml");
        Map<String, StrategyYamlConfig> strats = new YamlStrategyLoader().load(Path.of(stratFile));

        return new CliContext(config, sym, db, md, strats);
    }
}
```

If `ConfigManager` does not have a `getInstance()` / `getProperty(key, default)` pair, open `ConfigManager.java` and use the real accessors.

- [ ] **Step 2: Compile**

```
cd backend && mvn -q test-compile
```
Expected: success.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/rj/cli/CliContext.java
git commit -m "feat(cli): add CliContext manual wiring helper"
```

---

## Task 10: `DownloadScriptDataTest` driver

**Files:**
- Create: `backend/src/test/java/com/rj/DownloadScriptDataTest.java`

- [ ] **Step 1: Implement**

```java
import com.rj.cli.*;
import com.rj.engine.CandleDownloader;

import java.time.LocalDate;
import java.util.List;

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
        try {
            int n = dl.download(sym, range.from(), range.to());
            if (n == 0) skipped++; else ok++;
            System.out.printf("  %-25s %d day(s) downloaded%n", sym, n);
        } catch (Exception e) {
            System.out.printf("  %-25s ERROR: %s%n", sym, e.getMessage());
        }
    }
    System.out.printf("Done: %d symbols, %d fresh, %d skipped%n",
            symbols.size(), ok, skipped);
}
```

- [ ] **Step 2: Verify the class compiles**

```
cd backend && mvn -q test-compile
```
Expected: success.

- [ ] **Step 3: Manual smoke run** (requires a valid Fyers token — run `TokenGenTest1` first if needed)

```
cd backend && mvn -q test -Dtest=DownloadScriptDataTest \
  -Dcategory=CM -Dlimit=2 \
  -Dfrom=2026-04-01 -Dto=2026-04-03
```
Expected: 2 symbols logged, some day-counts > 0, CSV files appear under `data/candles/<SYM>/`. Re-run should show `skipped` increase.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/rj/DownloadScriptDataTest.java
git commit -m "feat(cli): add DownloadScriptDataTest driver"
```

---

## Task 11: `RunStrategySignalsTest` driver

**Files:**
- Create: `backend/src/test/java/com/rj/RunStrategySignalsTest.java`

- [ ] **Step 1: Implement**

```java
import com.rj.cli.*;
import com.rj.config.StrategyYamlConfig;
import com.rj.engine.SignalScanner;
import com.rj.model.Candle;
import com.rj.model.Timeframe;
import com.rj.strategy.ITradeStrategy;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

void main() {
    CliContext ctx = CliContext.bootstrap();

    String name = Objects.requireNonNull(
            System.getProperty("strategy.name"),
            "-Dstrategy.name required; available: " + ctx.strategies.keySet());
    StrategyYamlConfig cfg = ctx.strategies.get(name);
    if (cfg == null) {
        throw new IllegalArgumentException(
                "unknown strategy: " + name + "; available: " + ctx.strategies.keySet());
    }

    List<String> symbols = cfg.getSymbols();
    Timeframe tf = Timeframe.valueOf(cfg.getTimeframe());
    DateRange range = DateRange.resolve(cfg.getBacktest());

    int fired = 0;
    try (SignalCsvWriter out = SignalCsvWriter.openDefault(tf)) {
        ITradeStrategy strategy = StrategyFactory.from(cfg, name);
        for (String sym : symbols) {
            List<LocalDate> avail = ctx.candleDb.availableDates(sym);
            List<LocalDate> days = range.isEmpty() ? avail : range.businessDaysIntersect(avail);
            if (days.isEmpty()) {
                System.out.printf("  %-25s no data%n", sym);
                continue;
            }
            SignalScanner scanner = new SignalScanner(sym, tf, strategy);
            for (LocalDate d : days) {
                List<Candle> m1 = ctx.candleDb.load(sym, d);
                for (SignalScanner.SignalEvent ev : scanner.scan(m1, d)) {
                    out.write(ev);
                    System.out.printf("  signal %s %s %s conf=%.2f%n",
                            sym, d, ev.signal().getDirection(), ev.signal().getConfidence());
                    fired++;
                }
            }
        }
    }
    System.out.printf("Done. Fired %d signals across %d symbols.%n", fired, symbols.size());
}
```

- [ ] **Step 2: Verify compile**

```
cd backend && mvn -q test-compile
```

- [ ] **Step 3: Add a `backtest` block to one strategy in `intraday.yaml`** (manual edit — not a code task). E.g., under `trend_following`:
```yaml
    backtest:
      from: 2026-04-01
      to: 2026-04-03
```

- [ ] **Step 4: Manual smoke run**

```
cd backend && mvn -q test -Dtest=RunStrategySignalsTest -Dstrategy.name=trend_following
```
Expected: for each symbol in the YAML `symbols:` list, the driver prints either `no data`, or one line per firing signal. A CSV file appears at `data/signals/run-<ts>.csv` containing the header + rows.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/rj/RunStrategySignalsTest.java
git commit -m "feat(cli): add RunStrategySignalsTest driver"
```

---

## Task 12: Cross-CLI smoke + housekeeping

- [ ] **Step 1: Run the full unit suite**

```
cd backend && mvn -q test
```
Expected: all tests pass.

- [ ] **Step 2: End-to-end smoke**

1. `mvn test -Dtest=TokenGenTest1` (refresh token if needed — may be manual).
2. `mvn test -Dtest=DownloadScriptDataTest -Dcategory=CM -Dlimit=3 -Dfrom=2026-04-01 -Dto=2026-04-03`
3. `mvn test -Dtest=RunStrategySignalsTest -Dstrategy.name=trend_following -Dfrom=2026-04-01 -Dto=2026-04-03`

Confirm CSV appears, rows look reasonable.

- [ ] **Step 3: Commit any docstring / minor tweaks found during smoke**

```bash
git add -p                           # review carefully
git commit -m "chore(cli): smoke-test tweaks"
```

(Skip if nothing to commit.)

---

## Self-Review Notes (completed before handoff)

- **Spec coverage:** every spec section maps to a task — YAML BacktestBlock (T1-2), CandleAggregator (T3), SignalScanner covering "firing signals only, no position sim" (T4), each cli helper (T5-8), CliContext (T9), drivers (T10-11), e2e (T12).
- **Placeholders:** none — every step has either commands or full code.
- **Type consistency:** `SignalScanner.SignalEvent` record used consistently in `SignalCsvWriter.write(...)` and drivers. `DateRange` record fields `from()/to()` used throughout. `CandleAggregator.to(tf, m1)` signature consistent.
- **Known adaptation points flagged explicitly:**
  - `CandleAnalyzer` method name (Task 4 Step 3 note).
  - `Candle` constructor shape (Task 3 Step 1/3 note).
  - `ConfigManager.getInstance() / getProperty(key, default)` (Task 9 Step 1 note).
  - `Entry.setMinConfidence`, `Risk.setSlAtrMultiplier`, `Risk.setTpRMultiple` setter names (Task 7 Step 1 note).

These are called out at point of use so the implementing engineer checks real signatures rather than trusting the plan blindly.
