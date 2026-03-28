package com.rj.engine;

import com.rj.config.ConfigManager;
import com.rj.config.StrategyYamlConfig;
import com.rj.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread Group 2 — Candle Workers.
 */
public class CandleService {

    static final Timeframe[] DEFAULT_TIMEFRAMES = {
            Timeframe.M5,
            Timeframe.M15,
            Timeframe.H1
    };
    private static final Logger log = LoggerFactory.getLogger(CandleService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private final TickStore tickStore;
    private final BlockingQueue<CandleRecommendation> outQueue;
    private final ConfigManager configManager;
    private final Timeframe[] timeframes;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Thread> workers = new ConcurrentHashMap<>();
    private final AtomicInteger workerCount = new AtomicInteger(0);

    private volatile Map<String, StrategyYamlConfig> strategyConfigs = Map.of();

    public CandleService(TickStore tickStore,
                         BlockingQueue<CandleRecommendation> outQueue,
                         ConfigManager configManager) {
        this(tickStore, outQueue, configManager, DEFAULT_TIMEFRAMES);
    }

    public CandleService(TickStore tickStore,
                         BlockingQueue<CandleRecommendation> outQueue,
                         ConfigManager configManager,
                         Timeframe[] timeframes) {
        this.tickStore = tickStore;
        this.outQueue = outQueue;
        this.configManager = configManager;
        this.timeframes = Arrays.copyOf(timeframes, timeframes.length);
    }

    public void setStrategyConfigs(Map<String, StrategyYamlConfig> configs) {
        this.strategyConfigs = configs != null ? Map.copyOf(configs) : Map.of();
        log.info("CandleService strategy configs updated: {} strategies", this.strategyConfigs.size());
    }

    static Candle buildCandle(List<Tick> ticks, Instant windowStart) {
        double open = ticks.get(0).getLtp();
        double close = ticks.get(ticks.size() - 1).getLtp();
        double high = ticks.stream().mapToDouble(Tick::getLtp).max().orElse(open);
        double low = ticks.stream().mapToDouble(Tick::getLtp).min().orElse(open);
        long volume = ticks.stream().mapToLong(Tick::getLastTradedQty).sum();
        return Candle.of(windowStart.getEpochSecond(), open, high, low, close, volume);
    }

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

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        log.info("CandleService stopping {} workers", workers.size());
        workers.values().forEach(Thread::interrupt);
        workers.clear();
    }

    public boolean isRunning() {
        return running.get();
    }

    public int workerCount() {
        return workerCount.get();
    }

    private void startWorker(String symbol, Timeframe tf) {
        String threadName = "candle-" + symbol.replace(":", "_") + "-" + tf.getLabel();
        Thread worker = Thread.ofVirtual()
                .name(threadName)
                .start(() -> runCandleLoop(symbol, tf));
        workers.put(threadName, worker);
        workerCount.incrementAndGet();
        log.debug("Started candle worker: {}", threadName);
    }

    private void runCandleLoop(String symbol, Timeframe tf) {
        log.info("[{}][{}] Candle worker started", symbol, tf);

        StrategyYamlConfig.Indicators indicators = resolveIndicatorsForSymbol(symbol);
        StrategyYamlConfig.Entry entryConfig = resolveEntryForSymbol(symbol);
        CandleAnalyzer analyzer = new CandleAnalyzer(symbol, tf, indicators, entryConfig);
        Instant lastEmittedWin = Instant.EPOCH;

        while (!Thread.currentThread().isInterrupted() && running.get()) {
            try {
                ZonedDateTime now = ZonedDateTime.now(IST);
                long sleepMs = tf.millisUntilNextBoundaryWithBuffer(now);
                sleepMs = Math.max(sleepMs, 200L); 
                Thread.sleep(sleepMs);

                ZonedDateTime afterSleep = ZonedDateTime.now(IST);
                ZonedDateTime currentWinStart = tf.truncate(afterSleep);
                ZonedDateTime prevWinStart = currentWinStart.minus(tf.getDuration());

                Instant winStart = prevWinStart.toInstant();
                Instant winEnd = currentWinStart.toInstant();

                if (winStart.equals(lastEmittedWin)) continue;

                TickBuffer buffer = tickStore.bufferFor(symbol);
                if (buffer == null || buffer.isEmpty()) {
                    log.debug("[{}][{}] No tick buffer yet", symbol, tf);
                    continue;
                }

                List<Tick> snapshot = buffer.snapshot();
                List<Tick> windowTicks = snapshot.stream()
                        .filter(t -> !t.getFeedTime().isBefore(winStart)
                                && t.getFeedTime().isBefore(winEnd))
                        .toList();

                if (windowTicks.isEmpty()) {
                    log.debug("[{}][{}] No ticks for window {}", symbol, tf, winStart);
                    lastEmittedWin = winStart; 
                    continue;
                }

                Candle candle = buildCandle(windowTicks, winStart);
                InstrumentInfo info = configManager.getSymbolRegistry().getInstrumentInfo(symbol);
                CandleRecommendation rec = analyzer.addAndAnalyze(candle, winStart, winEnd, info);

                boolean offered = outQueue.offer(rec);
                if (!offered) {
                    log.warn("[{}][{}] Recommendation queue full — dropping signal for {}", symbol, tf, winStart);
                }

                lastEmittedWin = winStart;
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

    private StrategyYamlConfig.Indicators resolveIndicatorsForSymbol(String symbol) {
        for (StrategyYamlConfig cfg : strategyConfigs.values()) {
            if (cfg.getSymbols().contains(symbol)) {
                return cfg.getIndicators();
            }
        }
        return new StrategyYamlConfig.Indicators();
    }

    private StrategyYamlConfig.Entry resolveEntryForSymbol(String symbol) {
        for (StrategyYamlConfig cfg : strategyConfigs.values()) {
            if (cfg.getSymbols().contains(symbol)) {
                return cfg.getEntry();
            }
        }
        return new StrategyYamlConfig.Entry();
    }
}
