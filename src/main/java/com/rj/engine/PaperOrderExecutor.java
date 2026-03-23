package com.rj.engine;

import com.rj.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Paper trading executor.
 *
 * <p>Fills use the latest live tick price from {@link TickStore}.
 * No broker API is called. Ideal for forward-testing strategies on live data
 * before committing real capital.</p>
 *
 * <p>If no live tick is available for the symbol the order is rejected — this
 * prevents phantom fills during WebSocket disconnects.</p>
 */
public class PaperOrderExecutor implements IOrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(PaperOrderExecutor.class);

    private final TickStore tickStore;
    private final AtomicInteger orderSeq = new AtomicInteger(0);

    public PaperOrderExecutor(TickStore tickStore) {
        this.tickStore = tickStore;
    }

    @Override
    public OrderFill placeEntry(TradeSignal signal, int quantity) {
        double ltp = latestPrice(signal.getSymbol());
        if (ltp <= 0) {
            String msg = "No live tick for " + signal.getSymbol() + " — rejecting paper entry";
            log.warn("[PAPER] {}", msg);
            return OrderFill.rejected(msg);
        }
        if (quantity <= 0) {
            return OrderFill.rejected("Quantity must be > 0");
        }

        String orderId = "PP-ENTRY-" + orderSeq.incrementAndGet();
        log.info("[PAPER] Entry fill: {} {} qty={} @ {}", signal.getSymbol(),
                signal.getDirection(), quantity, String.format("%.2f", ltp));
        return OrderFill.success(orderId, ltp, quantity, Instant.now());
    }

    @Override
    public OrderFill placeExit(OpenPosition position,
                               PositionMonitor.ExitReason reason,
                               double exitPrice) {
        // Use the actual trigger price for exits (SL / TP / force)
        // rather than the live LTP, so the fill matches the trigger condition exactly
        double fillPx = exitPrice > 0 ? exitPrice : latestPrice(position.getSymbol());
        if (fillPx <= 0) {
            String msg = "No exit price available for " + position.getSymbol();
            log.warn("[PAPER] {}", msg);
            return OrderFill.rejected(msg);
        }

        String orderId = "PP-EXIT-" + orderSeq.incrementAndGet();
        log.info("[PAPER] Exit fill: {} {} qty={} @ {} reason={}",
                position.getSymbol(), position.getDirection(),
                position.getQuantity(), String.format("%.2f", fillPx), reason);
        return OrderFill.success(orderId, fillPx, position.getQuantity(), Instant.now());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double latestPrice(String symbol) {
        TickBuffer buf = tickStore.bufferFor(symbol);
        if (buf == null || buf.isEmpty()) return 0;
        List<com.rj.model.Tick> snapshot = buf.snapshot();
        return snapshot.isEmpty() ? 0 : snapshot.get(snapshot.size() - 1).getLtp();
    }
}
