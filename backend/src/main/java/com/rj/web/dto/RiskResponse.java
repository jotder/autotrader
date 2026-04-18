package com.rj.web.dto;

public record RiskResponse(
        double dailyRealizedPnl,
        boolean killSwitchActive,
        boolean dailyProfitLocked,
        double maxDailyLossInr,
        double maxDailyProfitInr,
        double initialCapitalInr
) {
}
