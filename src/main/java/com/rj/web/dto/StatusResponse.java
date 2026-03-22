package com.rj.web.dto;

import java.time.Instant;
import java.util.List;

public record StatusResponse(
        boolean running,
        String mode,
        List<String> symbols,
        Instant timestamp
) {}
