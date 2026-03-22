package com.rj.model;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of per-symbol {@link TickBuffer}s.
 *
 * <h2>Threading model</h2>
 * <pre>
 *  WebSocket thread(s)          Candle-analysis thread (per symbol)
 *  ──────────────────           ──────────────────────────────────
 *  TickStore.append(tick)  ──►  buffer.snapshot()  →  build candles
 *                               buffer.pruneBefore(closedCandleEnd)
 * </pre>
 *
 * <p>The map itself is a {@link ConcurrentHashMap} so symbol registration is
 * lock-free. Each {@link TickBuffer} manages its own read/write lock internally.</p>
 */
public class TickStore {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static final TickStore INSTANCE = new TickStore();

    public static TickStore getInstance() { return INSTANCE; }

    private TickStore() {}

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * symbol → its TickBuffer.
     * Buffers are created lazily on first tick for that symbol.
     */
    private final ConcurrentHashMap<String, TickBuffer> buffers = new ConcurrentHashMap<>();

    // ── Producer API ──────────────────────────────────────────────────────────

    /**
     * Appends a tick to its symbol's buffer, creating the buffer if needed.
     * This is the only method the WebSocket feed thread needs to call.
     */
    public void append(Tick tick) {
        buffers.computeIfAbsent(tick.getSymbol(), TickBuffer::new)
               .append(tick);
    }

    // ── Consumer API ─────────────────────────────────────────────────────────

    /**
     * Returns the buffer for the given symbol, or {@code null} if no ticks have
     * arrived yet for that symbol.
     */
    public TickBuffer bufferFor(String symbol) {
        return buffers.get(symbol);
    }

    /**
     * Returns an unmodifiable view of all currently registered symbols.
     * The set reflects the live map — symbols appear as soon as the first tick
     * arrives and are never removed automatically.
     */
    public Collection<String> symbols() {
        return Collections.unmodifiableSet(buffers.keySet());
    }

    /** Total number of symbols currently tracked. */
    public int symbolCount() { return buffers.size(); }

    /** Removes all buffers — useful for end-of-day reset or testing. */
    public void clear() { buffers.clear(); }

    @Override
    public String toString() {
        return "TickStore{symbols=" + buffers.size() + "}";
    }
}