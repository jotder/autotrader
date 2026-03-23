package com.rj.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, time-ordered ring of ticks for a single symbol.
 *
 * <p>Producer (WebSocket thread) calls {@link #append}.<br>
 * Consumer (candle-analysis thread) calls {@link #snapshot} then {@link #pruneBefore}
 * once a candle period is fully closed.</p>
 *
 * <p>Write lock is held only for the duration of a single deque operation, so
 * contention is minimal even at high tick rates.</p>
 */
public class TickBuffer {

    private final String symbol;
    private final ArrayDeque<Tick> ticks;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** @param initialCapacity hint — e.g. expected ticks per session for this symbol */
    public TickBuffer(String symbol, int initialCapacity) {
        this.symbol = symbol;
        this.ticks = new ArrayDeque<>(initialCapacity);
    }

    public TickBuffer(String symbol) {
        this(symbol, 4096);
    }

    // ── Producer side ────────────────────────────────────────────────────────

    /**
     * Appends a tick to the tail. Called by the WebSocket feed thread.
     * Ticks are expected to arrive roughly in chronological order; if the
     * exchange ever re-sends an out-of-order tick, it is still appended at the
     * tail so the deque stays in insertion order (which is feed order).
     */
    public void append(Tick tick) {
        lock.writeLock().lock();
        try {
            ticks.addLast(tick);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ── Consumer side ────────────────────────────────────────────────────────

    /**
     * Returns a stable snapshot of all buffered ticks, ordered oldest → newest.
     * The snapshot is a new list; no lock is held after this call returns.
     * The candle thread should call this, compute candles, then call
     * {@link #pruneBefore} to evict ticks belonging to fully-closed candle periods.
     */
    public List<Tick> snapshot() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(ticks);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes all ticks whose {@code feedTime} is strictly before {@code cutoff}.
     * Call this after a candle period is confirmed closed, passing the candle's
     * end-time as the cutoff, so memory doesn't grow unboundedly.
     *
     * @param cutoff exclusive lower bound — ticks at exactly this instant are kept
     */
    public void pruneBefore(Instant cutoff) {
        lock.writeLock().lock();
        try {
            while (!ticks.isEmpty() && ticks.peekFirst().getFeedTime().isBefore(cutoff)) {
                ticks.pollFirst();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Drains and returns all ticks currently in the buffer, leaving it empty.
     * Useful when the candle builder owns ticks exclusively and wants to avoid
     * the extra ArrayList copy that {@link #snapshot} produces.
     */
    public List<Tick> drain() {
        lock.writeLock().lock();
        try {
            List<Tick> out = new ArrayList<>(ticks);
            ticks.clear();
            return out;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ── Diagnostics ──────────────────────────────────────────────────────────

    public String getSymbol() {
        return symbol;
    }

    /** Approximate size — may be stale by the time caller uses it. */
    public int size() {
        lock.readLock().lock();
        try {
            return ticks.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return ticks.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Oldest buffered tick's feedTime, or {@code Instant.EPOCH} if empty. */
    public Instant oldestTime() {
        lock.readLock().lock();
        try {
            Tick head = ticks.peekFirst();
            return head != null ? head.getFeedTime() : Instant.EPOCH;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Newest buffered tick's feedTime, or {@code Instant.EPOCH} if empty. */
    public Instant newestTime() {
        lock.readLock().lock();
        try {
            Tick tail = ticks.peekLast();
            return tail != null ? tail.getFeedTime() : Instant.EPOCH;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        return "TickBuffer{symbol='" + symbol + "', size=" + size() +
                ", oldest=" + oldestTime() + ", newest=" + newestTime() + "}";
    }
}