package com.rj.engine;

import com.rj.model.OpenPosition;
import com.rj.model.OrderFill;
import com.rj.model.TradeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Backtest order executor.
 *
 * <p>Fills are simulated at the price provided by the BacktestEngine
 * (typically next candle's open price ± slippage). No broker API is called.</p>
 *
 * <p>Slippage model: {@code fillPrice = signalPrice × (1 + slippagePct × direction)}.
 * Default slippage is 0.05 % (5 basis points) — typical NSE equity spread.
 * Override via constructor for worst-case sensitivity testing.</p>
 */
public class BacktestOrderExecutor implements IOrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(BacktestOrderExecutor.class);

    /** Default slippage as fraction of price (0.0005 = 0.05 %). */
    private static final double DEFAULT_SLIPPAGE = 0.0005;

    private final double slippageFraction;
    private final AtomicInteger orderSeq = new AtomicInteger(0);

    // Injected by BacktestEngine before each candle so fills use the right price
    private volatile double nextBarOpenPrice = 0;
    private volatile Instant nextBarTime = Instant.EPOCH;

    public BacktestOrderExecutor() {
        this(DEFAULT_SLIPPAGE);
    }

    public BacktestOrderExecutor(double slippageFraction) {
        this.slippageFraction = slippageFraction;
    }

    /** Called by BacktestEngine before processing each new candle. */
    public void setNextBar(double openPrice, Instant barTime) {
        this.nextBarOpenPrice = openPrice;
        this.nextBarTime = barTime;
    }

    @Override
    public OrderFill placeEntry(TradeSignal signal, int quantity) {
        if (nextBarOpenPrice <= 0) {
            return OrderFill.rejected("No next bar price available for backtest fill");
        }
        if (quantity <= 0) {
            return OrderFill.rejected("Quantity must be > 0");
        }

        // Apply slippage in the direction of the trade (adverse to the trader)
        double slippage = nextBarOpenPrice * slippageFraction;
        double fillPx = signal.getDirection().toString().equals("BUY")
                ? nextBarOpenPrice + slippage
                : nextBarOpenPrice - slippage;

        String orderId = "BT-ENTRY-" + orderSeq.incrementAndGet();
        log.debug("[BT] Entry fill: {} {} qty={} @ {:.2f} (next-bar={:.2f} slip={:.4f})",
                signal.getSymbol(), signal.getDirection(), quantity, fillPx,
                nextBarOpenPrice, slippage);

        return OrderFill.success(orderId, fillPx, quantity, nextBarTime);
    }

    @Override
    public OrderFill placeExit(OpenPosition position,
                               PositionMonitor.ExitReason reason,
                               double exitPrice) {
        // For SL/TP hits in backtest, use the exact trigger price (no additional slippage)
        // to avoid double-counting since the trigger price already models adverse fill
        double fillPx;
        switch (reason) {
            case STOP_LOSS:
            case TRAILING_STOP:
                // Worst-case: assume fill at SL price (could be worse in gapping markets)
                fillPx = position.getCurrentStopLoss();
                break;
            case TAKE_PROFIT:
                fillPx = position.getTakeProfit();
                break;
            default:
                // FORCE_SQUAREOFF or MANUAL: fill at provided exit price
                fillPx = exitPrice;
        }

        String orderId = "BT-EXIT-" + orderSeq.incrementAndGet();
        log.debug("[BT] Exit fill: {} {} @ {:.2f} reason={}",
                position.getSymbol(), position.getDirection(), fillPx, reason);

        return OrderFill.success(orderId, fillPx, position.getQuantity(), nextBarTime);
    }
}
