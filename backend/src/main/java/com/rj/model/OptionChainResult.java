package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OptionChainResult {
    public final long callOi;
    public final long putOi;
    public final VixData indiaVix;
    public final List<OptionEntry> optionsChain;
    public final List<Expiry> expiryData;

    private OptionChainResult(long callOi, long putOi, VixData indiaVix,
                              List<OptionEntry> optionsChain, List<Expiry> expiryData) {
        this.callOi = callOi;
        this.putOi = putOi;
        this.indiaVix = indiaVix;
        this.optionsChain = Collections.unmodifiableList(optionsChain);
        this.expiryData = Collections.unmodifiableList(expiryData);
    }

    public static OptionChainResult from(JSONObject json) {
        if (json == null) return null;
        JSONObject data = json.optJSONObject("data");
        if (data == null) return null;

        List<OptionEntry> options = new ArrayList<>();
        JSONArray optArr = data.optJSONArray("optionsChain");
        if (optArr != null) {
            for (int i = 0; i < optArr.length(); i++) {
                options.add(OptionEntry.from(optArr.getJSONObject(i)));
            }
        }

        List<Expiry> expiries = new ArrayList<>();
        JSONArray expArr = data.optJSONArray("expiryData");
        if (expArr != null) {
            for (int i = 0; i < expArr.length(); i++) {
                expiries.add(Expiry.from(expArr.getJSONObject(i)));
            }
        }

        return new OptionChainResult(
                data.optLong("callOi"), data.optLong("putOi"),
                VixData.from(data.optJSONObject("indiavixData")),
                options, expiries
        );
    }

    @Override
    public String toString() {
        return "OptionChainResult{callOi=" + callOi + ", putOi=" + putOi +
                ", options=" + optionsChain.size() + "}";
    }

    public static class VixData {
        public final String symbol;
        public final String fyToken;
        public final String exchange;
        public final String description;
        public final double ltp;
        public final double ltpCh;
        public final double ltpChp;

        private VixData(String symbol, String fyToken, String exchange, String description,
                        double ltp, double ltpCh, double ltpChp) {
            this.symbol = symbol;
            this.fyToken = fyToken;
            this.exchange = exchange;
            this.description = description;
            this.ltp = ltp;
            this.ltpCh = ltpCh;
            this.ltpChp = ltpChp;
        }

        static VixData from(JSONObject j) {
            if (j == null) return null;
            return new VixData(
                    j.optString("symbol"), j.optString("fyToken"), j.optString("exchange"),
                    j.optString("description"), j.optDouble("ltp"),
                    j.optDouble("ltpch"), j.optDouble("ltpchp")
            );
        }
    }

    public static class OptionEntry {
        public final String symbol;
        public final String fyToken;
        public final String exchange;
        public final String description;
        public final String optionType;
        public final double ltp;
        public final double ltpCh;
        public final double ltpChp;
        public final double fp;
        public final double fpCh;
        public final double fpChp;
        public final double bid;
        public final double ask;
        public final double strikePrice;
        public final long oi;
        public final long oiCh;
        public final double oiChp;
        public final long volume;

        private OptionEntry(String symbol, String fyToken, String exchange, String description,
                            String optionType, double ltp, double ltpCh, double ltpChp,
                            double fp, double fpCh, double fpChp, double bid, double ask,
                            double strikePrice, long oi, long oiCh, double oiChp, long volume) {
            this.symbol = symbol;
            this.fyToken = fyToken;
            this.exchange = exchange;
            this.description = description;
            this.optionType = optionType;
            this.ltp = ltp;
            this.ltpCh = ltpCh;
            this.ltpChp = ltpChp;
            this.fp = fp;
            this.fpCh = fpCh;
            this.fpChp = fpChp;
            this.bid = bid;
            this.ask = ask;
            this.strikePrice = strikePrice;
            this.oi = oi;
            this.oiCh = oiCh;
            this.oiChp = oiChp;
            this.volume = volume;
        }

        static OptionEntry from(JSONObject j) {
            return new OptionEntry(
                    j.optString("symbol"), j.optString("fyToken"), j.optString("exchange"),
                    j.optString("description"), j.optString("option_type"),
                    j.optDouble("ltp"), j.optDouble("ltpch"), j.optDouble("ltpchp"),
                    j.optDouble("fp"), j.optDouble("fpch"), j.optDouble("fpchp"),
                    j.optDouble("bid"), j.optDouble("ask"), j.optDouble("strike_price"),
                    j.optLong("oi"), j.optLong("oich"), j.optDouble("oichp"), j.optLong("volume")
            );
        }

        @Override
        public String toString() {
            return "OptionEntry{symbol='" + symbol + "', strike=" + strikePrice +
                    ", type='" + optionType + "', ltp=" + ltp + "}";
        }
    }

    public static class Expiry {
        public final String date;
        public final long expiry;

        private Expiry(String date, long expiry) {
            this.date = date;
            this.expiry = expiry;
        }

        static Expiry from(JSONObject j) {
            return new Expiry(j.optString("date"), j.optLong("expiry"));
        }

        @Override
        public String toString() {
            return "Expiry{date='" + date + "'}";
        }
    }
}
