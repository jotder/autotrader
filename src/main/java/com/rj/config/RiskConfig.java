package com.rj.config;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

public class RiskConfig {
    private final double maxRiskPerTradePercent;
    private final double maxDailyLossInr;
    private final double maxDailyProfitInr;
    private final double initialCapitalInr;
    private final double maxExposurePerSymbolPercent;
    private final int maxQuantityPerOrder;
    private final int maxConsecutiveLossesPerStrategy;
    private final double stopLossAtrMultiplier;
    private final double takeProfitAtrMultiplier;
    private final double trailingActivationPercent;
    private final double trailingStepPercent;
    private final int instrumentLotSize;
    private final LocalTime noNewTradesAfter;
    private final LocalTime marketCloseTime;
    private final ZoneId exchangeZone;

    private RiskConfig(
            double maxRiskPerTradePercent,
            double maxDailyLossInr,
            double maxDailyProfitInr,
            double initialCapitalInr,
            double maxExposurePerSymbolPercent,
            int maxQuantityPerOrder,
            int maxConsecutiveLossesPerStrategy,
            double stopLossAtrMultiplier,
            double takeProfitAtrMultiplier,
            double trailingActivationPercent,
            double trailingStepPercent,
            int instrumentLotSize,
            LocalTime noNewTradesAfter,
            LocalTime marketCloseTime,
            ZoneId exchangeZone) {
        this.maxRiskPerTradePercent = maxRiskPerTradePercent;
        this.maxDailyLossInr = maxDailyLossInr;
        this.maxDailyProfitInr = maxDailyProfitInr;
        this.initialCapitalInr = initialCapitalInr;
        this.maxExposurePerSymbolPercent = maxExposurePerSymbolPercent;
        this.maxQuantityPerOrder = maxQuantityPerOrder;
        this.maxConsecutiveLossesPerStrategy = maxConsecutiveLossesPerStrategy;
        this.stopLossAtrMultiplier = stopLossAtrMultiplier;
        this.takeProfitAtrMultiplier = takeProfitAtrMultiplier;
        this.trailingActivationPercent = trailingActivationPercent;
        this.trailingStepPercent = trailingStepPercent;
        this.instrumentLotSize = instrumentLotSize;
        this.noNewTradesAfter = noNewTradesAfter;
        this.marketCloseTime = marketCloseTime;
        this.exchangeZone = exchangeZone;
    }

    public static RiskConfig defaults() {
        return new RiskConfig(
                0.02,
                5000.0,
                15000.0,
                500000.0,
                0.20,
                1000,
                3,
                2.0,
                2.0,
                0.01,
                0.01,
                1,
                LocalTime.of(15, 0),
                LocalTime.of(15, 15),
                ZoneId.of("Asia/Kolkata"));
    }

    public static RiskConfig fromEnvironment(Function<String, String> envReader) {
        RiskConfig d = defaults();
        return new RiskConfig(
                parseDouble(envReader.apply("RISK_MAX_PER_TRADE_PCT"), d.maxRiskPerTradePercent),
                parseDouble(envReader.apply("RISK_MAX_DAILY_LOSS_INR"), d.maxDailyLossInr),
                parseDouble(envReader.apply("RISK_MAX_DAILY_PROFIT_INR"), d.maxDailyProfitInr),
                parseDouble(envReader.apply("RISK_INITIAL_CAPITAL_INR"), d.initialCapitalInr),
                parseDouble(envReader.apply("RISK_MAX_EXPOSURE_PER_SYMBOL_PCT"), d.maxExposurePerSymbolPercent),
                parseInt(envReader.apply("RISK_MAX_QTY_PER_ORDER"), d.maxQuantityPerOrder),
                parseInt(envReader.apply("RISK_MAX_CONSECUTIVE_LOSSES"), d.maxConsecutiveLossesPerStrategy),
                parseDouble(envReader.apply("RISK_STOP_LOSS_ATR_MULTIPLIER"), d.stopLossAtrMultiplier),
                parseDouble(envReader.apply("RISK_TAKE_PROFIT_R_MULTIPLIER"), d.takeProfitAtrMultiplier),
                parseDouble(envReader.apply("RISK_TRAIL_ACTIVATION_PCT"), d.trailingActivationPercent),
                parseDouble(envReader.apply("RISK_TRAIL_STEP_PCT"), d.trailingStepPercent),
                parseInt(envReader.apply("RISK_INSTRUMENT_LOT_SIZE"), d.instrumentLotSize),
                parseTime(envReader.apply("RISK_NO_NEW_TRADES_AFTER"), d.noNewTradesAfter),
                parseTime(envReader.apply("RISK_MARKET_CLOSE_TIME"), d.marketCloseTime),
                parseZone(envReader.apply("RISK_EXCHANGE_ZONE"), d.exchangeZone));
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

    private static ZoneId parseZone(String value, ZoneId fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return ZoneId.of(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public double getMaxRiskPerTradePercent() {
        return maxRiskPerTradePercent;
    }

    public double getMaxDailyLossInr() {
        return maxDailyLossInr;
    }

    public double getMaxDailyProfitInr() {
        return maxDailyProfitInr;
    }

    public double getInitialCapitalInr() {
        return initialCapitalInr;
    }

    public double getMaxExposurePerSymbolPercent() {
        return maxExposurePerSymbolPercent;
    }

    public int getMaxQuantityPerOrder() {
        return maxQuantityPerOrder;
    }

    public int getMaxConsecutiveLossesPerStrategy() {
        return maxConsecutiveLossesPerStrategy;
    }

    public double getStopLossAtrMultiplier() {
        return stopLossAtrMultiplier;
    }

    public double getTakeProfitAtrMultiplier() {
        return takeProfitAtrMultiplier;
    }

    public double getTrailingActivationPercent() {
        return trailingActivationPercent;
    }

    public double getTrailingStepPercent() {
        return trailingStepPercent;
    }

    public int getInstrumentLotSize() {
        return instrumentLotSize;
    }

    public LocalTime getNoNewTradesAfter() {
        return noNewTradesAfter;
    }

    public LocalTime getMarketCloseTime() {
        return marketCloseTime;
    }

    public ZoneId getExchangeZone() {
        return exchangeZone;
    }
}
