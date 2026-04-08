package com.rj.engine.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rj.broker.IMarketDataAdapter;
import com.rj.config.OptionChainConfig;
import com.rj.config.SymbolMasterCache;
import com.rj.model.OptionChainResult;
import com.rj.model.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages the live options chain cache.
 *
 * <p><b>Three refresh paths:</b>
 * <ol>
 *   <li>Scheduled — polls all tracked underlyings every {@code pollIntervalSeconds}</li>
 *   <li>Event-triggered — called by {@link com.rj.engine.CandleService} on strong moves</li>
 *   <li>On-demand — REST endpoint; cold-start synchronous fetch if cache empty</li>
 * </ol>
 */
public class OptionChainService {

    private static final Logger log = LoggerFactory.getLogger(OptionChainService.class);

    private final IMarketDataAdapter marketDataAdapter;
    private final OptionChainConfig config;
    private final SymbolMasterCache symbolMasterCache;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, OptionChainSnapshot> cache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private ScheduledExecutorService scheduler;
    private List<String> trackedUnderlyings = List.of();

    public OptionChainService(IMarketDataAdapter marketDataAdapter,
                              OptionChainConfig config,
                              SymbolMasterCache symbolMasterCache,
                              ObjectMapper objectMapper) {
        this.marketDataAdapter = marketDataAdapter;
        this.config = config;
        this.symbolMasterCache = symbolMasterCache;
        this.objectMapper = objectMapper;
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    public void start() {
        if (!config.isEnabled()) {
            log.info("OptionChainService disabled — skipping start");
            return;
        }
        trackedUnderlyings = config.getUnderlyings().isEmpty()
                ? new ArrayList<>(symbolMasterCache.allFnoUnderlyings())
                : config.getUnderlyings();

        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("option-chain-scheduler").factory());
        scheduler.scheduleAtFixedRate(this::refreshAll,
                0, config.getPollIntervalSeconds(), TimeUnit.SECONDS);

        log.info("OptionChainService started — poll={}s, {} underlyings",
                config.getPollIntervalSeconds(), trackedUnderlyings.size());
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        executor.shutdownNow();
        log.info("OptionChainService stopped");
    }

    // ── Refresh paths ───────────────────────────────────────────────────────

    /** Path 1: scheduled — polls all tracked underlyings. Called by scheduler. */
    void refreshAll() {
        for (String underlying : trackedUnderlyings) {
            try {
                fetchAndCache(underlying);
            } catch (Exception e) {
                log.warn("Scheduled refresh failed for {}: {}", underlying, e.getMessage());
            }
        }
    }

    /**
     * Path 2: event-triggered — called by CandleService on movement detection.
     * No-op if underlying is not tracked or a fetch is already in-flight.
     *
     * <p>Pre-start (trackedUnderlyings empty): uses config.getUnderlyings() list as guard
     * so tests calling refresh() without start() still work correctly.
     */
    public void refresh(String underlying) {
        if (!config.isEnabled()) return;
        // When post-start, trackedUnderlyings is populated — skip if not tracked.
        // When pre-start (trackedUnderlyings empty), fall back to config list.
        List<String> tracked = trackedUnderlyings;
        if (!tracked.isEmpty() && !tracked.contains(underlying)) return;
        // If neither trackedUnderlyings nor config list contains the symbol, skip.
        List<String> configured = config.getUnderlyings();
        if (tracked.isEmpty() && !configured.isEmpty() && !configured.contains(underlying)) return;

        if (!inFlight.add(underlying)) return; // debounce: skip if fetch already in-flight
        executor.submit(() -> {
            try {
                fetchAndCache(underlying);
            } catch (Exception e) {
                log.warn("Event-triggered refresh failed for {}: {}", underlying, e.getMessage());
            } finally {
                inFlight.remove(underlying);
            }
        });
    }

    /**
     * Checks signal strength against configured thresholds and triggers {@link #refresh}
     * if the move is significant. Called by CandleService after each candle emission.
     */
    public void refreshIfSignificant(String underlying, Signal signal, double confidence, double relVol) {
        boolean strongSignal = signal != Signal.HOLD
                && confidence >= config.getRefreshConfidenceThreshold();
        boolean highVolume   = relVol >= config.getRefreshRelVolThreshold();
        if (strongSignal || highVolume) {
            refresh(underlying);
        }
    }

    /**
     * Path 3: on-demand — returns cached snapshot if present; triggers synchronous
     * fetch on cold start. Returns empty if the underlying is not in the configured
     * list (when a list is configured) or broker fails.
     */
    public Optional<OptionChainSnapshot> getChain(String underlying) {
        OptionChainSnapshot cached = cache.get(underlying);
        if (cached != null) return Optional.of(cached);

        // If a specific list is configured, only fetch for listed underlyings.
        List<String> configured = config.getUnderlyings();
        if (!configured.isEmpty() && !configured.contains(underlying)) {
            return Optional.empty(); // not a configured underlying
        }

        // Cold start: synchronous fetch
        try {
            fetchAndCache(underlying);
            return Optional.ofNullable(cache.get(underlying));
        } catch (Exception e) {
            log.error("On-demand fetch failed for {}: {}", underlying, e.getMessage());
            return Optional.empty();
        }
    }

    public Map<String, OptionChainSnapshot> allSnapshots() {
        return Collections.unmodifiableMap(cache);
    }

    // ── EOD archive ─────────────────────────────────────────────────────────

    public void archiveEod() {
        if (cache.isEmpty()) {
            log.info("OptionChainService: nothing to archive (cache empty)");
            return;
        }
        String date = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
        Path dir = Path.of(config.getArchivePath(), date);
        try {
            Files.createDirectories(dir);
            for (var entry : cache.entrySet()) {
                String filename = entry.getKey()
                        .replace(":", "_").replace(" ", "_") + ".json";
                Path file = dir.resolve(filename);
                Files.writeString(file, objectMapper.writeValueAsString(entry.getValue().data()));
                log.info("Archived option chain for {} → {}", entry.getKey(), file);
            }
        } catch (IOException e) {
            log.error("EOD archive failed: {}", e.getMessage(), e);
        }
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private void fetchAndCache(String underlying) {
        // Pass empty string — Fyers API returns all available expiries
        OptionChainResult result = marketDataAdapter.getOptionChain(
                underlying, config.getStrikeCount(), "");
        if (result == null) {
            log.warn("getOptionChain returned null for {}", underlying);
            return;
        }
        cache.put(underlying, new OptionChainSnapshot(underlying, result, Instant.now()));
        log.debug("Cached option chain for {}", underlying);
    }
}
