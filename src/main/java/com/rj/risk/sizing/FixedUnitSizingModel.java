package com.rj.risk.sizing;

import com.rj.model.TradeSignal;

/**
 * Buying a fixed number of shares regardless of account size or stop-loss.
 */
public class FixedUnitSizingModel implements ISizingModel {

    private final int fixedQuantity;

    public FixedUnitSizingModel(int fixedQuantity) {
        this.fixedQuantity = fixedQuantity;
    }

    @Override
    public double calculateQuantity(TradeSignal signal, double currentCapital) {
        // Confidence multiplier can still apply to the quantity if desired,
        // but traditionally Unit method is fixed.
        // We'll apply the confidence multiplier to the unit.
        return fixedQuantity * signal.getConfidenceLevel().getMultiplier();
    }

    @Override
    public String getName() {
        return "FIXED_UNIT";
    }
}
