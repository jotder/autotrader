package com.rj.model;

/**
 * Execution mode that governs how orders are placed and filled.
 *
 * <table>
 *   <tr><th>Mode</th><th>Data source</th><th>Orders</th><th>Capital at risk</th></tr>
 *   <tr><td>BACKTEST</td><td>Historical candles (replay)</td><td>Simulated next-bar fill</td><td>None</td></tr>
 *   <tr><td>PAPER</td><td>Live WebSocket ticks</td><td>Simulated fill at live price</td><td>None</td></tr>
 *   <tr><td>LIVE</td><td>Live WebSocket ticks</td><td>Real Fyers API orders</td><td>Real money</td></tr>
 * </table>
 */
public enum ExecutionMode {

    /** Replay historical candles. Orders filled at next candle open + slippage. */
    BACKTEST,

    /** Live market data, simulated fills. No broker API calls for orders. */
    PAPER,

    /** Live market data, real orders placed via Fyers API. */
    LIVE;

    public boolean isLive()    { return this == LIVE;     }
    public boolean isPaper()   { return this == PAPER;    }
    public boolean isBacktest(){ return this == BACKTEST; }

    /** True if capital is not at risk (safe to run freely). */
    public boolean isSafe()    { return this != LIVE;     }
}
