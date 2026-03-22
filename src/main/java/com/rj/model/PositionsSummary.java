package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PositionsSummary {
    public final List<NetPosition> netPositions;
    public final Overall overall;

    public static class NetPosition {
        public final String symbol;
        public final String id;
        public final String productType;
        public final String fyToken;
        public final String crossCurrency;
        public final int segment;
        public final int exchange;
        public final int side;
        public final int netQty;
        public final int buyQty;
        public final int sellQty;
        public final int dayBuyQty;
        public final int daySellQty;
        public final int cfBuyQty;
        public final int cfSellQty;
        public final int qty;
        public final int qtyMultiCom;
        public final int slNo;
        public final double buyAvg;
        public final double sellAvg;
        public final double netAvg;
        public final double buyVal;
        public final double sellVal;
        public final double ltp;
        public final double pl;
        public final double realizedProfit;
        public final double unrealizedProfit;
        public final double rbiRefRate;

        private NetPosition(String symbol, String id, String productType, String fyToken, String crossCurrency,
                            int segment, int exchange, int side, int netQty, int buyQty, int sellQty,
                            int dayBuyQty, int daySellQty, int cfBuyQty, int cfSellQty, int qty, int qtyMultiCom, int slNo,
                            double buyAvg, double sellAvg, double netAvg, double buyVal, double sellVal,
                            double ltp, double pl, double realizedProfit, double unrealizedProfit, double rbiRefRate) {
            this.symbol = symbol; this.id = id; this.productType = productType;
            this.fyToken = fyToken; this.crossCurrency = crossCurrency;
            this.segment = segment; this.exchange = exchange; this.side = side;
            this.netQty = netQty; this.buyQty = buyQty; this.sellQty = sellQty;
            this.dayBuyQty = dayBuyQty; this.daySellQty = daySellQty;
            this.cfBuyQty = cfBuyQty; this.cfSellQty = cfSellQty;
            this.qty = qty; this.qtyMultiCom = qtyMultiCom; this.slNo = slNo;
            this.buyAvg = buyAvg; this.sellAvg = sellAvg; this.netAvg = netAvg;
            this.buyVal = buyVal; this.sellVal = sellVal;
            this.ltp = ltp; this.pl = pl;
            this.realizedProfit = realizedProfit; this.unrealizedProfit = unrealizedProfit;
            this.rbiRefRate = rbiRefRate;
        }

        static NetPosition from(JSONObject j) {
            return new NetPosition(
                j.optString("symbol"), j.optString("id"), j.optString("productType"),
                j.optString("fyToken"), j.optString("crossCurrency"),
                j.optInt("segment"), j.optInt("exchange"), j.optInt("side"),
                j.optInt("netQty"), j.optInt("buyQty"), j.optInt("sellQty"),
                j.optInt("dayBuyQty"), j.optInt("daySellQty"),
                j.optInt("cfBuyQty"), j.optInt("cfSellQty"),
                j.optInt("qty"), j.optInt("qtyMulti_com"), j.optInt("slNo"),
                j.optDouble("buyAvg"), j.optDouble("sellAvg"), j.optDouble("netAvg"),
                j.optDouble("buyVal"), j.optDouble("sellVal"),
                j.optDouble("ltp"), j.optDouble("pl"),
                j.optDouble("realized_profit"), j.optDouble("unrealized_profit"),
                j.optDouble("rbiRefRate")
            );
        }

        @Override public String toString() {
            return "NetPosition{symbol='" + symbol + "', netQty=" + netQty + ", ltp=" + ltp + ", pl=" + pl + "}";
        }
    }

    public static class Overall {
        public final int countOpen;
        public final int countTotal;
        public final double plRealized;
        public final double plUnrealized;
        public final double plTotal;

        private Overall(int countOpen, int countTotal, double plRealized, double plUnrealized, double plTotal) {
            this.countOpen = countOpen; this.countTotal = countTotal;
            this.plRealized = plRealized; this.plUnrealized = plUnrealized; this.plTotal = plTotal;
        }

        static Overall from(JSONObject j) {
            if (j == null) return new Overall(0, 0, 0, 0, 0);
            return new Overall(
                j.optInt("count_open"), j.optInt("count_total"),
                j.optDouble("pl_realized"), j.optDouble("pl_unrealized"), j.optDouble("pl_total")
            );
        }

        @Override public String toString() {
            return "Overall{countOpen=" + countOpen + ", plTotal=" + plTotal + "}";
        }
    }

    private PositionsSummary(List<NetPosition> netPositions, Overall overall) {
        this.netPositions = Collections.unmodifiableList(netPositions);
        this.overall = overall;
    }

    public static PositionsSummary from(JSONObject json) {
        if (json == null) return null;
        List<NetPosition> positions = new ArrayList<>();
        JSONArray arr = json.optJSONArray("netPositions");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                positions.add(NetPosition.from(arr.getJSONObject(i)));
            }
        }
        return new PositionsSummary(positions, Overall.from(json.optJSONObject("overall")));
    }

    @Override public String toString() {
        return "PositionsSummary{positions=" + netPositions.size() + ", overall=" + overall + "}";
    }
}
