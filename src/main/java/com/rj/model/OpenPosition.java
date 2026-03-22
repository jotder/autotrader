package com.rj.model;

import java.time.Instant;

/**
 * Tracks the live state of a single open position.
 *
 * <p>The mutable fields ({@code currentStopLoss}, {@code trailingActivated},
 * {@code highWaterMark}) are {@code volatile} so the position-monitor thread can
 * update them while the health-monitor thread reads them without a lock.</p>
 */
public class OpenPosition {

    // ── Immutable identity ────────────────────────────────────────────────────
    private final String  symbol;
    private final String  correlationId;
    private final String  strategyId;
    private final Signal  direction;       // BUY or SELL
    private final double  entryPrice;
    private final int     quantity;
    private final double  initialStopLoss;
    private final double  takeProfit;
    private final Instant entryTime;

    // ── Mutable — written by position-monitor thread only ────────────────────
    private volatile double  currentStopLoss;
    private volatile boolean trailingActivated;
    private volatile double  highWaterMark;   // best price reached since entry

    public OpenPosition(String symbol, String correlationId, String strategyId,
                        Signal direction, double entryPrice, int quantity,
                        double stopLoss, double takeProfit, Instant entryTime) {
        this.symbol           = symbol;
        this.correlationId    = correlationId;
        this.strategyId       = strategyId;
        this.direction        = direction;
        this.entryPrice       = entryPrice;
        this.quantity         = quantity;
        this.initialStopLoss  = stopLoss;
        this.currentStopLoss  = stopLoss;
        this.takeProfit       = takeProfit;
        this.entryTime        = entryTime;
        this.trailingActivated = false;
        this.highWaterMark    = entryPrice;
    }

    // ── Trailing stop management ──────────────────────────────────────────────

    /**
     * Updates the high-water mark in the direction of the trade.
     * Long: tracks the highest price seen; Short: tracks the lowest.
     */
    public void updateHighWaterMark(double price) {
        if (direction == Signal.BUY  && price > highWaterMark) highWaterMark = price;
        if (direction == Signal.SELL && price < highWaterMark) highWaterMark = price;
    }

    /**
     * Moves the stop loss monotonically toward the entry direction.
     * Long: stop can only move up. Short: stop can only move down.
     *
     * @return true if the stop was actually moved
     */
    public boolean stepTrailingStop(double newStop) {
        if (direction == Signal.BUY  && newStop > currentStopLoss) { currentStopLoss = newStop; return true; }
        if (direction == Signal.SELL && newStop < currentStopLoss) { currentStopLoss = newStop; return true; }
        return false;
    }

    // ── Exit checks ───────────────────────────────────────────────────────────

    public boolean isStopLossHit(double price) {
        return direction == Signal.BUY ? price <= currentStopLoss : price >= currentStopLoss;
    }

    public boolean isTakeProfitHit(double price) {
        return direction == Signal.BUY ? price >= takeProfit : price <= takeProfit;
    }

    /**
     * Unrealized PnL in INR (positive = profit, negative = loss).
     */
    public double unrealizedPnl(double currentPrice) {
        double priceDiff = direction == Signal.BUY
                ? currentPrice - entryPrice
                : entryPrice - currentPrice;
        return priceDiff * quantity;
    }

    /**
     * Unrealized PnL as percentage of entry price.
     */
    public double unrealizedPnlPct(double currentPrice) {
        if (entryPrice == 0) return 0;
        double priceDiff = direction == Signal.BUY
                ? currentPrice - entryPrice
                : entryPrice - currentPrice;
        return (priceDiff / entryPrice) * 100.0;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String  getSymbol()            { return symbol;            }
    public String  getCorrelationId()     { return correlationId;     }
    public String  getStrategyId()        { return strategyId;        }
    public Signal  getDirection()         { return direction;         }
    public double  getEntryPrice()        { return entryPrice;        }
    public int     getQuantity()          { return quantity;          }
    public double  getInitialStopLoss()   { return initialStopLoss;   }
    public double  getCurrentStopLoss()   { return currentStopLoss;   }
    public double  getTakeProfit()        { return takeProfit;        }
    public Instant getEntryTime()         { return entryTime;         }
    public boolean isTrailingActivated()  { return trailingActivated; }
    public double  getHighWaterMark()     { return highWaterMark;     }

    public void setTrailingActivated(boolean v) { trailingActivated = v; }

    @Override
    public String toString() {
        return String.format("OpenPosition{%s %s qty=%d entry=%.2f sl=%.2f tp=%.2f pnl=%.2f trail=%s}",
                symbol, direction, quantity, entryPrice, currentStopLoss, takeProfit,
                unrealizedPnl(highWaterMark), trailingActivated);
    }
}
