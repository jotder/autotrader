package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.engine.BacktestService;
import com.rj.engine.CandleDatabase;
import com.rj.engine.StrategyAnalyzer;
import com.rj.model.Candle;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private final BacktestService backtestService;
    private final CandleDatabase candleDatabase;

    public BacktestController(BacktestService backtestService, CandleDatabase candleDatabase) {
        this.backtestService = backtestService;
        this.candleDatabase = candleDatabase;
    }

    @PostMapping("/jobs")
    public Map<String, String> createJob(@RequestBody Map<String, Object> request) {
        String symbol = (String) request.get("symbol");
        String from = (String) request.get("from");
        String to = (String) request.get("to");
        Map<String, List<Double>> sweeps = (Map<String, List<Double>>) request.get("sweeps");

        List<Candle> history = candleDatabase.loadRange(
                symbol, 
                LocalDate.parse(from), 
                LocalDate.parse(to)
        );
        
        if (history.isEmpty()) {
            throw new IllegalArgumentException("No history found for " + symbol + " in range " + from + " to " + to);
        }

        // BacktestEngine aggregates M1 to M5 automatically if needed, 
        // but it currently expects M5 history in constructor.
        // Let's ensure it's aggregated.
        List<Candle> m5history = com.rj.engine.BacktestEngine.aggregateToHigherTimeframe(history, 5);

        String jobId = backtestService.runIterativeBacktest(
                symbol, m5history, ConfigManager.getInstance().getRiskConfig(), sweeps);

        return Map.of("jobId", jobId);
    }

    @GetMapping("/jobs/{jobId}/results")
    public List<StrategyAnalyzer.Report> getResults(@PathVariable String jobId) {
        return backtestService.getResults(jobId);
    }
}
