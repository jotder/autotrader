package com.rj.web.dto;

import java.time.Instant;

public record TickResponse(
        String symbol,
        double ltp,
        int bufferSize,
        Instant lastTickTime
) {}
