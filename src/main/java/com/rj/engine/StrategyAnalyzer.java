package com.rj.engine;

import com.rj.model.TradeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes comprehensive performance metrics from a list of closed {@link TradeRecord}s.
 *
 * <p>Produces a {@link Report} containing:</p>
 * <ul>
 *   <li>Overall metrics: win rate, profit factor, expectancy, Sharpe, max drawdown</li>
 *   <li>Per-strategy breakdown</li>
 *   <li>Per-symbol breakdown</li>
 *   <li>Best / worst / average R-multiple achieved</li>
 *   <li>Actionable improvement suggestions</li>
 * </ul>
 */
public class StrategyAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(StrategyAnalyzer.class);

    /** Minimum trades required before producing statistically meaningful suggestions. */
    private static final int MIN_TRADES_FOR_SUGGESTION = 10;

    /** Entry point — compute report from a list of completed trades. */
    public static Report analyze(List<TradeRecord> trades) {
        List<TradeRecord> closed = trades.stream()
                .filter(TradeRecord::isClosed)
                .sorted(Comparator.comparing(TradeRecord::getExitTime))
                .collect(Collectors.toList());

        if (closed.isEmpty()) {
            return Report.empty();
        }

        Metrics overall = computeMetrics(closed);
        Map<String, Metrics> byStrategy = groupAndCompute(closed, TradeRecord::getStrategyId);
        Map<String, Metrics> bySymbol = groupAndCompute(closed, TradeRecord::getSymbol);

        List<String> suggestions = generateSuggestions(overall, byStrategy, bySymbol, closed);

        return new Report(closed.size(), overall, byStrategy, bySymbol, suggestions, buildEquityCurve(closed));
    }

    // ── Core metrics ─────────────────────────────────────────────────────────

    private static Metrics computeMetrics(List<TradeRecord> trades) {
        if (trades.isEmpty()) return Metrics.ZERO;

        int wins = (int) trades.stream().filter(TradeRecord::isWinner).count();
        int losses = (int) trades.stream().filter(TradeRecord::isLoser).count();
        int total = trades.size();
        double winRate = total > 0 ? (double) wins / total : 0;

        double grossProfit = trades.stream()
                .filter(TradeRecord::isWinner)
                .mapToDouble(t -> t.getPnl()).sum();
        double grossLoss = Math.abs(trades.stream()
                .filter(TradeRecord::isLoser)
                .mapToDouble(t -> t.getPnl()).sum());

        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : grossProfit > 0 ? Double.MAX_VALUE : 0;
        double totalPnl = grossProfit - grossLoss;

        double avgWin = wins > 0 ? grossProfit / wins : 0;
        double avgLoss = losses > 0 ? grossLoss / losses : 0;
        double expectancy = (winRate * avgWin) - ((1 - winRate) * avgLoss);

        // Per-trade returns for Sharpe
        double[] returns = trades.stream().mapToDouble(t -> t.getPnl()).toArray();
        double mean = totalPnl / total;
        double variance = 0;
        for (double r : returns) variance += Math.pow(r - mean, 2);
        variance /= total;
        double stdDev = Math.sqrt(variance);
        // Annualise using ~252 trading days; approximate trades per year
        double tradesPerYear = 252.0;
        double sharpe = stdDev > 0 ? (mean / stdDev) * Math.sqrt(tradesPerYear) : 0;

        double maxDrawdown = computeMaxDrawdown(trades);

        double avgR = trades.stream()
                .filter(t -> t.getRMultipleAchieved() != null)
                .mapToDouble(t -> t.getRMultipleAchieved())
                .average().orElse(0);

        double avgHoldMinutes = trades.stream()
                .filter(t -> t.getHoldDuration() != null)
                .mapToLong(t -> t.getHoldDuration().toMinutes())
                .average().orElse(0);

        int maxConsecLosses = maxConsecutiveLosses(trades);

        return new Metrics(total, wins, losses, winRate, totalPnl, grossProfit, grossLoss,
                profitFactor, expectancy, sharpe, maxDrawdown, avgR, avgWin, avgLoss,
                avgHoldMinutes, maxConsecLosses);
    }

    private static double computeMaxDrawdown(List<TradeRecord> trades) {
        double peak = 0, equity = 0, maxDD = 0;
        for (TradeRecord t : trades) {
            equity += t.getPnl();
            if (equity > peak) peak = equity;
            double dd = peak > 0 ? (peak - equity) / peak : 0;
            if (dd > maxDD) maxDD = dd;
        }
        return maxDD;
    }

    private static int maxConsecutiveLosses(List<TradeRecord> trades) {
        int max = 0, cur = 0;
        for (TradeRecord t : trades) {
            if (t.isLoser()) {
                cur++;
                max = Math.max(max, cur);
            } else {
                cur = 0;
            }
        }
        return max;
    }

    private static Map<String, Metrics> groupAndCompute(
            List<TradeRecord> trades,
            java.util.function.Function<TradeRecord, String> keyFn) {
        Map<String, Metrics> result = new LinkedHashMap<>();
        trades.stream()
                .collect(Collectors.groupingBy(keyFn))
                .forEach((k, v) -> result.put(k, computeMetrics(v)));
        return result;
    }

    private static List<Double> buildEquityCurve(List<TradeRecord> trades) {
        List<Double> curve = new ArrayList<>();
        double equity = 0;
        for (TradeRecord t : trades) {
            equity += t.getPnl();
            curve.add(equity);
        }
        return curve;
    }

    // ── Suggestions ───────────────────────────────────────────────────────────

    private static List<String> generateSuggestions(
            Metrics overall,
            Map<String, Metrics> byStrategy,
            Map<String, Metrics> bySymbol,
            List<TradeRecord> trades) {

        List<String> s = new ArrayList<>();
        if (trades.size() < MIN_TRADES_FOR_SUGGESTION) {
            s.add("Not enough trades (" + trades.size() + ") for statistically reliable suggestions. Need " + MIN_TRADES_FOR_SUGGESTION + "+.");
            return s;
        }

        // ── Overall ──────────────────────────────────────────────────────────
        if (overall.winRate < 0.40) {
            s.add("OVERALL: Win rate " + pct(overall.winRate) + " is below 40%. Review entry filters — consider raising minimum confidence threshold.");
        }
        if (overall.profitFactor < 1.0) {
            s.add("OVERALL: Profit factor " + fmt2(overall.profitFactor) + " < 1.0 — system is losing money. Widen target (raise R-multiple) or tighten SL.");
        }
        if (overall.maxDrawdown > 0.20) {
            s.add("OVERALL: Max drawdown " + pct(overall.maxDrawdown) + " > 20%. Reduce position size or add kill switch at lower daily loss limit.");
        }
        if (overall.sharpe < 0) {
            s.add("OVERALL: Negative Sharpe ratio (" + fmt2(overall.sharpe) + "). Risk-adjusted return is negative — reduce trade frequency or improve accuracy.");
        }
        if (overall.avgR < 1.0) {
            s.add("OVERALL: Average R achieved " + fmt2(overall.avgR) + " < 1.0. Exits are too early or SL too close. Consider wider targets or tighter SL.");
        }
        if (overall.maxConsecLosses >= 5) {
            s.add("OVERALL: " + overall.maxConsecLosses + " consecutive losses observed. Add strategy-level suspension after 3 losses.");
        }

        // ── Per-strategy ─────────────────────────────────────────────────────
        byStrategy.forEach((strategy, m) -> {
            if (m.total < 5) return;
            if (m.winRate < 0.35) {
                s.add("STRATEGY [" + strategy + "]: Win rate " + pct(m.winRate) + " very low. Consider disabling or re-parameterizing.");
            }
            if (m.profitFactor < 1.0 && m.total >= 10) {
                s.add("STRATEGY [" + strategy + "]: Negative profit factor " + fmt2(m.profitFactor) + " over " + m.total + " trades. Disable until root cause identified.");
            }
            if (m.winRate > 0.65 && m.avgR < 0.8) {
                s.add("STRATEGY [" + strategy + "]: High win rate (" + pct(m.winRate) + ") but low average R (" + fmt2(m.avgR) + "). Exits are too early — trail the winner harder.");
            }
        });

        // ── Per-symbol ───────────────────────────────────────────────────────
        bySymbol.forEach((sym, m) -> {
            if (m.total < 5) return;
            if (m.totalPnl < 0) {
                s.add("SYMBOL [" + sym + "]: Net negative PnL " + fmt2(m.totalPnl) + " over " + m.total + " trades. Consider removing from universe or using symbol-specific parameters.");
            }
        });

        // ── Exit reason analysis ──────────────────────────────────────────────
        long slCount = trades.stream().filter(t -> t.getExitReason() == PositionMonitor.ExitReason.STOP_LOSS).count();
        long tpCount = trades.stream().filter(t -> t.getExitReason() == PositionMonitor.ExitReason.TAKE_PROFIT).count();
        long trCount = trades.stream().filter(t -> t.getExitReason() == PositionMonitor.ExitReason.TRAILING_STOP).count();
        long sqCount = trades.stream().filter(t -> t.getExitReason() == PositionMonitor.ExitReason.FORCE_SQUAREOFF).count();
        double slPct = (double) slCount / trades.size();
        if (slPct > 0.60) {
            s.add("EXIT: " + pct(slPct) + " of trades exit via stop loss. Entries may be poor quality or SL is too tight.");
        }
        if (sqCount > trades.size() * 0.20) {
            s.add("EXIT: " + sqCount + " trades force-closed at EOD (" + pct((double) sqCount / trades.size()) + "). Consider earlier target/trail or avoid late entries.");
        }
        if (trCount > tpCount && trCount > 0) {
            s.add("EXIT: More trailing exits (" + trCount + ") than TP exits (" + tpCount + "). Trailing is capturing profits well — this is positive.");
        }

        if (s.isEmpty()) {
            s.add("All metrics look healthy. Continue monitoring with live forward test before scaling capital.");
        }

        return s;
    }

    // ── Report ────────────────────────────────────────────────────────────────

    private static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }

    // ── Metrics value object ──────────────────────────────────────────────────

    private static String fmt2(double v) {
        return String.format("%.2f", v);
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    public static final class Report {
        private final int totalTrades;
        private final Metrics overall;
        private final Map<String, Metrics> byStrategy;
        private final Map<String, Metrics> bySymbol;
        private final List<String> suggestions;
        private final List<Double> equityCurve;

        Report(int totalTrades, Metrics overall,
               Map<String, Metrics> byStrategy, Map<String, Metrics> bySymbol,
               List<String> suggestions, List<Double> equityCurve) {
            this.totalTrades = totalTrades;
            this.overall = overall;
            this.byStrategy = byStrategy;
            this.bySymbol = bySymbol;
            this.suggestions = suggestions;
            this.equityCurve = equityCurve;
        }

        static Report empty() {
            return new Report(0, Metrics.ZERO,
                    new LinkedHashMap<>(), new LinkedHashMap<>(),
                    List.of("No closed trades to analyze."), new ArrayList<>());
        }

        public int totalTrades() {
            return totalTrades;
        }

        public Metrics overall() {
            return overall;
        }

        public Map<String, Metrics> byStrategy() {
            return byStrategy;
        }

        public Map<String, Metrics> bySymbol() {
            return bySymbol;
        }

        public List<String> suggestions() {
            return suggestions;
        }

        public List<Double> equityCurve() {
            return equityCurve;
        }

        /** Human-readable summary. */
        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n═══════════════ BACKTEST / FORWARD TEST REPORT ═══════════════\n");
            sb.append("Total trades : ").append(totalTrades).append("\n");
            sb.append("Win rate     : ").append(pct(overall.winRate)).append("\n");
            sb.append("Profit factor: ").append(fmt2(overall.profitFactor)).append("\n");
            sb.append("Net PnL      : ₹").append(fmt2(overall.totalPnl)).append("\n");
            sb.append("Expectancy   : ₹").append(fmt2(overall.expectancy)).append(" / trade\n");
            sb.append("Sharpe ratio : ").append(fmt2(overall.sharpe)).append("\n");
            sb.append("Max drawdown : ").append(pct(overall.maxDrawdown)).append("\n");
            sb.append("Avg R achieved: ").append(fmt2(overall.avgR)).append("\n");
            sb.append("Avg hold time : ").append(fmt2(overall.avgHoldMinutes)).append(" min\n");
            sb.append("Max consec loss: ").append(overall.maxConsecLosses).append("\n");

            if (!byStrategy.isEmpty()) {
                sb.append("\n─── By Strategy ───────────────────────────────────────────────\n");
                byStrategy.forEach((k, m) -> sb.append(String.format(
                        "  %-25s  trades=%d  win=%s  pf=%.2f  PnL=₹%.2f\n",
                        k, m.total, pct(m.winRate), m.profitFactor, m.totalPnl)));
            }

            if (!bySymbol.isEmpty()) {
                sb.append("\n─── By Symbol ─────────────────────────────────────────────────\n");
                bySymbol.forEach((k, m) -> sb.append(String.format(
                        "  %-25s  trades=%d  win=%s  pf=%.2f  PnL=₹%.2f\n",
                        k, m.total, pct(m.winRate), m.profitFactor, m.totalPnl)));
            }

            sb.append("\n─── Suggestions ────────────────────────────────────────────────\n");
            suggestions.forEach(s -> sb.append("  • ").append(s).append("\n"));
            sb.append("═══════════════════════════════════════════════════════════════\n");
            return sb.toString();
        }

        @Override
        public String toString() {
            return summary();
        }
    }

    public static final class Metrics {
        static final Metrics ZERO = new Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        public final int total;
        public final int wins;
        public final int losses;
        public final double winRate;
        public final double totalPnl;
        public final double grossProfit;
        public final double grossLoss;
        public final double profitFactor;
        public final double expectancy;
        public final double sharpe;
        public final double maxDrawdown;
        public final double avgR;
        public final double avgWin;
        public final double avgLoss;
        public final double avgHoldMinutes;
        public final int maxConsecLosses;

        Metrics(int total, int wins, int losses, double winRate,
                double totalPnl, double grossProfit, double grossLoss,
                double profitFactor, double expectancy, double sharpe,
                double maxDrawdown, double avgR, double avgWin, double avgLoss,
                double avgHoldMinutes, int maxConsecLosses) {
            this.total = total;
            this.wins = wins;
            this.losses = losses;
            this.winRate = winRate;
            this.totalPnl = totalPnl;
            this.grossProfit = grossProfit;
            this.grossLoss = grossLoss;
            this.profitFactor = profitFactor;
            this.expectancy = expectancy;
            this.sharpe = sharpe;
            this.maxDrawdown = maxDrawdown;
            this.avgR = avgR;
            this.avgWin = avgWin;
            this.avgLoss = avgLoss;
            this.avgHoldMinutes = avgHoldMinutes;
            this.maxConsecLosses = maxConsecLosses;
        }
    }
}
