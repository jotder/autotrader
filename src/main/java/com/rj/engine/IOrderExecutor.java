package com.rj.engine;

import com.rj.model.OpenPosition;
import com.rj.model.OrderFill;
import com.rj.model.TradeSignal;

/**
 * Abstraction over order placement.
 *
 * <p>Three implementations exist:</p>
 * <ul>
 *   <li>{@link BacktestOrderExecutor} — fills at next candle open + slippage</li>
 *   <li>{@link PaperOrderExecutor}    — fills at current live price, no API call</li>
 *   <li>{@link LiveOrderExecutor}     — places real orders via Fyers API</li>
 * </ul>
 *
 * <p>All implementations must be thread-safe.</p>
 */
public interface IOrderExecutor {

    /**
     * Place an entry order based on the given compound signal.
     *
     * @param signal   the trade signal including direction, suggested prices
     * @param quantity pre-computed quantity from the risk/sizing layer
     * @return fill result — check {@link OrderFill#isSuccess()} before proceeding
     */
    OrderFill placeEntry(TradeSignal signal, int quantity);

    /**
     * Place an exit order for an open position.
     *
     * @param position   the open position to close
     * @param reason     why the exit was triggered (SL / TP / trail / squareoff)
     * @param exitPrice  the target / stop price that triggered the exit
     * @return fill result
     */
    OrderFill placeExit(OpenPosition position,
                        PositionMonitor.ExitReason reason,
                        double exitPrice);
}
