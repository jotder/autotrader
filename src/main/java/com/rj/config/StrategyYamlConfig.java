package com.rj.config;

import java.util.List;

/**
 * Typed representation of a single strategy block from a YAML config file.
 * Section names mirror the YAML schema defined in docs/PRD.md section 6.
 *
 * All nested sections default to safe values so callers never receive nulls.
 */
public class StrategyYamlConfig {

    private boolean enabled = true;
    private List<String> symbols = List.of();
    private String timeframe = "M5";
    private ActiveHours activeHours = new ActiveHours();
    private int cooldownMinutes = 25;
    private int maxTradesPerDay = 10;
    private Indicators indicators = new Indicators();
    private Entry entry = new Entry();
    private Risk risk = new Risk();
    private Order order = new Order();

    // ── Getters / Setters ────────────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getSymbols() { return symbols; }
    public void setSymbols(List<String> symbols) { this.symbols = symbols != null ? symbols : List.of(); }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe != null ? timeframe : "M5"; }

    public ActiveHours getActiveHours() { return activeHours; }
    public void setActiveHours(ActiveHours activeHours) { this.activeHours = activeHours != null ? activeHours : new ActiveHours(); }

    public int getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(int cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }

    public int getMaxTradesPerDay() { return maxTradesPerDay; }
    public void setMaxTradesPerDay(int maxTradesPerDay) { this.maxTradesPerDay = maxTradesPerDay; }

    public Indicators getIndicators() { return indicators; }
    public void setIndicators(Indicators indicators) { this.indicators = indicators != null ? indicators : new Indicators(); }

    public Entry getEntry() { return entry; }
    public void setEntry(Entry entry) { this.entry = entry != null ? entry : new Entry(); }

    public Risk getRisk() { return risk; }
    public void setRisk(Risk risk) { this.risk = risk != null ? risk : new Risk(); }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order != null ? order : new Order(); }

    // ── Nested section: active_hours ─────────────────────────────────────────

    public static class ActiveHours {
        private String start = "09:15";
        private String end = "15:00";

        public String getStart() { return start; }
        public void setStart(String start) { this.start = start != null ? start : "09:15"; }

        public String getEnd() { return end; }
        public void setEnd(String end) { this.end = end != null ? end : "15:00"; }
    }

    // ── Nested section: indicators ───────────────────────────────────────────

    public static class Indicators {
        private int emaFast = 20;
        private int emaSlow = 50;
        private int rsiPeriod = 14;
        private int atrPeriod = 14;
        private int relVolPeriod = 20;
        private int minCandles = 21;

        public int getEmaFast() { return emaFast; }
        public void setEmaFast(int emaFast) { this.emaFast = emaFast; }

        public int getEmaSlow() { return emaSlow; }
        public void setEmaSlow(int emaSlow) { this.emaSlow = emaSlow; }

        public int getRsiPeriod() { return rsiPeriod; }
        public void setRsiPeriod(int rsiPeriod) { this.rsiPeriod = rsiPeriod; }

        public int getAtrPeriod() { return atrPeriod; }
        public void setAtrPeriod(int atrPeriod) { this.atrPeriod = atrPeriod; }

        public int getRelVolPeriod() { return relVolPeriod; }
        public void setRelVolPeriod(int relVolPeriod) { this.relVolPeriod = relVolPeriod; }

        public int getMinCandles() { return minCandles; }
        public void setMinCandles(int minCandles) { this.minCandles = minCandles; }
    }

    // ── Nested section: entry ────────────────────────────────────────────────

    public static class Entry {
        private double minConfidence = 0.85;
        private double relVolThreshold = 1.2;
        private String trendStrength = "STRONG_BULLISH";

        public double getMinConfidence() { return minConfidence; }
        public void setMinConfidence(double minConfidence) { this.minConfidence = minConfidence; }

        public double getRelVolThreshold() { return relVolThreshold; }
        public void setRelVolThreshold(double relVolThreshold) { this.relVolThreshold = relVolThreshold; }

        public String getTrendStrength() { return trendStrength; }
        public void setTrendStrength(String trendStrength) { this.trendStrength = trendStrength != null ? trendStrength : "STRONG_BULLISH"; }
    }

    // ── Nested section: risk ─────────────────────────────────────────────────

    public static class Risk {
        private double riskPerTradePct = 2.0;
        private double slAtrMultiplier = 2.0;
        private double tpRMultiple = 2.0;
        private double trailingActivationPct = 1.0;
        private double trailingStepPct = 1.0;
        private double maxExposurePct = 20.0;
        private int maxQty = 1000;
        private int maxConsecutiveLosses = 3;

        public double getRiskPerTradePct() { return riskPerTradePct; }
        public void setRiskPerTradePct(double riskPerTradePct) { this.riskPerTradePct = riskPerTradePct; }

        public double getSlAtrMultiplier() { return slAtrMultiplier; }
        public void setSlAtrMultiplier(double slAtrMultiplier) { this.slAtrMultiplier = slAtrMultiplier; }

        public double getTpRMultiple() { return tpRMultiple; }
        public void setTpRMultiple(double tpRMultiple) { this.tpRMultiple = tpRMultiple; }

        public double getTrailingActivationPct() { return trailingActivationPct; }
        public void setTrailingActivationPct(double trailingActivationPct) { this.trailingActivationPct = trailingActivationPct; }

        public double getTrailingStepPct() { return trailingStepPct; }
        public void setTrailingStepPct(double trailingStepPct) { this.trailingStepPct = trailingStepPct; }

        public double getMaxExposurePct() { return maxExposurePct; }
        public void setMaxExposurePct(double maxExposurePct) { this.maxExposurePct = maxExposurePct; }

        public int getMaxQty() { return maxQty; }
        public void setMaxQty(int maxQty) { this.maxQty = maxQty; }

        public int getMaxConsecutiveLosses() { return maxConsecutiveLosses; }
        public void setMaxConsecutiveLosses(int maxConsecutiveLosses) { this.maxConsecutiveLosses = maxConsecutiveLosses; }
    }

    // ── Nested section: order ────────────────────────────────────────────────

    public static class Order {
        private String type = "MARKET";
        private double slippageTolerance = 0.05;
        private String productType = "INTRADAY";

        public String getType() { return type; }
        public void setType(String type) { this.type = type != null ? type : "MARKET"; }

        public double getSlippageTolerance() { return slippageTolerance; }
        public void setSlippageTolerance(double slippageTolerance) { this.slippageTolerance = slippageTolerance; }

        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType != null ? productType : "INTRADAY"; }
    }
}
