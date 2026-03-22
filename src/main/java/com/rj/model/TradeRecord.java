package com.rj.model;

import com.rj.engine.PositionMonitor;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Full lifecycle record of a single trade — from signal through fill to exit.
 *
 * <p>Created when an entry order is filled; updated in-place as the position evolves;
 * sealed when the exit order is filled. Written to the {@code TradeJournal} at close.</p>
 *
 * <p>Fields are mutable and not thread-safe — only the {@code PositionMonitor} thread
 * should mutate an open record.</p>
 */
public class TradeRecord {

    public enum Status { OPEN, CLOSED, CANCELLED }

    // ── Identity ──────────────────────────────────────────────────────────────
    private final String        correlationId;
    private final String        symbol;
    private final String        strategyId;
    private final ExecutionMode mode;
    private final Instant       createdAt;

    // ── Entry ─────────────────────────────────────────────────────────────────
    private final Signal                  direction;
    private final double                  entryPrice;
    private final int                     quantity;
    private final double                  initialStopLoss;
    private final double                  takeProfit;
    private final Instant                 entryTime;
    private final double                  entryAtr;           // ATR at entry (for R calc)
    private final double                  entryConfidence;
    private final Map<Timeframe, Signal>  timeframeVotes;

    // ── Exit (set when position closes) ──────────────────────────────────────
    private Double                      exitPrice;
    private Instant                     exitTime;
    private PositionMonitor.ExitReason  exitReason;
    private Status                      status = Status.OPEN;

    // ── Computed at close ─────────────────────────────────────────────────────
    private Double   pnl;                 // INR: (exit - entry) * qty, direction-adjusted
    private Double   pnlPct;             // as % of entry price
    private Double   rMultipleAchieved;  // pnl / (entry - initialSL) * qty
    private Duration holdDuration;

    // ── Live tracking (updated per tick / per candle during the trade) ────────
    private double maxAdverseExcursion   = 0; // MAE: worst price against trade, in INR
    private double maxFavorableExcursion = 0; // MFE: best price in favour,    in INR

    // ── Constructor ───────────────────────────────────────────────────────────

    public TradeRecord(String correlationId, String symbol, String strategyId,
                       ExecutionMode mode, Signal direction,
                       double entryPrice, int quantity,
                       double initialStopLoss, double takeProfit,
                       Instant entryTime, double entryAtr, double entryConfidence,
                       Map<Timeframe, Signal> timeframeVotes) {
        this.correlationId   = correlationId;
        this.symbol          = symbol;
        this.strategyId      = strategyId;
        this.mode            = mode;
        this.createdAt       = Instant.now();
        this.direction       = direction;
        this.entryPrice      = entryPrice;
        this.quantity        = quantity;
        this.initialStopLoss = initialStopLoss;
        this.takeProfit      = takeProfit;
        this.entryTime       = entryTime;
        this.entryAtr        = entryAtr;
        this.entryConfidence = entryConfidence;
        this.timeframeVotes  = new EnumMap<>(timeframeVotes);
    }

    // ── Close the trade ───────────────────────────────────────────────────────

    /**
     * Seals the record with exit details and computes derived metrics.
     * Safe to call only once.
     */
    public void close(double exitPx, Instant exitTs, PositionMonitor.ExitReason reason) {
        this.exitPrice    = exitPx;
        this.exitTime     = exitTs;
        this.exitReason   = reason;
        this.status       = Status.CLOSED;
        this.holdDuration = Duration.between(entryTime, exitTs);

        double priceDiff = direction == Signal.BUY
                ? exitPx - entryPrice
                : entryPrice - exitPx;
        this.pnl             = priceDiff * quantity;
        this.pnlPct          = (priceDiff / entryPrice) * 100.0;
        double initialRisk   = Math.abs(entryPrice - initialStopLoss) * quantity;
        this.rMultipleAchieved = initialRisk > 0 ? pnl / initialRisk : 0;
    }

    // ── MAE / MFE update (called each monitoring cycle) ──────────────────────

    public void updateExcursion(double currentPrice) {
        double priceDiff = direction == Signal.BUY
                ? currentPrice - entryPrice
                : entryPrice - currentPrice;
        double pnlNow = priceDiff * quantity;
        if (pnlNow < 0 && Math.abs(pnlNow) > maxAdverseExcursion) {
            maxAdverseExcursion = Math.abs(pnlNow);
        }
        if (pnlNow > 0 && pnlNow > maxFavorableExcursion) {
            maxFavorableExcursion = pnlNow;
        }
    }

    // ── Derived helpers ───────────────────────────────────────────────────────

    public boolean isWinner()   { return pnl != null && pnl > 0; }
    public boolean isLoser()    { return pnl != null && pnl < 0; }
    public boolean isOpen()     { return status == Status.OPEN;   }
    public boolean isClosed()   { return status == Status.CLOSED; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String       getCorrelationId()        { return correlationId;       }
    public String       getSymbol()               { return symbol;              }
    public String       getStrategyId()           { return strategyId;          }
    public ExecutionMode getMode()                { return mode;                }
    public Instant      getCreatedAt()            { return createdAt;           }
    public Signal       getDirection()            { return direction;           }
    public double       getEntryPrice()           { return entryPrice;          }
    public int          getQuantity()             { return quantity;            }
    public double       getInitialStopLoss()      { return initialStopLoss;     }
    public double       getTakeProfit()           { return takeProfit;          }
    public Instant      getEntryTime()            { return entryTime;           }
    public double       getEntryAtr()             { return entryAtr;            }
    public double       getEntryConfidence()      { return entryConfidence;     }
    public Map<Timeframe, Signal> getTimeframeVotes() { return timeframeVotes; }
    public Double       getExitPrice()            { return exitPrice;           }
    public Instant      getExitTime()             { return exitTime;            }
    public PositionMonitor.ExitReason getExitReason() { return exitReason;     }
    public Status       getStatus()               { return status;              }
    public Double       getPnl()                  { return pnl;                 }
    public Double       getPnlPct()               { return pnlPct;              }
    public Double       getRMultipleAchieved()    { return rMultipleAchieved;   }
    public Duration     getHoldDuration()         { return holdDuration;        }
    public double       getMaxAdverseExcursion()  { return maxAdverseExcursion; }
    public double       getMaxFavorableExcursion(){ return maxFavorableExcursion;}

    @Override
    public String toString() {
        if (status == Status.OPEN) {
            return String.format("TradeRecord[OPEN  %s %s entry=%.2f sl=%.2f tp=%.2f qty=%d %s]",
                    symbol, direction, entryPrice, initialStopLoss, takeProfit, quantity, strategyId);
        }
        return String.format("TradeRecord[CLOSED %s %s entry=%.2f exit=%.2f pnl=%.2f R=%.2f dur=%s %s]",
                symbol, direction, entryPrice, exitPrice, pnl, rMultipleAchieved,
                holdDuration, exitReason);
    }
}
