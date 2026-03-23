package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed representation of the Fyers GetHoldings() response.
 * Contains portfolio-level totals (overall) and a list of individual holdings.
 */
public class HoldingsSummary {

    // ── Overall POJO ─────────────────────────────────────────────────────────

    private final Overall overall;

    // ── Holding POJO ─────────────────────────────────────────────────────────
    private final List<Holding> holdings;

    // ── HoldingsSummary ──────────────────────────────────────────────────────

    private HoldingsSummary(Overall overall, List<Holding> holdings) {
        this.overall = overall;
        this.holdings = Collections.unmodifiableList(holdings);
    }

    public static HoldingsSummary from(JSONObject data) {
        Overall overall = Overall.from(data.optJSONObject("overall") != null
                ? data.getJSONObject("overall") : new JSONObject());

        JSONArray arr = data.optJSONArray("holdings");
        List<Holding> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(Holding.from(arr.getJSONObject(i)));
            }
        }
        return new HoldingsSummary(overall, list);
    }

    public Overall getOverall() {
        return overall;
    }

    public List<Holding> getHoldings() {
        return holdings;
    }

    @Override
    public String toString() {
        return "HoldingsSummary{overall=" + overall + ", holdings=" + holdings + '}';
    }

    public static final class Overall {
        private final double totalPl;
        private final int countTotal;
        private final double totalInvestment;
        private final double totalCurrentValue;
        private final double pnlPerc;

        private Overall(double totalPl, int countTotal, double totalInvestment,
                        double totalCurrentValue, double pnlPerc) {
            this.totalPl = totalPl;
            this.countTotal = countTotal;
            this.totalInvestment = totalInvestment;
            this.totalCurrentValue = totalCurrentValue;
            this.pnlPerc = pnlPerc;
        }

        public static Overall from(JSONObject o) {
            return new Overall(
                    o.optDouble("total_pl", 0),
                    o.optInt("count_total", 0),
                    o.optDouble("total_investment", 0),
                    o.optDouble("total_current_value", 0),
                    o.optDouble("pnl_perc", 0)
            );
        }

        public double getTotalPl() {
            return totalPl;
        }

        public int getCountTotal() {
            return countTotal;
        }

        public double getTotalInvestment() {
            return totalInvestment;
        }

        public double getTotalCurrentValue() {
            return totalCurrentValue;
        }

        public double getPnlPerc() {
            return pnlPerc;
        }


        @Override
        public String toString() {
            return "Overall{totalPl=" + totalPl +
                    ", countTotal=" + countTotal +
                    ", totalInvestment=" + totalInvestment +
                    ", totalCurrentValue=" + totalCurrentValue +
                    ", pnlPerc=" + pnlPerc + '}';
        }
    }

    public static final class Holding {
        private final String symbol;
        private final String isin;
        private final String fyToken;
        private final String holdingType;
        private final int id;
        private final int segment;
        private final int exchange;
        private final int quantity;
        private final int remainingQuantity;
        private final int remainingPledgeQuantity;
        private final int collateralQuantity;
        private final int qtyT1;
        private final double costPrice;
        private final double ltp;
        private final double marketVal;
        private final double pl;

        private Holding(Builder b) {
            this.symbol = b.symbol;
            this.isin = b.isin;
            this.fyToken = b.fyToken;
            this.holdingType = b.holdingType;
            this.id = b.id;
            this.segment = b.segment;
            this.exchange = b.exchange;
            this.quantity = b.quantity;
            this.remainingQuantity = b.remainingQuantity;
            this.remainingPledgeQuantity = b.remainingPledgeQuantity;
            this.collateralQuantity = b.collateralQuantity;
            this.qtyT1 = b.qtyT1;
            this.costPrice = b.costPrice;
            this.ltp = b.ltp;
            this.marketVal = b.marketVal;
            this.pl = b.pl;
        }

        public static Holding from(JSONObject o) {
            return new Builder()
                    .symbol(o.optString("symbol", ""))
                    .isin(o.optString("isin", ""))
                    .fyToken(o.optString("fyToken", ""))
                    .holdingType(o.optString("holdingType", ""))
                    .id(o.optInt("id", 0))
                    .segment(o.optInt("segment", 0))
                    .exchange(o.optInt("exchange", 0))
                    .quantity(o.optInt("quantity", 0))
                    .remainingQuantity(o.optInt("remainingQuantity", 0))
                    .remainingPledgeQuantity(o.optInt("remainingPledgeQuantity", 0))
                    .collateralQuantity(o.optInt("collateralQuantity", 0))
                    .qtyT1(o.optInt("qty_t1", 0))
                    .costPrice(o.optDouble("costPrice", 0))
                    .ltp(o.optDouble("ltp", 0))
                    .marketVal(o.optDouble("marketVal", 0))
                    .pl(o.optDouble("pl", 0))
                    .build();
        }

        public String getSymbol() {
            return symbol;
        }

        public String getIsin() {
            return isin;
        }

        public String getFyToken() {
            return fyToken;
        }

        public String getHoldingType() {
            return holdingType;
        }

        public int getId() {
            return id;
        }

        public int getSegment() {
            return segment;
        }

        public int getExchange() {
            return exchange;
        }

        public int getQuantity() {
            return quantity;
        }

        public int getRemainingQuantity() {
            return remainingQuantity;
        }

        public int getRemainingPledgeQuantity() {
            return remainingPledgeQuantity;
        }

        public int getCollateralQuantity() {
            return collateralQuantity;
        }

        public int getQtyT1() {
            return qtyT1;
        }

        public double getCostPrice() {
            return costPrice;
        }

        public double getLtp() {
            return ltp;
        }

        public double getMarketVal() {
            return marketVal;
        }

        public double getPl() {
            return pl;
        }

        @Override
        public String toString() {
            return "Holding{symbol='" + symbol + '\'' +
                    ", isin='" + isin + '\'' +
                    ", quantity=" + quantity +
                    ", costPrice=" + costPrice +
                    ", ltp=" + ltp +
                    ", marketVal=" + marketVal +
                    ", pl=" + pl + '}';
        }

        public static final class Builder {
            private String symbol = "", isin = "", fyToken = "", holdingType = "";
            private int id, segment, exchange, quantity, remainingQuantity;
            private int remainingPledgeQuantity, collateralQuantity, qtyT1;
            private double costPrice, ltp, marketVal, pl;

            public Builder symbol(String v) {
                symbol = v;
                return this;
            }

            public Builder isin(String v) {
                isin = v;
                return this;
            }

            public Builder fyToken(String v) {
                fyToken = v;
                return this;
            }

            public Builder holdingType(String v) {
                holdingType = v;
                return this;
            }

            public Builder id(int v) {
                id = v;
                return this;
            }

            public Builder segment(int v) {
                segment = v;
                return this;
            }

            public Builder exchange(int v) {
                exchange = v;
                return this;
            }

            public Builder quantity(int v) {
                quantity = v;
                return this;
            }

            public Builder remainingQuantity(int v) {
                remainingQuantity = v;
                return this;
            }

            public Builder remainingPledgeQuantity(int v) {
                remainingPledgeQuantity = v;
                return this;
            }

            public Builder collateralQuantity(int v) {
                collateralQuantity = v;
                return this;
            }

            public Builder qtyT1(int v) {
                qtyT1 = v;
                return this;
            }

            public Builder costPrice(double v) {
                costPrice = v;
                return this;
            }

            public Builder ltp(double v) {
                ltp = v;
                return this;
            }

            public Builder marketVal(double v) {
                marketVal = v;
                return this;
            }

            public Builder pl(double v) {
                pl = v;
                return this;
            }

            public Holding build() {
                return new Holding(this);
            }
        }
    }
}