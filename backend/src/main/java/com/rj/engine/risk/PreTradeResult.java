package com.rj.engine.risk;

public record PreTradeResult(
        boolean approved,
        int     quantity,
        double  stopLoss,
        double  takeProfit,
        String  rejectReason) {
}
