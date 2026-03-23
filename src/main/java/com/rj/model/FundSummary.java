package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed representation of the Fyers GetFunds() response.
 * The API returns a fixed list of fund_limit entries identified by id (1–10).
 * Named getters map directly to those well-known ids for convenience.
 */
public class FundSummary {

    // ── Entry POJO ───────────────────────────────────────────────────────────

    private final List<FundLimit> fundLimits;

    // ── Summary ──────────────────────────────────────────────────────────────

    private FundSummary(List<FundLimit> fundLimits) {
        this.fundLimits = Collections.unmodifiableList(fundLimits);
    }

    public static FundSummary from(JSONObject data) {
        JSONArray arr = data.optJSONArray("fund_limit");
        List<FundLimit> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(FundLimit.from(arr.getJSONObject(i)));
            }
        }
        return new FundSummary(list);
    }

    /** All fund limit entries as received from the API. */
    public List<FundLimit> getFundLimits() {
        return fundLimits;
    }

    /** Returns the entry for the given id, or null if not present. */
    public FundLimit getById(int id) {
        return fundLimits.stream().filter(f -> f.id == id).findFirst().orElse(null);
    }

    private double equity(int id) {
        FundLimit f = getById(id);
        return f == null ? 0 : f.equityAmount;
    }

    /** id=1  Total Balance */
    public double getTotalBalance() {
        return equity(1);
    }

    // ── Named accessors (equity segment) for well-known ids ──────────────────

    /** id=2  Utilized Amount */
    public double getUtilizedAmount() {
        return equity(2);
    }

    /** id=3  Clear Balance */
    public double getClearBalance() {
        return equity(3);
    }

    /** id=4  Realized P&L */
    public double getRealizedPnl() {
        return equity(4);
    }

    /** id=5  Collaterals */
    public double getCollaterals() {
        return equity(5);
    }

    /** id=6  Fund Transfer */
    public double getFundTransfer() {
        return equity(6);
    }

    /** id=7  Receivables */
    public double getReceivables() {
        return equity(7);
    }

    /** id=8  Adhoc Limit */
    public double getAdhocLimit() {
        return equity(8);
    }

    /** id=9  Limit at start of the day */
    public double getLimitAtStartOfDay() {
        return equity(9);
    }

    /** id=10 Available Balance */
    public double getAvailableBalance() {
        return equity(10);
    }

    @Override
    public String toString() {
        return "FundSummary{" +
                "totalBalance=" + getTotalBalance() +
                ", utilizedAmount=" + getUtilizedAmount() +
                ", clearBalance=" + getClearBalance() +
                ", availableBalance=" + getAvailableBalance() +
                ", receivables=" + getReceivables() +
                ", realizedPnl=" + getRealizedPnl() +
                ", collaterals=" + getCollaterals() +
                ", adhocLimit=" + getAdhocLimit() +
                ", limitAtStartOfDay=" + getLimitAtStartOfDay() +
                ", fundTransfer=" + getFundTransfer() +
                '}';
    }

    public static final class FundLimit {
        private final int id;
        private final String title;
        private final double equityAmount;
        private final double commodityAmount;

        private FundLimit(int id, String title, double equityAmount, double commodityAmount) {
            this.id = id;
            this.title = title;
            this.equityAmount = equityAmount;
            this.commodityAmount = commodityAmount;
        }

        public static FundLimit from(JSONObject o) {
            return new FundLimit(
                    o.optInt("id", 0),
                    o.optString("title", ""),
                    o.optDouble("equityAmount", 0),
                    o.optDouble("commodityAmount", 0)
            );
        }

        public int getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public double getEquityAmount() {
            return equityAmount;
        }

        public double getCommodityAmount() {
            return commodityAmount;
        }

        @Override
        public String toString() {
            return "FundLimit{id=" + id + ", title='" + title + '\'' +
                    ", equity=" + equityAmount + ", commodity=" + commodityAmount + '}';
        }
    }
}
