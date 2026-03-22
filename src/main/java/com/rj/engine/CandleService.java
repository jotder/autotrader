package com.rj.engine;

import com.rj.model.Candle;
import com.rj.model.CandleRecommendation;
import com.rj.model.Tick;
import com.rj.model.TickBuffer;
import com.rj.model.TickStore;
import com.rj.model.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread Group 2 — Candle Workers.
 *
 * <p>Starts one virtual thread per (symbol × timeframe).
 * Each thread sleeps until the next candle boundary, builds an OHLCV candle
 * from tick snapshots, runs {@link CandleAnalyzer}, and publishes a
 * {@link CandleRecommendation} to the shared output queue.</p>
 *
 * <h3>Thread naming</h3>
 * Threads are named {@code candle-<SYMBOL_SAFE>-<TF>}, e.g.
 * {@code candle-NSE_SBIN-EQ-5m}.
 */
public class CandleService {

    private static final Logger log = LoggerFactory.getLogger(CandleService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Default timeframes analyzed for every symbol
    static final Timeframe[] DEFAULT_TIMEFRAMES = {
            Timeframe.M5,
            Timeframe.M15,
            Timeframe.H1
    };

    private final TickStore                              tickStore;
    private final BlockingQueue<CandleRecommendation>   outQueue;
    private final Timeframe[]                           timeframes;
    private final AtomicBoolean                         running   = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Thread>     workers   = new ConcurrentHashMap<>();
    private final AtomicInteger                         workerCount = new AtomicInteger(0);

    public CandleService(TickStore tickStore,
                         BlockingQueue<CandleRecommendation> outQueue) {
        this(tickStore, outQueue, DEFAULT_TIMEFRAMES);
    }

    public CandleService(TickStore tickStore,
                         BlockingQueue<CandleRecommendation> outQueue,
                         Timeframe[] timeframes) {
        this.tickStore  = tickStore;
        this.outQueue   = outQueue;
        this.timeframes = Arrays.copyOf(timeframes, timeframes.length);
    }

    /**
     * Starts one virtual thread per (symbol × timeframe).
     * Call this after the WebSocket feed has been started and symbols are known.
     */
    public void start(String[] symbols) {
        if (!running.compareAndSet(false, true)) {
            log.warn("CandleService already running — ignoring start()");
            return;
        }
        log.info("CandleService starting: {} symbols × {} timeframes = {} workers",
                symbols.length, timeframes.length, symbols.length * timeframes.length);

        for (String symbol : symbols) {
            for (Timeframe tf : timeframes) {
                startWorker(symbol, tf);
            }
        }
    }

    /** Stops all candle workers by interrupting their threads. */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        log.info("CandleService stopping {} workers", workers.size());
        workers.values().forEach(Thread::interrupt);
        workers.clear();
    }

    public boolean isRunning()   { return running.get();         }
    public int     workerCount() { return workerCount.get();     }

    // ── Worker lifecycle ──────────────────────────────────────────────────────

    private void startWorker(String symbol, Timeframe tf) {
        String threadName = "candle-" + symbol.replace(":", "_") + "-" + tf.getLabel();
        Thread worker = Thread.ofVirtual()
                .name(threadName)
                .start(() -> runCandleLoop(symbol, tf));
        workers.put(threadName, worker);
        workerCount.incrementAndGet();
        log.debug("Started candle worker: {}", threadName);
    }

    // ── Per-worker loop ───────────────────────────────────────────────────────

    private void runCandleLoop(String symbol, Timeframe tf) {
        log.info("[{}][{}] Candle worker started", symbol, tf);
        CandleAnalyzer analyzer       = new CandleAnalyzer(symbol, tf);
        Instant        lastEmittedWin = Instant.EPOCH;

        while (!Thread.currentThread().isInterrupted() && running.get()) {
            try {
                // Sleep until just after the next candle boundary
                ZonedDateTime now      = ZonedDateTime.now(IST);
                long          sleepMs  = tf.millisUntilNextBoundaryWithBuffer(now);
                sleepMs = Math.max(sleepMs, 200L); // guard against negative/tiny values
                Thread.sleep(sleepMs);

                // After sleep we should be in the new candle window;
                // the *previous* window is now fully closed.
                ZonedDateTime afterSleep      = ZonedDateTime.now(IST);
                ZonedDateTime currentWinStart = tf.truncate(afterSleep);
                ZonedDateTime prevWinStart    = currentWinStart.minus(tf.getDuration());

                Instant winStart = prevWinStart.toInstant();
                Instant winEnd   = currentWinStart.toInstant();

                if (winStart.equals(lastEmittedWin)) continue; // already processed

                TickBuffer buffer = tickStore.bufferFor(symbol);
                if (buffer == null || buffer.isEmpty()) {
                    log.debug("[{}][{}] No tick buffer yet", symbol, tf);
                    continue;
                }

                List<Tick> snapshot = buffer.snapshot();
                List<Tick> windowTicks = snapshot.stream()
                        .filter(t -> !t.getFeedTime().isBefore(winStart)
                                  &&  t.getFeedTime().isBefore(winEnd))
                        .toList();

                if (windowTicks.isEmpty()) {
                    log.debug("[{}][{}] No ticks for window {}", symbol, tf, winStart);
                    lastEmittedWin = winStart; // mark as processed even if empty
                    continue;
                }

                Candle candle = buildCandle(windowTicks, winStart);
                CandleRecommendation rec = analyzer.addAndAnalyze(candle, winStart, winEnd);

                boolean offered = outQueue.offer(rec);
                if (!offered) {
                    log.warn("[{}][{}] Recommendation queue full — dropping signal for {}", symbol, tf, winStart);
                }

                lastEmittedWin = winStart;

                // Prune ticks older than one full candle period before the window we just processed
                buffer.pruneBefore(winStart.minus(tf.getDuration()));

                log.info("[{}][{}] Candle emitted: {} ticks → {} conf={} src={}",
                        symbol, tf, windowTicks.size(),
                        rec.getSignal(), String.format("%.2f", rec.getConfidence()),
                        rec.getStrategySource());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[{}][{}] Error in candle loop: {}", symbol, tf, e.getMessage(), e);
            }
        }
        workerCount.decrementAndGet();
        log.info("[{}][{}] Candle worker stopped", symbol, tf);
    }

    // ── Candle construction from ticks ────────────────────────────────────────

    /**
     * Aggregates a list of ticks (already filtered to a time window) into one OHLCV candle.
     *
     * <ul>
     *   <li>Open  = first tick LTP</li>
     *   <li>High  = max LTP</li>
     *   <li>Low   = min LTP</li>
     *   <li>Close = last tick LTP</li>
     *   <li>Volume = sum of {@code lastTradedQty} across all ticks in the window</li>
     * </ul>
     */
    static Candle buildCandle(List<Tick> ticks, Instant windowStart) {
        double open   = ticks.get(0).getLtp();
        double close  = ticks.get(ticks.size() - 1).getLtp();
        double high   = ticks.stream().mapToDouble(Tick::getLtp).max().orElse(open);
        double low    = ticks.stream().mapToDouble(Tick::getLtp).min().orElse(open);
        long   volume = ticks.stream().mapToLong(Tick::getLastTradedQty).sum();
        return Candle.of(windowStart.getEpochSecond(), open, high, low, close, volume);
    }
}
