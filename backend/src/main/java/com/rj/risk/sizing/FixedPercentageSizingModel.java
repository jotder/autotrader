package com.rj.risk.sizing;

import com.rj.model.TradeSignal;

/**
 * Risks a fixed percentage of the strategy's capital per trade.
 * Formula: Qty = (Capital * Risk%) / (Entry - SL)
 */
public class FixedPercentageSizingModel implements ISizingModel {

    private final double riskPercentage;

    public FixedPercentageSizingModel(double riskPercentage) {
        this.riskPercentage = riskPercentage;
    }

    @Override
    public double calculateQuantity(TradeSignal signal, double currentCapital) {
        double entry = signal.getSuggestedEntry();
        double sl = signal.getSuggestedStopLoss();
        double riskPerUnit = Math.abs(entry - sl);

        if (riskPerUnit <= 0) return 0;

        // Apply confidence multiplier from Phase-II requirements
        double adjustedRiskPct = riskPercentage * signal.getConfidenceLevel().getMultiplier();
        double monetaryRisk = (currentCapital * adjustedRiskPct) / 100.0;

        return monetaryRisk / riskPerUnit;
    }

    @Override
    public String getName() {
        return "FIXED_PERCENTAGE";
    }
}
