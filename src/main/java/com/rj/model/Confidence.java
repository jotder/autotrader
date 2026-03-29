package com.rj.model;

/**
 * Strategy signal confidence levels for Phase-II position sizing multipliers.
 */
public enum Confidence {
    NORMAL(1.0),
    HIGH(1.5),
    VERY_HIGH(2.0);

    private final double multiplier;

    Confidence(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
