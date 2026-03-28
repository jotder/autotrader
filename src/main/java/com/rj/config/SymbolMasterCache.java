package com.rj.config;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.rj.model.dim.SymbolMasterEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable in-memory cache for Fyers symbol master data.
 * <p>
 * Loads all {@code *.csv} files from the symbol master directory (e.g.
 * {@code data/symbol_master/}). Each file represents one exchange–segment
 * pair (e.g. NSE_CM.csv, NSE_FO.csv, MCX_COM.csv).
 * <p>
 * Thread-safe — all collections are unmodifiable after construction.
 */
public final class SymbolMasterCache {

    private static final Logger log = LoggerFactory.getLogger(SymbolMasterCache.class);

    private final Map<String, SymbolMasterEntry> byTicker;
    private final Map<String, List<SymbolMasterEntry>> byUnderlying;
    private final Map<Long, List<SymbolMasterEntry>> byExchangeSegment;

    private SymbolMasterCache(List<SymbolMasterEntry> entries) {
        this.byTicker = entries.stream()
                .collect(Collectors.toUnmodifiableMap(
                        SymbolMasterEntry::symbolTicker,
                        e -> e,
                        (a, _) -> a));  // keep first on duplicate

        this.byUnderlying = entries.stream()
                .filter(e -> e.underlyingSymbol() != null && !e.underlyingSymbol().isBlank())
                .collect(Collectors.groupingBy(
                        SymbolMasterEntry::underlyingSymbol,
                        Collectors.toUnmodifiableList()));

        this.byExchangeSegment = entries.stream()
                .collect(Collectors.groupingBy(
                        e -> packPair(e.exchange(), e.segment()),
                        Collectors.toUnmodifiableList()));
    }

    // ── Factory ─────────────────────────────────────────────────────────────

    /**
     * Load specified symbol master CSVs from the given directory.
     *
     * @param symbolMasterDir path (e.g. {@code data/symbol_master})
     * @param allowedSegments set of segments to load (e.g. {"NSE_CM", "NSE_FO", "MCX_COM"})
     * @return an immutable cache
     */
    public static SymbolMasterCache load(Path symbolMasterDir, Set<String> allowedSegments) {
        log.info("Loading symbol master data from {} (Segments: {})", symbolMasterDir, allowedSegments);

        if (!Files.isDirectory(symbolMasterDir)) {
            log.warn("Symbol master directory not found: {}", symbolMasterDir);
            return new SymbolMasterCache(List.of());
        }

        var allEntries = new ArrayList<SymbolMasterEntry>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(symbolMasterDir, "*.csv")) {
            for (Path csvFile : ds) {
                String fileName = csvFile.getFileName().toString().replace(".csv", "");
                if (allowedSegments != null && !allowedSegments.isEmpty() && !allowedSegments.contains(fileName)) {
                    log.debug("Skipping segment: {}", fileName);
                    continue;
                }

                List<SymbolMasterEntry> entries = loadOneFile(csvFile);
                allEntries.addAll(entries);
                log.info("  {} → {} symbols", csvFile.getFileName(), entries.size());
            }
        } catch (IOException e) {
            log.error("Failed to scan symbol master directory: {}", e.getMessage());
        }

        var cache = new SymbolMasterCache(allEntries);
        log.info("Symbol master loaded: {} total symbols, {} underlyings, {} exchange-segment groups",
                cache.byTicker.size(),
                cache.byUnderlying.size(),
                cache.byExchangeSegment.size());
        return cache;
    }

    /**
     * Load all symbol master CSVs from the given directory.
     *
     * @param symbolMasterDir path (e.g. {@code data/symbol_master})
     * @return an immutable cache
     */
    public static SymbolMasterCache load(Path symbolMasterDir) {
        return load(symbolMasterDir, Set.of());
    }

    // ── Query API ───────────────────────────────────────────────────────────

    /** Lookup by symbol ticker (e.g. "NSE:SBIN-EQ"). */
    public Optional<SymbolMasterEntry> byTicker(String symbolTicker) {
        return Optional.ofNullable(byTicker.get(symbolTicker));
    }

    /** All symbols for a given underlying (e.g. "NIFTY" → all NIFTY futures/options). */
    public List<SymbolMasterEntry> byUnderlying(String underlyingSymbol) {
        return byUnderlying.getOrDefault(underlyingSymbol, List.of());
    }

    /** All symbols for an exchange–segment pair (e.g. exchange=10, segment=11 → NSE F&O). */
    public List<SymbolMasterEntry> byExchangeSegment(int exchange, int segment) {
        return byExchangeSegment.getOrDefault(packPair(exchange, segment), List.of());
    }

    /** Total number of symbols loaded. */
    public int size() { return byTicker.size(); }

    /** All loaded symbol tickers. */
    public Set<String> allTickers() { return byTicker.keySet(); }

    /** All unique underlying symbols. */
    public Set<String> allUnderlyings() { return byUnderlying.keySet(); }

    /**
     * Search by partial ticker or underlying (case-insensitive substring match).
     * Returns at most {@code limit} results.
     */
    public List<SymbolMasterEntry> search(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.toUpperCase();
        return byTicker.values().stream()
                .filter(e -> e.symbolTicker().toUpperCase().contains(q)
                        || e.underlyingSymbol().toUpperCase().contains(q)
                        || e.symbolDetails().toUpperCase().contains(q))
                .limit(limit)
                .toList();
    }

    // ── CSV Loader ──────────────────────────────────────────────────────────

    private static List<SymbolMasterEntry> loadOneFile(Path csvFile) {
        var results = new ArrayList<SymbolMasterEntry>();
        try {
            CsvMapper mapper = new CsvMapper();
            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            try (MappingIterator<Map<String, String>> it =
                         mapper.readerFor(Map.class).with(schema).readValues(csvFile.toFile())) {
                while (it.hasNext()) {
                    Map<String, String> rawRow = it.next();
                    // Strict column count check (Fyers master has 21 columns, 
                    // but Jackson deduplicates 'Reserved column' headers into one key)
                    if (rawRow.size() < 19) {
                        log.error("Malformed row in {}: expected >= 19 unique columns (from 21 raw), got {}", csvFile.getFileName(), rawRow.size());
                        continue;
                    }

                    // Normalize: trim header keys (CSV headers may have leading spaces)
                    var row = normalizeKeys(rawRow);
                    try {
                        SymbolMasterEntry entry = parseRow(row);
                        validateEntry(entry, csvFile.getFileName().toString());
                        results.add(entry);
                    } catch (Exception e) {
                        log.warn("Validation failed for row in {}: {}", csvFile.getFileName(), e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to load {}: {}", csvFile.getFileName(), e.getMessage());
        }
        return results;
    }

    private static void validateEntry(SymbolMasterEntry entry, String fileName) {
        if (entry.fyToken() == null || entry.fyToken().isBlank()) {
            throw new IllegalArgumentException("Missing Fytoken");
        }
        if (entry.symbolTicker() == null || entry.symbolTicker().isBlank()) {
            throw new IllegalArgumentException("Missing Symbol ticker");
        }
        if (SymbolFormatParser.parse(entry.symbolTicker()) == null) {
            throw new IllegalArgumentException("Invalid Symbol ticker format: " + entry.symbolTicker());
        }
    }

    /**
     * Trim leading/trailing whitespace from all map keys.
     * Jackson CSV preserves header whitespace; Fyers CSVs have spaces after commas.
     */
    private static Map<String, String> normalizeKeys(Map<String, String> raw) {
        var normalized = new LinkedHashMap<String, String>();
        for (var entry : raw.entrySet()) {
            normalized.put(entry.getKey().trim(), entry.getValue());
        }
        return normalized;
    }

    private static SymbolMasterEntry parseRow(Map<String, String> row) {
        return new SymbolMasterEntry(
                str(row, "Fytoken"),
                str(row, "Symbol Details"),
                intVal(row, "Exchange Instrument type"),
                intVal(row, "Minimum lot size"),
                doubleVal(row, "Tick size"),
                str(row, "ISIN"),
                str(row, "Trading Session"),
                str(row, "Last update date"),
                str(row, "Expiry date"),
                str(row, "Symbol ticker"),
                intVal(row, "Exchange"),
                intVal(row, "Segment"),
                intVal(row, "Scrip code"),
                str(row, "Underlying symbol"),
                str(row, "Underlying scrip code"),
                doubleVal(row, "Strike price"),
                str(row, "Option type"),
                str(row, "Underlying FyToken"),
                str(row, "Reserved column"),
                nthReserved(row, 1),
                nthReserved(row, 2)
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String str(Map<String, String> row, String key) {
        String v = row.get(key);
        return v != null ? v.trim() : "";
    }

    private static int intVal(Map<String, String> row, String key) {
        String v = str(row, key);
        if (v.isEmpty()) return 0;
        try {
            // Handle scientific notation from Excel (e.g. "1.01E+14")
            if (v.contains("E") || v.contains("e")) {
                return (int) Double.parseDouble(v);
            }
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + key + ": " + v);
        }
    }

    private static double doubleVal(Map<String, String> row, String key) {
        String v = str(row, key);
        if (v.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid double for " + key + ": " + v);
        }
    }

    /**
     * The CSV has multiple "Reserved column" headers which Jackson deduplicates
     * by appending suffixes. We pick the nth reserved field by iterating keys.
     */
    private static String nthReserved(Map<String, String> row, int n) {
        int count = 0;
        for (var entry : row.entrySet()) {
            if (entry.getKey().contains("Reserved")) {
                if (count == n) return entry.getValue() != null ? entry.getValue().trim() : "";
                count++;
            }
        }
        return "";
    }

    private static long packPair(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }
}
