package com.rj.config;

import com.rj.model.SizingType;
import com.rj.risk.sizing.ISizingModel;
import com.rj.risk.sizing.FixedPercentageSizingModel;
import com.rj.risk.sizing.AtrVolatilitySizingModel;
import com.rj.risk.sizing.FixedUnitSizingModel;

/**
 * Configuration for a single pluggable strategy in Phase-II.
 */
public class TradeStrategyConfig {
    private String strategyId;
    private String name;
    private boolean active;
    private double allocationPercentage; // % of total capital
    private SizingType sizingType;
    private double riskPercentage;       // for FIXED_PERCENTAGE and VOLATILITY_ATR
    private double atrMultiplier;        // for VOLATILITY_ATR
    private int fixedQuantity;           // for FIXED_UNIT

    public TradeStrategyConfig() {}

    public String getStrategyId() { return strategyId; }
    public void setStrategyId(String strategyId) { this.strategyId = strategyId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public double getAllocationPercentage() { return allocationPercentage; }
    public void setAllocationPercentage(double allocationPercentage) { this.allocationPercentage = allocationPercentage; }

    public SizingType getSizingType() { return sizingType; }
    public void setSizingType(SizingType sizingType) { this.sizingType = sizingType; }

    public double getRiskPercentage() { return riskPercentage; }
    public void setRiskPercentage(double riskPercentage) { this.riskPercentage = riskPercentage; }

    public double getAtrMultiplier() { return atrMultiplier; }
    public void setAtrMultiplier(double atrMultiplier) { this.atrMultiplier = atrMultiplier; }

    public int getFixedQuantity() { return fixedQuantity; }
    public void setFixedQuantity(int fixedQuantity) { this.fixedQuantity = fixedQuantity; }

    /**
     * Factory method to create the appropriate sizing model instance.
     */
    public ISizingModel createSizingModel() {
        return switch (sizingType) {
            case FIXED_PERCENTAGE -> new FixedPercentageSizingModel(riskPercentage);
            case VOLATILITY_ATR -> new AtrVolatilitySizingModel(riskPercentage, atrMultiplier);
            case FIXED_UNIT -> new FixedUnitSizingModel(fixedQuantity);
            case PYRAMIDING -> new FixedPercentageSizingModel(riskPercentage); // Default to fixed for now
        };
    }
}
