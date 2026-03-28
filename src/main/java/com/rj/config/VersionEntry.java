package com.rj.config;

import java.time.Instant;

/**
 * Represents a single version entry in a strategy's history.
 */
public record VersionEntry(
    int version,
    VersionState state,
    Instant createdAt,
    String note
) {}
