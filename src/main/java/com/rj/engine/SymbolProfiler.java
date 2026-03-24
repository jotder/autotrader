package com.rj.engine;

import com.rj.model.Candle;
import com.rj.model.SymbolProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;

/**
 * Computes statistical profiles for individual symbols from M1 candle data.
 * <p>
 * Reads from {@link CandleDatabase}, groups candles by trading day,
 * and computes volatility, volume, trend, and gap metrics.
 */
public class SymbolProfiler {

    private static final Logger log = LoggerFactory.getLogger(SymbolProfiler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MARKET_OPEN_HOUR = 9;
    private static final int MARKET_OPEN_MIN = 15;
    private static final int MARKET_CLOSE_HOUR = 15;
    private static final int MARKET_CLOSE_MIN = 30;
    private static final int MINUTES_PER_SESSION = (MARKET_CLOSE_HOUR - MARKET_OPEN_HOUR) * 60
            + (MARKET_CLOSE_MIN - MARKET_OPEN_MIN); // 375 mins
    private static final int SLOTS_5MIN = MINUTES_PER_SESSION / 5; // 75 slots

    private final CandleDatabase db;

    public SymbolProfiler(CandleDatabase db) {
        this.db = db;
    }

    /**
     * Compute a statistical profile for a symbol over a date range.
     *
     * @return profile, or null if insufficient data
     */
    public SymbolProfile profile(String symbol, LocalDate from, LocalDate to) {
        List<Candle> allCandles = db.loadRange(symbol, from, to);
        if (allCandles.isEmpty()) {
            log.warn("No M1 data for {} in range {} to {}", symbol, from, to);
            return null;
        }

        // Group candles by trading day
        Map<LocalDate, List<Candle>> byDay = groupByDay(allCandles);
        int tradingDays = byDay.size();
        if (tradingDays < 2) {
            log.warn("Need at least 2 trading days for profiling, got {}", tradingDays);
            return null;
        }

        // Compute per-day metrics
        var dailyRanges = new ArrayList<Double>();
        var dailyReturns = new ArrayList<Double>();
        var morningRanges = new ArrayList<Double>();
        int gapUpCount = 0, gapDownCount = 0;
        double[] volumeSlotAccum = new double[SLOTS_5MIN];
        int[] volumeSlotCount = new int[SLOTS_5MIN];
        double[] hourlyVolSum = new double[7]; // 09:xx to 15:xx
        int[] hourlyVolCount = new int[7];
        int totalUpRuns = 0, totalDownRuns = 0;
        int upRunCount = 0, downRunCount = 0;

        double prevClose = -1;
        List<LocalDate> sortedDates = new ArrayList<>(byDay.keySet());
        Collections.sort(sortedDates);

        for (LocalDate date : sortedDates) {
            List<Candle> dayCandles = byDay.get(date);
            if (dayCandles.isEmpty()) continue;

            Candle first = dayCandles.get(0);
            Candle last = dayCandles.get(dayCandles.size() - 1);
            double dayHigh = dayCandles.stream().mapToDouble(c -> c.high).max().orElse(first.open);
            double dayLow = dayCandles.stream().mapToDouble(c -> c.low).min().orElse(first.open);
            double dayOpen = first.open;
            double dayClose = last.close;

            // Daily range and return
            if (dayOpen > 0) {
                dailyRanges.add((dayHigh - dayLow) / dayOpen * 100.0);
                dailyReturns.add((dayClose - dayOpen) / dayOpen * 100.0);
            }

            // Gap analysis
            if (prevClose > 0 && dayOpen > 0) {
                double gapPct = (dayOpen - prevClose) / prevClose * 100.0;
                if (gapPct > 0.5) gapUpCount++;
                if (gapPct < -0.5) gapDownCount++;
            }
            prevClose = dayClose;

            // Morning range (first 30 mins = first 30 M1 candles)
            int morningBars = Math.min(30, dayCandles.size());
            double mHigh = dayCandles.subList(0, morningBars).stream().mapToDouble(c -> c.high).max().orElse(dayOpen);
            double mLow = dayCandles.subList(0, morningBars).stream().mapToDouble(c -> c.low).min().orElse(dayOpen);
            double dayRange = dayHigh - dayLow;
            if (dayRange > 0) {
                morningRanges.add((mHigh - mLow) / dayRange * 100.0);
            }

            // Volume by 5-min slot + hourly volatility
            for (Candle c : dayCandles) {
                ZonedDateTime cTime = Instant.ofEpochSecond(c.timestamp).atZone(IST);
                int minuteOfDay = cTime.getHour() * 60 + cTime.getMinute();
                int marketMinute = minuteOfDay - (MARKET_OPEN_HOUR * 60 + MARKET_OPEN_MIN);
                if (marketMinute >= 0 && marketMinute < MINUTES_PER_SESSION) {
                    int slot = marketMinute / 5;
                    if (slot < SLOTS_5MIN) {
                        volumeSlotAccum[slot] += c.volume;
                        volumeSlotCount[slot]++;
                    }
                }
                int hourIdx = cTime.getHour() - MARKET_OPEN_HOUR;
                if (hourIdx >= 0 && hourIdx < hourlyVolSum.length && c.open > 0) {
                    hourlyVolSum[hourIdx] += Math.abs(c.close - c.open) / c.open * 100.0;
                    hourlyVolCount[hourIdx]++;
                }
            }

            // Consecutive up/down bars
            int currentUp = 0, currentDown = 0;
            for (Candle c : dayCandles) {
                if (c.close >= c.open) {
                    currentUp++;
                    if (currentDown > 0) {
                        totalDownRuns += currentDown;
                        downRunCount++;
                        currentDown = 0;
                    }
                } else {
                    currentDown++;
                    if (currentUp > 0) {
                        totalUpRuns += currentUp;
                        upRunCount++;
                        currentUp = 0;
                    }
                }
            }
            if (currentUp > 0) { totalUpRuns += currentUp; upRunCount++; }
            if (currentDown > 0) { totalDownRuns += currentDown; downRunCount++; }
        }

        // Compute averages
        double avgRange = dailyRanges.stream().mapToDouble(d -> d).average().orElse(0);
        double avgReturn = dailyReturns.stream().mapToDouble(d -> d).average().orElse(0);
        double stdDev = stddev(dailyReturns, avgReturn);

        double[] avgVolumeBySlot = new double[SLOTS_5MIN];
        for (int i = 0; i < SLOTS_5MIN; i++) {
            avgVolumeBySlot[i] = volumeSlotCount[i] > 0 ? volumeSlotAccum[i] / volumeSlotCount[i] : 0;
        }

        double[] hourlyVol = new double[hourlyVolSum.length];
        for (int i = 0; i < hourlyVol.length; i++) {
            hourlyVol[i] = hourlyVolCount[i] > 0 ? hourlyVolSum[i] / hourlyVolCount[i] : 0;
        }

        double avgUpRun = upRunCount > 0 ? (double) totalUpRuns / upRunCount : 0;
        double avgDownRun = downRunCount > 0 ? (double) totalDownRuns / downRunCount : 0;
        double gapUpPct = tradingDays > 1 ? (double) gapUpCount / (tradingDays - 1) * 100.0 : 0;
        double gapDownPct = tradingDays > 1 ? (double) gapDownCount / (tradingDays - 1) * 100.0 : 0;
        double morningRangePctAvg = morningRanges.stream().mapToDouble(d -> d).average().orElse(0);

        return new SymbolProfile(
                symbol, tradingDays, from, to,
                avgRange, avgReturn, stdDev,
                avgVolumeBySlot,
                avgUpRun, avgDownRun,
                gapUpPct, gapDownPct,
                morningRangePctAvg, hourlyVol
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<LocalDate, List<Candle>> groupByDay(List<Candle> candles) {
        var map = new TreeMap<LocalDate, List<Candle>>();
        for (Candle c : candles) {
            LocalDate date = Instant.ofEpochSecond(c.timestamp).atZone(IST).toLocalDate();
            map.computeIfAbsent(date, k -> new ArrayList<>()).add(c);
        }
        return map;
    }

    private static double stddev(List<Double> values, double mean) {
        if (values.size() < 2) return 0;
        double sumSqDiff = 0;
        for (double v : values) {
            sumSqDiff += (v - mean) * (v - mean);
        }
        return Math.sqrt(sumSqDiff / (values.size() - 1));
    }
}
