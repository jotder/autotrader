package com.rj.risk.sizing;

import com.rj.model.TradeSignal;

/**
 * Sizes positions based on ATR volatility.
 * Formula: Qty = (Capital * Risk%) / (ATR * Multiplier)
 */
public class AtrVolatilitySizingModel implements ISizingModel {

    private final double riskPercentage;
    private final double atrMultiplier;

    public AtrVolatilitySizingModel(double riskPercentage, double atrMultiplier) {
        this.riskPercentage = riskPercentage;
        this.atrMultiplier = atrMultiplier;
    }

    @Override
    public double calculateQuantity(TradeSignal signal, double currentCapital) {
        double atr = signal.getAtr();
        if (atr <= 0) return 0;

        double adjustedRiskPct = riskPercentage * signal.getConfidenceLevel().getMultiplier();
        double monetaryRisk = (currentCapital * adjustedRiskPct) / 100.0;

        // Risk is distributed over (ATR * Multiplier)
        return monetaryRisk / (atr * atrMultiplier);
    }

    @Override
    public String getName() {
        return "VOLATILITY_ATR";
    }
}
