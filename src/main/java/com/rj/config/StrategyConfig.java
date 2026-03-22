package com.rj.config;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

public class StrategyConfig {
    private final double trendVolumeThreshold;
    private final double meanReversionOversoldRsi;
    private final double meanReversionOverboughtRsi;
    private final double breakoutVolatilityThreshold;
    private final double breakoutVolumeSpikeThreshold;
    private final double trendConfidence;
    private final double meanReversionConfidence;
    private final double breakoutConfidence;
    private final LocalTime openingWindowStart;
    private final LocalTime openingWindowEnd;
    private final LocalTime closingWindowStart;
    private final LocalTime closingWindowEnd;
    private final int cooldownCandles;

    private StrategyConfig(
            double trendVolumeThreshold,
            double meanReversionOversoldRsi,
            double meanReversionOverboughtRsi,
            double breakoutVolatilityThreshold,
            double breakoutVolumeSpikeThreshold,
            double trendConfidence,
            double meanReversionConfidence,
            double breakoutConfidence,
            LocalTime openingWindowStart,
            LocalTime openingWindowEnd,
            LocalTime closingWindowStart,
            LocalTime closingWindowEnd,
            int cooldownCandles) {
        this.trendVolumeThreshold = trendVolumeThreshold;
        this.meanReversionOversoldRsi = meanReversionOversoldRsi;
        this.meanReversionOverboughtRsi = meanReversionOverboughtRsi;
        this.breakoutVolatilityThreshold = breakoutVolatilityThreshold;
        this.breakoutVolumeSpikeThreshold = breakoutVolumeSpikeThreshold;
        this.trendConfidence = trendConfidence;
        this.meanReversionConfidence = meanReversionConfidence;
        this.breakoutConfidence = breakoutConfidence;
        this.openingWindowStart = openingWindowStart;
        this.openingWindowEnd = openingWindowEnd;
        this.closingWindowStart = closingWindowStart;
        this.closingWindowEnd = closingWindowEnd;
        this.cooldownCandles = cooldownCandles;
    }

    public static StrategyConfig defaults() {
        return new StrategyConfig(
                1.2,
                30.0,
                70.0,
                2.0,
                2.0,
                0.85,
                0.70,
                0.90,
                LocalTime.of(9, 15),
                LocalTime.of(10, 0),
                LocalTime.of(14, 30),
                LocalTime.of(15, 30),
                1);
    }

    public static StrategyConfig fromEnvironment(Function<String, String> envReader) {
        StrategyConfig d = defaults();
        return new StrategyConfig(
                parseDouble(envReader.apply("STRATEGY_TREND_VOLUME_THRESHOLD"), d.trendVolumeThreshold),
                parseDouble(envReader.apply("STRATEGY_MEANREV_OVERSOLD_RSI"), d.meanReversionOversoldRsi),
                parseDouble(envReader.apply("STRATEGY_MEANREV_OVERBOUGHT_RSI"), d.meanReversionOverboughtRsi),
                parseDouble(envReader.apply("STRATEGY_BREAKOUT_VOLATILITY_THRESHOLD"), d.breakoutVolatilityThreshold),
                parseDouble(envReader.apply("STRATEGY_BREAKOUT_VOLUME_SPIKE_THRESHOLD"), d.breakoutVolumeSpikeThreshold),
                parseDouble(envReader.apply("STRATEGY_TREND_CONFIDENCE"), d.trendConfidence),
                parseDouble(envReader.apply("STRATEGY_MEANREV_CONFIDENCE"), d.meanReversionConfidence),
                parseDouble(envReader.apply("STRATEGY_BREAKOUT_CONFIDENCE"), d.breakoutConfidence),
                parseTime(envReader.apply("STRATEGY_OPENING_START"), d.openingWindowStart),
                parseTime(envReader.apply("STRATEGY_OPENING_END"), d.openingWindowEnd),
                parseTime(envReader.apply("STRATEGY_CLOSING_START"), d.closingWindowStart),
                parseTime(envReader.apply("STRATEGY_CLOSING_END"), d.closingWindowEnd),
                parseInt(envReader.apply("STRATEGY_COOLDOWN_CANDLES"), d.cooldownCandles));
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static LocalTime parseTime(String value, LocalTime fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    public double getTrendVolumeThreshold() {
        return trendVolumeThreshold;
    }

    public double getMeanReversionOversoldRsi() {
        return meanReversionOversoldRsi;
    }

    public double getMeanReversionOverboughtRsi() {
        return meanReversionOverboughtRsi;
    }

    public double getBreakoutVolatilityThreshold() {
        return breakoutVolatilityThreshold;
    }

    public double getBreakoutVolumeSpikeThreshold() {
        return breakoutVolumeSpikeThreshold;
    }

    public double getTrendConfidence() {
        return trendConfidence;
    }

    public double getMeanReversionConfidence() {
        return meanReversionConfidence;
    }

    public double getBreakoutConfidence() {
        return breakoutConfidence;
    }

    public LocalTime getOpeningWindowStart() {
        return openingWindowStart;
    }

    public LocalTime getOpeningWindowEnd() {
        return openingWindowEnd;
    }

    public LocalTime getClosingWindowStart() {
        return closingWindowStart;
    }

    public LocalTime getClosingWindowEnd() {
        return closingWindowEnd;
    }

    public int getCooldownCandles() {
        return cooldownCandles;
    }
}
