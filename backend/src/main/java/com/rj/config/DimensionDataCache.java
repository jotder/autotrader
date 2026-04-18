package com.rj.config;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.rj.model.dim.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable in-memory cache for all dimension/reference CSV tables.
 * <p>
 * Loaded eagerly at startup via {@link #load(Path)}. Thread-safe — all
 * collections are unmodifiable after construction. Follows the same pattern
 * as {@link SymbolRegistry}.
 */
public final class DimensionDataCache {

    private static final Logger log = LoggerFactory.getLogger(DimensionDataCache.class);

    private final List<Exchange> exchanges;
    private final Map<Integer, Exchange> exchangeByCode;
    private final List<Segment> segments;
    private final Map<Integer, Segment> segmentByCode;
    private final List<ExchangeSegment> exchangeSegments;
    private final Set<Long> validExchangeSegmentPairs;
    private final List<InstrumentType> instrumentTypes;
    private final Map<Integer, List<InstrumentType>> instrumentTypeByCode;
    private final List<OrderSide> orderSides;
    private final List<OrderSource> orderSources;
    private final List<OrderStatus> orderStatuses;
    private final Map<Integer, OrderStatus> orderStatusByCode;
    private final List<OrderType> orderTypes;
    private final Map<Integer, OrderType> orderTypeByCode;
    private final List<PositionSide> positionSides;
    private final List<ProductType> productTypes;
    private final Map<String, ProductType> productTypeByCode;
    private final List<HoldingType> holdingTypes;

    private DimensionDataCache(Builder b) {
        this.exchanges = List.copyOf(b.exchanges);
        this.exchangeByCode = Map.copyOf(b.exchangeByCode);
        this.segments = List.copyOf(b.segments);
        this.segmentByCode = Map.copyOf(b.segmentByCode);
        this.exchangeSegments = List.copyOf(b.exchangeSegments);
        this.validExchangeSegmentPairs = Set.copyOf(b.validExchangeSegmentPairs);
        this.instrumentTypes = List.copyOf(b.instrumentTypes);
        this.instrumentTypeByCode = b.instrumentTypeByCode.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        this.orderSides = List.copyOf(b.orderSides);
        this.orderSources = List.copyOf(b.orderSources);
        this.orderStatuses = List.copyOf(b.orderStatuses);
        this.orderStatusByCode = Map.copyOf(b.orderStatusByCode);
        this.orderTypes = List.copyOf(b.orderTypes);
        this.orderTypeByCode = Map.copyOf(b.orderTypeByCode);
        this.positionSides = List.copyOf(b.positionSides);
        this.productTypes = List.copyOf(b.productTypes);
        this.productTypeByCode = Map.copyOf(b.productTypeByCode);
        this.holdingTypes = List.copyOf(b.holdingTypes);
    }

    // ── Factory ─────────────────────────────────────────────────────────────

    /**
     * Load all dimension CSVs from the given directory.
     *
     * @param dimDir path to the dimension directory (e.g. {@code data/dim})
     * @return an immutable cache
     */
    public static DimensionDataCache load(Path dimDir) {
        log.info("Loading dimension data from {}", dimDir);
        var b = new Builder();

        // exchanges.csv — "Possible Values,Description"
        b.exchanges = loadTwoColumnIntKey(dimDir, "exchanges.csv", Exchange::new);
        b.exchangeByCode = indexBy(b.exchanges, Exchange::code);

        // segments.csv — "Possible Values,Description"
        b.segments = loadTwoColumnIntKey(dimDir, "segments.csv", Segment::new);
        b.segmentByCode = indexBy(b.segments, Segment::code);

        // exchange_segment.csv — "Exchange,Segment,Exchange Code,Segment Code"
        b.exchangeSegments = loadExchangeSegments(dimDir);
        b.validExchangeSegmentPairs = b.exchangeSegments.stream()
                .map(es -> packPair(es.exchangeCode(), es.segmentCode()))
                .collect(Collectors.toSet());

        // instrument_types.csv — "Values,Description,Segment,"
        b.instrumentTypes = loadInstrumentTypes(dimDir);
        b.instrumentTypeByCode = new HashMap<>();
        for (var it : b.instrumentTypes) {
            b.instrumentTypeByCode.computeIfAbsent(it.code(), k -> new ArrayList<>()).add(it);
        }

        // order_sides.csv — "Possible Values,Description,,"
        b.orderSides = loadTwoColumnIntKey(dimDir, "order_sides.csv", OrderSide::new);

        // order_sources.csv — "Possible Values,Description"
        b.orderSources = loadTwoColumnStringKey(dimDir, "order_sources.csv", OrderSource::new);

        // order_status.csv — "Possible Values,Description,,"
        b.orderStatuses = loadTwoColumnIntKey(dimDir, "order_status.csv", OrderStatus::new);
        b.orderStatusByCode = indexBy(b.orderStatuses, OrderStatus::code);

        // order_types.csv — "Possible Values,Description,,"
        b.orderTypes = loadTwoColumnIntKey(dimDir, "order_types.csv", OrderType::new);
        b.orderTypeByCode = indexBy(b.orderTypes, OrderType::code);

        // position_sides.csv — "Possible Values,Description,,"
        b.positionSides = loadTwoColumnIntKey(dimDir, "position_sides.csv", PositionSide::new);

        // product_types.csv — "Possible Values,Description,,"
        b.productTypes = loadTwoColumnStringKey(dimDir, "product_types.csv", ProductType::new);
        b.productTypeByCode = b.productTypes.stream()
                .collect(Collectors.toMap(ProductType::code, Function.identity()));

        // holding_types.csv — "Possible Values,Description,,"
        b.holdingTypes = loadTwoColumnStringKey(dimDir, "holding_types.csv", HoldingType::new);

        var cache = new DimensionDataCache(b);
        log.info("Dimension data loaded: {} exchanges, {} segments, {} exchange-segments, " +
                        "{} instrument types, {} order types, {} product types",
                cache.exchanges.size(), cache.segments.size(), cache.exchangeSegments.size(),
                cache.instrumentTypes.size(), cache.orderTypes.size(), cache.productTypes.size());
        return cache;
    }

    // ── Query API ───────────────────────────────────────────────────────────

    public List<Exchange> exchanges() { return exchanges; }
    public Optional<Exchange> exchangeByCode(int code) { return Optional.ofNullable(exchangeByCode.get(code)); }

    public List<Segment> segments() { return segments; }
    public Optional<Segment> segmentByCode(int code) { return Optional.ofNullable(segmentByCode.get(code)); }

    public List<ExchangeSegment> exchangeSegments() { return exchangeSegments; }
    public boolean isValidExchangeSegment(int exchangeCode, int segmentCode) {
        return validExchangeSegmentPairs.contains(packPair(exchangeCode, segmentCode));
    }

    public List<InstrumentType> instrumentTypes() { return instrumentTypes; }
    public List<InstrumentType> instrumentTypesByCode(int code) {
        return instrumentTypeByCode.getOrDefault(code, List.of());
    }

    public List<OrderSide> orderSides() { return orderSides; }
    public List<OrderSource> orderSources() { return orderSources; }

    public List<OrderStatus> orderStatuses() { return orderStatuses; }
    public Optional<OrderStatus> orderStatusByCode(int code) { return Optional.ofNullable(orderStatusByCode.get(code)); }

    public List<OrderType> orderTypes() { return orderTypes; }
    public Optional<OrderType> orderTypeByCode(int code) { return Optional.ofNullable(orderTypeByCode.get(code)); }

    public List<PositionSide> positionSides() { return positionSides; }

    public List<ProductType> productTypes() { return productTypes; }
    public Optional<ProductType> productTypeByCode(String code) { return Optional.ofNullable(productTypeByCode.get(code)); }

    public List<HoldingType> holdingTypes() { return holdingTypes; }

    /**
     * Returns all dimension tables as a name→list map (for REST serialization).
     */
    public Map<String, Object> allTables() {
        var map = new LinkedHashMap<String, Object>();
        map.put("exchanges", exchanges);
        map.put("segments", segments);
        map.put("exchangeSegments", exchangeSegments);
        map.put("instrumentTypes", instrumentTypes);
        map.put("orderSides", orderSides);
        map.put("orderSources", orderSources);
        map.put("orderStatuses", orderStatuses);
        map.put("orderTypes", orderTypes);
        map.put("positionSides", positionSides);
        map.put("productTypes", productTypes);
        map.put("holdingTypes", holdingTypes);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns a single dimension table by name, or empty if unknown.
     */
    public Optional<List<?>> tableByName(String name) {
        return switch (name) {
            case "exchanges" -> Optional.of(exchanges);
            case "segments" -> Optional.of(segments);
            case "exchangeSegments" -> Optional.of(exchangeSegments);
            case "instrumentTypes" -> Optional.of(instrumentTypes);
            case "orderSides" -> Optional.of(orderSides);
            case "orderSources" -> Optional.of(orderSources);
            case "orderStatuses" -> Optional.of(orderStatuses);
            case "orderTypes" -> Optional.of(orderTypes);
            case "positionSides" -> Optional.of(positionSides);
            case "productTypes" -> Optional.of(productTypes);
            case "holdingTypes" -> Optional.of(holdingTypes);
            default -> Optional.empty();
        };
    }

    // ── CSV Loaders ─────────────────────────────────────────────────────────

    /**
     * Load a 2-column CSV with integer key: "code,description" (with possible trailing empty cols).
     */
    private static <T> List<T> loadTwoColumnIntKey(Path dir, String fileName,
                                                    IntStringFactory<T> factory) {
        Path file = dir.resolve(fileName);
        if (!Files.exists(file)) {
            log.warn("Dimension file not found: {}", file);
            return List.of();
        }
        var results = new ArrayList<T>();
        try {
            CsvMapper mapper = new CsvMapper();
            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            try (MappingIterator<Map<String, String>> it =
                         mapper.readerFor(Map.class).with(schema).readValues(file.toFile())) {
                while (it.hasNext()) {
                    Map<String, String> row = it.next();
                    // First column is always code, second is description
                    String codeStr = firstValue(row);
                    String desc = secondValue(row);
                    if (codeStr == null || codeStr.isBlank()) continue;
                    try {
                        results.add(factory.create(Integer.parseInt(codeStr.trim()), desc.trim()));
                    } catch (NumberFormatException e) {
                        log.debug("Skipping non-integer row in {}: {}", fileName, codeStr);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to load {}: {}", fileName, e.getMessage());
        }
        return results;
    }

    /**
     * Load a 2-column CSV with string key: "code,description" (with possible trailing empty cols).
     */
    private static <T> List<T> loadTwoColumnStringKey(Path dir, String fileName,
                                                       StringStringFactory<T> factory) {
        Path file = dir.resolve(fileName);
        if (!Files.exists(file)) {
            log.warn("Dimension file not found: {}", file);
            return List.of();
        }
        var results = new ArrayList<T>();
        try {
            CsvMapper mapper = new CsvMapper();
            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            try (MappingIterator<Map<String, String>> it =
                         mapper.readerFor(Map.class).with(schema).readValues(file.toFile())) {
                while (it.hasNext()) {
                    Map<String, String> row = it.next();
                    String code = firstValue(row);
                    String desc = secondValue(row);
                    if (code == null || code.isBlank()) continue;
                    results.add(factory.create(code.trim(), desc.trim()));
                }
            }
        } catch (IOException e) {
            log.error("Failed to load {}: {}", fileName, e.getMessage());
        }
        return results;
    }

    private static List<ExchangeSegment> loadExchangeSegments(Path dir) {
        Path file = dir.resolve("exchange_segment.csv");
        if (!Files.exists(file)) {
            log.warn("Dimension file not found: {}", file);
            return List.of();
        }
        var results = new ArrayList<ExchangeSegment>();
        try {
            CsvMapper mapper = new CsvMapper();
            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            try (MappingIterator<Map<String, String>> it =
                         mapper.readerFor(Map.class).with(schema).readValues(file.toFile())) {
                while (it.hasNext()) {
                    Map<String, String> row = it.next();
                    String exchange = row.getOrDefault("Exchange", "").trim();
                    String segment = row.getOrDefault("Segment", "").trim();
                    String exCodeStr = row.getOrDefault("Exchange Code", "").trim();
                    String segCodeStr = row.getOrDefault("Segment Code", "").trim();
                    if (exchange.isEmpty() || exCodeStr.isEmpty()) continue;
                    results.add(new ExchangeSegment(exchange, segment,
                            Integer.parseInt(exCodeStr), Integer.parseInt(segCodeStr)));
                }
            }
        } catch (IOException e) {
            log.error("Failed to load exchange_segment.csv: {}", e.getMessage());
        }
        return results;
    }

    private static List<InstrumentType> loadInstrumentTypes(Path dir) {
        Path file = dir.resolve("instrument_types.csv");
        if (!Files.exists(file)) {
            log.warn("Dimension file not found: {}", file);
            return List.of();
        }
        var results = new ArrayList<InstrumentType>();
        try {
            CsvMapper mapper = new CsvMapper();
            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            try (MappingIterator<Map<String, String>> it =
                         mapper.readerFor(Map.class).with(schema).readValues(file.toFile())) {
                while (it.hasNext()) {
                    Map<String, String> row = it.next();
                    String codeStr = row.getOrDefault("Values", "").trim();
                    String desc = row.getOrDefault("Description", "").trim();
                    String segment = row.getOrDefault("Segment", "").trim();
                    if (codeStr.isEmpty()) continue;
                    try {
                        results.add(new InstrumentType(Integer.parseInt(codeStr), desc, segment));
                    } catch (NumberFormatException e) {
                        log.debug("Skipping non-integer row in instrument_types.csv: {}", codeStr);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to load instrument_types.csv: {}", e.getMessage());
        }
        return results;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String firstValue(Map<String, String> row) {
        var it = row.values().iterator();
        return it.hasNext() ? it.next() : null;
    }

    private static String secondValue(Map<String, String> row) {
        var it = row.values().iterator();
        if (it.hasNext()) it.next();
        return it.hasNext() ? it.next() : "";
    }

    private static long packPair(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    private static <T, K> Map<K, T> indexBy(List<T> list, Function<T, K> keyFn) {
        return list.stream().collect(Collectors.toMap(keyFn, Function.identity(), (a, _) -> a));
    }

    @FunctionalInterface
    private interface IntStringFactory<T> { T create(int code, String name); }

    @FunctionalInterface
    private interface StringStringFactory<T> { T create(String code, String name); }

    private static class Builder {
        List<Exchange> exchanges = List.of();
        Map<Integer, Exchange> exchangeByCode = Map.of();
        List<Segment> segments = List.of();
        Map<Integer, Segment> segmentByCode = Map.of();
        List<ExchangeSegment> exchangeSegments = List.of();
        Set<Long> validExchangeSegmentPairs = Set.of();
        List<InstrumentType> instrumentTypes = List.of();
        Map<Integer, List<InstrumentType>> instrumentTypeByCode = Map.of();
        List<OrderSide> orderSides = List.of();
        List<OrderSource> orderSources = List.of();
        List<OrderStatus> orderStatuses = List.of();
        Map<Integer, OrderStatus> orderStatusByCode = Map.of();
        List<OrderType> orderTypes = List.of();
        Map<Integer, OrderType> orderTypeByCode = Map.of();
        List<PositionSide> positionSides = List.of();
        List<ProductType> productTypes = List.of();
        Map<String, ProductType> productTypeByCode = Map.of();
        List<HoldingType> holdingTypes = List.of();
    }
}
