package com.rj.model;

import java.time.LocalDate;

/**
 * Statistical profile for a symbol computed from M1 candle history.
 * Used for strategy parameter tuning and behavioral analysis.
 */
public record SymbolProfile(
        String symbol,
        int tradingDays,
        LocalDate from,
        LocalDate to,

        // Volatility
        double avgDailyRangePct,
        double avgDailyReturnPct,
        double dailyReturnStdDev,

        // Volume profile (average volume per 5-min bucket, 75 slots for 09:15–15:30 IST)
        double[] avgVolumeBySlot,

        // Trend persistence
        double avgConsecutiveUpBars,
        double avgConsecutiveDownBars,

        // Gap analysis
        double gapUpPct,
        double gapDownPct,

        // Session structure
        double morningRangePct,
        double[] hourlyVolatility
) {}
