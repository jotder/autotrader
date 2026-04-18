package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.Candle;
import com.rj.strategy.ITradeStrategy;
import com.rj.strategy.MultiTimeframeVotingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage iterative backtest jobs and parameter tuning.
 */
@Service
public class BacktestService {
    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);

    private final Map<String, List<StrategyAnalyzer.Report>> jobResults = new ConcurrentHashMap<>();

    public String runIterativeBacktest(String symbol, List<Candle> history, RiskConfig baseRisk, 
                                      Map<String, List<Double>> parameterSweeps) {
        String jobId = UUID.randomUUID().toString();
        List<StrategyAnalyzer.Report> results = new ArrayList<>();
        jobResults.put(jobId, results);

        // Run in virtual thread
        Thread.startVirtualThread(() -> {
            log.info("[BT-SERVICE] Starting iterative job: {}", jobId);
            
            // For simplicity in Phase-II, we'll sweep slAtrMultiplier and tpRMultiple
            List<Double> slSweeps = parameterSweeps.getOrDefault("slAtrMultiplier", List.of(2.0));
            List<Double> tpSweeps = parameterSweeps.getOrDefault("tpRMultiple", List.of(2.0));

            for (Double sl : slSweeps) {
                for (Double tp : tpSweeps) {
                    ITradeStrategy strategy = new MultiTimeframeVotingStrategy(
                            "tuned_tf", "Tuned [SL=" + sl + ", TP=" + tp + "]", 0.70, sl, tp);
                    
                    BacktestEngine engine = new BacktestEngine(history, symbol, strategy, baseRisk);
                    StrategyAnalyzer.Report report = engine.run();
                    results.add(report);
                    
                    log.info("[BT-SERVICE] Job {} run complete: SL={}, TP={} -> PnL={}", 
                            jobId, sl, tp, report.overall().totalPnl);
                }
            }
            log.info("[BT-SERVICE] Job {} finished all {} runs", jobId, results.size());
        });

        return jobId;
    }

    public List<StrategyAnalyzer.Report> getResults(String jobId) {
        return jobResults.getOrDefault(jobId, List.of());
    }
}
