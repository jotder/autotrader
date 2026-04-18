package com.rj.web.dto;

public record SizingResponse(
    boolean approved,
    int quantity,
    double stopLoss,
    double takeProfit,
    String rejectReason
) {}
