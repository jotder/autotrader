package com.rj.config;

import java.util.function.Function;

/**
 * Configuration for the broker API circuit breaker.
 * Loaded from environment variables with {@code CIRCUIT_BREAKER_} prefix.
 */
public class CircuitBreakerConfig {

    private final int failureThreshold;
    private final int successThreshold;
    private final int openTimeoutSeconds;
    private final int maxRetries;
    private final long baseBackoffMs;
    private final int max429PerDay;

    public CircuitBreakerConfig(int failureThreshold, int successThreshold,
                                int openTimeoutSeconds, int maxRetries,
                                long baseBackoffMs, int max429PerDay) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.openTimeoutSeconds = openTimeoutSeconds;
        this.maxRetries = maxRetries;
        this.baseBackoffMs = baseBackoffMs;
        this.max429PerDay = max429PerDay;
    }

    public static CircuitBreakerConfig defaults() {
        return new CircuitBreakerConfig(5, 2, 30, 3, 500L, 3);
    }

    public static CircuitBreakerConfig fromEnvironment(Function<String, String> envReader) {
        CircuitBreakerConfig d = defaults();
        return new CircuitBreakerConfig(
                parseInt(envReader.apply("CIRCUIT_BREAKER_FAILURE_THRESHOLD"), d.failureThreshold),
                parseInt(envReader.apply("CIRCUIT_BREAKER_SUCCESS_THRESHOLD"), d.successThreshold),
                parseInt(envReader.apply("CIRCUIT_BREAKER_OPEN_TIMEOUT_SECONDS"), d.openTimeoutSeconds),
                parseInt(envReader.apply("CIRCUIT_BREAKER_MAX_RETRIES"), d.maxRetries),
                parseLong(envReader.apply("CIRCUIT_BREAKER_BASE_BACKOFF_MS"), d.baseBackoffMs),
                parseInt(envReader.apply("CIRCUIT_BREAKER_MAX_429_PER_DAY"), d.max429PerDay));
    }

    private static int parseInt(String val, int fallback) {
        if (val == null || val.isBlank()) return fallback;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static long parseLong(String val, long fallback) {
        if (val == null || val.isBlank()) return fallback;
        try { return Long.parseLong(val.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    public int getFailureThreshold() { return failureThreshold; }
    public int getSuccessThreshold() { return successThreshold; }
    public int getOpenTimeoutSeconds() { return openTimeoutSeconds; }
    public int getMaxRetries() { return maxRetries; }
    public long getBaseBackoffMs() { return baseBackoffMs; }
    public int getMax429PerDay() { return max429PerDay; }
}
