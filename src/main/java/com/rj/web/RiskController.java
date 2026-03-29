package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.engine.RiskManager;
import com.rj.engine.TradingEngine;
import com.rj.model.Confidence;
import com.rj.model.TradeSignal;
import com.rj.web.dto.SizingRequest;
import com.rj.web.dto.SizingResponse;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for pre-trade calculations and risk parameter management.
 */
@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final TradingEngine engine;

    public RiskController(TradingEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/calculate-sizing")
    public SizingResponse calculateSizing(@RequestBody SizingRequest request) {
        // Construct a dummy signal for the calculator
        TradeSignal dummySignal = TradeSignal.builder()
                .symbol(request.symbol())
                .strategyId(request.strategyId())
                .suggestedEntry(request.entryPrice())
                .suggestedStopLoss(request.stopLoss())
                .suggestedTarget(request.entryPrice() * 1.02)
                .confidenceLevel(request.confidence())
                .atr(request.atr() > 0 ? request.atr() : request.entryPrice() * 0.01)
                .build();

        RiskManager.PreTradeResult result = engine.getRiskManager().preTradeCheck(
                dummySignal, 
                engine.getPositionMonitor().openPositions(),
                ConfigManager.getInstance().getRiskConfig().getInitialCapitalInr()
        );

        return new SizingResponse(
                result.approved(),
                result.quantity(),
                result.stopLoss(),
                result.takeProfit(),
                result.rejectReason()
        );
    }
}
