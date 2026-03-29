package com.rj.web.dto;

import com.rj.model.Confidence;

public record SizingRequest(
    String symbol,
    String strategyId,
    double entryPrice,
    double stopLoss,
    Confidence confidence,
    double atr
) {}
