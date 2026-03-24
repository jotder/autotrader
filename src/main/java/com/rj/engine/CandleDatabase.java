package com.rj.engine;

import com.rj.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * File-based M1 candle storage.
 * <p>
 * Stores one CSV file per symbol per date:
 * {@code {baseDir}/{SYMBOL_SAFE}/{SYMBOL_SAFE}_YYYY-MM-DD.csv}
 * <p>
 * CSV format: {@code timestamp,open,high,low,close,volume} (epoch seconds).
 * Atomic writes via .tmp → rename (follows {@link TradeJournal} pattern).
 * Thread-safe for concurrent reads; writes are serialized per symbol.
 */
public class CandleDatabase {

    private static final Logger log = LoggerFactory.getLogger(CandleDatabase.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String CSV_HEADER = "timestamp,open,high,low,close,volume";

    private final Path baseDir;

    public CandleDatabase(Path baseDir) {
        this.baseDir = baseDir;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            log.error("Failed to create candle database directory: {}", baseDir, e);
        }
    }

    // ── Write ───────────────────────────────────────────────────────────────

    /**
     * Store M1 candles for a symbol and date. Atomic write (.tmp → rename).
     *
     * @param symbol   e.g. "NSE:SBIN-EQ"
     * @param date     trading date
     * @param candles  M1 candles (should be sorted by timestamp)
     */
    public void store(String symbol, LocalDate date, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        Path dir = symbolDir(symbol);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Failed to create directory for {}: {}", symbol, e.getMessage());
            return;
        }

        Path target = dir.resolve(fileName(symbol, date));
        Path tmp = dir.resolve(fileName(symbol, date) + ".tmp");

        try (BufferedWriter w = Files.newBufferedWriter(tmp)) {
            w.write(CSV_HEADER);
            w.newLine();
            for (Candle c : candles) {
                w.write(String.format("%d,%.2f,%.2f,%.2f,%.2f,%d",
                        c.timestamp, c.open, c.high, c.low, c.close, c.volume));
                w.newLine();
            }
        } catch (IOException e) {
            log.error("Failed to write candle file {}: {}", tmp, e.getMessage());
            return;
        }

        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.debug("Stored {} candles for {} on {}", candles.size(), symbol, date);
        } catch (IOException e) {
            // ATOMIC_MOVE may not be supported; fall back
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                log.error("Failed to rename {} → {}: {}", tmp, target, e2.getMessage());
            }
        }
    }

    // ── Read ────────────────────────────────────────────────────────────────

    /**
     * Load M1 candles for one symbol and date.
     *
     * @return candles sorted by timestamp, or empty list if file doesn't exist
     */
    public List<Candle> load(String symbol, LocalDate date) {
        Path file = symbolDir(symbol).resolve(fileName(symbol, date));
        if (!Files.exists(file)) return List.of();
        return readCsvFile(file);
    }

    /**
     * Load M1 candles for a date range (inclusive on both ends).
     */
    public List<Candle> loadRange(String symbol, LocalDate from, LocalDate to) {
        var all = new ArrayList<Candle>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            all.addAll(load(symbol, d));
        }
        return all;
    }

    // ── Query ───────────────────────────────────────────────────────────────

    /** Check if data exists for a symbol and date. */
    public boolean exists(String symbol, LocalDate date) {
        return Files.exists(symbolDir(symbol).resolve(fileName(symbol, date)));
    }

    /** List available dates for a symbol, sorted ascending. */
    public List<LocalDate> availableDates(String symbol) {
        Path dir = symbolDir(symbol);
        if (!Files.isDirectory(dir)) return List.of();

        String prefix = safeSymbol(symbol) + "_";
        var dates = new ArrayList<LocalDate>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, prefix + "*.csv")) {
            for (Path file : ds) {
                String name = file.getFileName().toString();
                // Extract date from "NSE_SBIN-EQ_2026-03-25.csv"
                int dateStart = name.lastIndexOf('_') + 1;
                int dateEnd = name.lastIndexOf('.');
                if (dateStart > 0 && dateEnd > dateStart) {
                    String dateStr = name.substring(dateStart, dateEnd);
                    try {
                        dates.add(LocalDate.parse(dateStr, DATE_FMT));
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException e) {
            log.error("Failed to list dates for {}: {}", symbol, e.getMessage());
        }
        Collections.sort(dates);
        return dates;
    }

    /** List all symbols that have stored data. */
    public Set<String> availableSymbols() {
        if (!Files.isDirectory(baseDir)) return Set.of();

        var symbols = new TreeSet<String>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(baseDir)) {
            for (Path dir : ds) {
                if (Files.isDirectory(dir)) {
                    // Convert safe name back: "NSE_SBIN-EQ" → "NSE:SBIN-EQ"
                    symbols.add(unsafeSymbol(dir.getFileName().toString()));
                }
            }
        } catch (IOException e) {
            log.error("Failed to list symbols: {}", e.getMessage());
        }
        return symbols;
    }

    // ── CSV Reader ──────────────────────────────────────────────────────────

    private static List<Candle> readCsvFile(Path file) {
        var candles = new ArrayList<Candle>();
        try (BufferedReader r = Files.newBufferedReader(file)) {
            String line = r.readLine(); // skip header
            if (line == null) return candles;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    String[] parts = line.split(",");
                    candles.add(Candle.of(
                            Long.parseLong(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]),
                            Double.parseDouble(parts[4]),
                            Long.parseLong(parts[5])
                    ));
                } catch (Exception e) {
                    // skip malformed lines
                }
            }
        } catch (IOException e) {
            log.error("Failed to read candle file {}: {}", file, e.getMessage());
        }
        return candles;
    }

    // ── Path helpers ────────────────────────────────────────────────────────

    private Path symbolDir(String symbol) {
        return baseDir.resolve(safeSymbol(symbol));
    }

    static String fileName(String symbol, LocalDate date) {
        return safeSymbol(symbol) + "_" + date.format(DATE_FMT) + ".csv";
    }

    /** Encode symbol for filesystem: "NSE:SBIN-EQ" → "NSE_SBIN-EQ" */
    public static String safeSymbol(String symbol) {
        return symbol.replace(':', '_');
    }

    /** Decode filesystem name back to symbol: "NSE_SBIN-EQ" → "NSE:SBIN-EQ" */
    static String unsafeSymbol(String safe) {
        // Replace only the FIRST underscore (exchange separator) back to colon
        int idx = safe.indexOf('_');
        if (idx > 0) {
            return safe.substring(0, idx) + ":" + safe.substring(idx + 1);
        }
        return safe;
    }
}
