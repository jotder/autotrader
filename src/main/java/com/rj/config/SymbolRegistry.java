package com.rj.config;

import com.rj.fyers.SymbolParser;
import com.rj.model.InstrumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Global symbol registry — single source of truth for all tradeable symbols.
 * <p>
 * Loads {@code config/symbols.yaml} grouped by {@link MarketCategory} (CM, FO, COM).
 * WebSocket subscribes to ALL symbols; strategies reference a subset.
 * Immutable after construction — safe for concurrent reads.
 */
public final class SymbolRegistry {

    private static final Logger log = LoggerFactory.getLogger(SymbolRegistry.class);

    private final Map<MarketCategory, List<String>> categoryMap;
    private final Map<String, InstrumentInfo> instrumentMap;
    private final Set<String> allSymbolSet;
    private final String[] allSymbolArray;

    private SymbolRegistry(Map<MarketCategory, List<String>> categoryMap, 
                           Map<String, InstrumentInfo> instrumentMap) {
        this.categoryMap = Collections.unmodifiableMap(categoryMap);
        this.instrumentMap = Collections.unmodifiableMap(instrumentMap);

        // Pre-compute flat set and array for fast lookups
        Set<String> flat = new LinkedHashSet<>();
        for (MarketCategory cat : MarketCategory.values()) {
            flat.addAll(this.categoryMap.getOrDefault(cat, List.of()));
        }
        this.allSymbolSet = Collections.unmodifiableSet(flat);
        this.allSymbolArray = flat.toArray(String[]::new);
    }

    // ── Factory ─────────────────────────────────────────────────────────────────

    /**
     * Load and parse a symbols YAML file.
     */
    @SuppressWarnings("unchecked")
    public static SymbolRegistry load(Path symbolsYamlPath) {
        if (!Files.exists(symbolsYamlPath)) {
            throw new IllegalArgumentException("Symbol registry file not found: " + symbolsYamlPath);
        }

        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(symbolsYamlPath)) {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(in);
            if (parsed == null || !(parsed instanceof Map)) {
                throw new IllegalArgumentException("Symbol registry file is empty or malformed: " + symbolsYamlPath);
            }
            root = (Map<String, Object>) parsed;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read symbol registry: " + symbolsYamlPath, e);
        }

        Object symbolsObj = root.get("symbols");
        if (symbolsObj == null || !(symbolsObj instanceof Map)) {
            throw new IllegalArgumentException(
                    "Symbol registry must have a 'symbols' root key with category maps: " + symbolsYamlPath);
        }

        Map<String, Object> symbolsMap = (Map<String, Object>) symbolsObj;
        Set<String> seen = new HashSet<>();
        Map<MarketCategory, List<String>> categoryMap = new EnumMap<>(MarketCategory.class);
        Map<String, InstrumentInfo> instrumentMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : symbolsMap.entrySet()) {
            String key = entry.getKey();
            MarketCategory cat = MarketCategory.fromYamlKey(key);

            List<String> rawSymbols = parseSymbolList(entry.getValue(), key);

            List<String> deduped = new ArrayList<>();
            for (String symbol : rawSymbols) {
                if (seen.add(symbol)) {
                    deduped.add(symbol);
                    
                    // Derive metadata
                    var meta = SymbolParser.parse(symbol);
                    int lotSize = SymbolParser.getLotSize(meta.baseSymbol());
                    instrumentMap.put(symbol, InstrumentInfo.fromType(meta.type(), lotSize));
                }
            }
            categoryMap.put(cat, Collections.unmodifiableList(deduped));
        }

        for (MarketCategory cat : MarketCategory.values()) {
            categoryMap.putIfAbsent(cat, List.of());
        }

        SymbolRegistry registry = new SymbolRegistry(categoryMap, instrumentMap);
        log.info("SymbolRegistry loaded: {} symbols [CM={}, FO={}, COM={}]",
                registry.size(),
                registry.symbolsFor(MarketCategory.CM).size(),
                registry.symbolsFor(MarketCategory.FO).size(),
                registry.symbolsFor(MarketCategory.COM).size());

        return registry;
    }

    // ── Query API ───────────────────────────────────────────────────────────────

    public String[] allSymbols() { return allSymbolArray.clone(); }
    public Set<String> allSymbolSet() { return allSymbolSet; }
    public List<String> symbolsFor(MarketCategory category) { return categoryMap.getOrDefault(category, List.of()); }
    public boolean contains(String symbol) { return symbol != null && allSymbolSet.contains(symbol); }
    public int size() { return allSymbolSet.size(); }
    
    /** Retrieve instrument metadata. Returns EQUITY_DEFAULT if unknown. */
    public InstrumentInfo getInstrumentInfo(String symbol) {
        return instrumentMap.getOrDefault(symbol, InstrumentInfo.EQUITY_DEFAULT);
    }

    /** The category map (unmodifiable). */
    public Map<MarketCategory, List<String>> categoryMap() {
        return categoryMap;
    }

    // ── Validation ──────────────────────────────────────────────────────────────

    /**
     * Validate that all symbols in a strategy's symbol list exist in the global registry.
     *
     * @param strategyName the strategy name (for logging)
     * @param symbols      the strategy's configured symbols
     * @return list of invalid symbols (empty if all valid)
     */
    public List<String> validateStrategySymbols(String strategyName, List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return List.of();

        List<String> invalid = symbols.stream()
                .filter(s -> !allSymbolSet.contains(s))
                .collect(Collectors.toList());

        if (!invalid.isEmpty()) {
            log.warn("Strategy '{}' references {} symbol(s) not in global registry: {}",
                    strategyName, invalid.size(), invalid);
        }
        return invalid;
    }

    // ── Internal helpers ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<String> parseSymbolList(Object value, String categoryKey) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(e -> e instanceof String)
                    .map(e -> ((String) e).trim())
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        log.warn("Category '{}' has no valid symbol list — treating as empty", categoryKey);
        return List.of();
    }
}
