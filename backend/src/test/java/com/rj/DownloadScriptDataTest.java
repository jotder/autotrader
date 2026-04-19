import com.rj.cli.CliContext;
import com.rj.cli.DateRange;
import com.rj.cli.SymbolSelector;
import com.rj.engine.CandleDownloader;

import java.util.List;

void main() {
    CliContext ctx = CliContext.bootstrap();

    String category = System.getProperty("category", "CM");
    int limit  = Integer.parseInt(System.getProperty("limit",  "0"));
    int offset = Integer.parseInt(System.getProperty("offset", "0"));

    List<String> symbols = SymbolSelector.select(ctx.symbols, category, limit, offset);
    DateRange range = DateRange.fromSystemProps();
    CandleDownloader dl = new CandleDownloader(ctx.marketData, ctx.candleDb);

    System.out.printf("Downloading %d symbols from %s to %s%n",
            symbols.size(), range.from(), range.to());

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
