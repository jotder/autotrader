import com.rj.cli.CliContext;
import com.rj.cli.DateRange;
import com.rj.cli.SignalCsvWriter;
import com.rj.cli.StrategyFactory;
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
    ITradeStrategy strategy = StrategyFactory.from(cfg, name);

    System.out.printf("Strategy: %s | timeframe=%s | %d symbols | range=%s%n",
            name, tf, symbols.size(), range.isEmpty() ? "(all available)" : (range.from() + ".." + range.to()));

    int fired = 0, scanned = 0;
    try (SignalCsvWriter out = SignalCsvWriter.openDefault(tf)) {
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
                if (m1 == null || m1.isEmpty()) continue;
                scanned++;
                for (SignalScanner.SignalEvent ev : scanner.scan(m1, d)) {
                    out.write(ev);
                    System.out.printf("  signal %s %s %s @ %.2f conf=%.2f%n",
                            sym, d, ev.signal().getDirection(),
                            ev.signal().getSuggestedEntry(),
                            ev.signal().getConfidence());
                    fired++;
                }
            }
        }
    } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException(e);
    }
    System.out.printf("Done. Fired %d signals across %d symbol-days (%d symbols).%n",
            fired, scanned, symbols.size());
}
