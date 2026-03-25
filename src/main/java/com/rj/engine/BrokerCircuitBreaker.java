package com.rj.engine;

import com.rj.config.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Circuit breaker for all broker API calls.
 * <p>
 * Implements the standard three-state pattern:
 * <pre>
 *   CLOSED ──(failures ≥ threshold)──→ OPEN ──(timeout)──→ HALF_OPEN
 *      ↑                                                       │
 *      └──────────(probe succeeds)─────────────────────────────┘
 *      HALF_OPEN ──(probe fails)──→ OPEN
 * </pre>
 *
 * <h3>Retry policy</h3>
 * <ul>
 *   <li>Up to N retries with exponential backoff + jitter</li>
 *   <li>Never retry 4xx except 429</li>
 *   <li>429 → increment daily counter, extra backoff, WARN</li>
 * </ul>
 *
 * <h3>Integration</h3>
 * Reports success/failure to {@link AnomalyDetector} for cascading
 * anomaly detection.
 */
public class BrokerCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(BrokerCircuitBreaker.class);
    private static final int MAX_JITTER_MS = 200;

    public enum State { CLOSED, OPEN, HALF_OPEN }

    // ── Config ──────────────────────────────────────────────────────────────
    private final CircuitBreakerConfig config;
    private final AnomalyDetector anomalyDetector; // nullable (paper/backtest modes)

    // ── State ───────────────────────────────────────────────────────────────
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicInteger daily429Count = new AtomicInteger(0);
    private volatile Instant lastFailureTime;
    private volatile Instant openedAt;

    public BrokerCircuitBreaker(CircuitBreakerConfig config, AnomalyDetector anomalyDetector) {
        this.config = config;
        this.anomalyDetector = anomalyDetector;
    }

    // ── Main entry point ────────────────────────────────────────────────────

    /**
     * Execute a broker API call through the circuit breaker.
     *
     * @param brokerCall the API call to execute
     * @param isCritical if true, attempts even when 429 daily limit reached
     * @param <T>        return type of the broker call
     * @return the result of the broker call
     * @throws CircuitBreakerOpenException if circuit is OPEN
     * @throws BrokerApiException          if all retries exhausted
     */
    public <T> T execute(Supplier<T> brokerCall, boolean isCritical) {
        // Check 429 daily limit for non-critical calls
        if (!isCritical && daily429Count.get() >= config.getMax429PerDay()) {
            log.warn("Circuit breaker: non-critical call blocked — daily 429 limit reached ({}/{})",
                    daily429Count.get(), config.getMax429PerDay());
            throw new BrokerApiException("Daily 429 rate limit reached — non-critical calls blocked");
        }

        State currentState = state.get();

        return switch (currentState) {
            case OPEN -> handleOpen(brokerCall, isCritical);
            case HALF_OPEN -> handleHalfOpen(brokerCall);
            case CLOSED -> handleClosed(brokerCall);
        };
    }

    // ── State handlers ──────────────────────────────────────────────────────

    private <T> T handleOpen(Supplier<T> brokerCall, boolean isCritical) {
        // Check if timeout has elapsed → transition to HALF_OPEN
        if (openedAt != null && Instant.now().isAfter(
                openedAt.plusSeconds(config.getOpenTimeoutSeconds()))) {
            if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                log.info("Circuit breaker: OPEN → HALF_OPEN (timeout elapsed)");
                consecutiveSuccesses.set(0);
                return handleHalfOpen(brokerCall);
            }
        }
        log.warn("Circuit breaker OPEN — fast-failing broker call");
        throw new CircuitBreakerOpenException("Circuit breaker is OPEN — broker API unavailable");
    }

    private <T> T handleHalfOpen(Supplier<T> brokerCall) {
        // Allow one probe call
        try {
            T result = brokerCall.get();
            onProbeSuccess();
            return result;
        } catch (Exception e) {
            onProbeFailure(e);
            throw wrapException(e);
        }
    }

    private <T> T handleClosed(Supplier<T> brokerCall) {
        return executeWithRetry(brokerCall);
    }

    // ── Retry loop ──────────────────────────────────────────────────────────

    private <T> T executeWithRetry(Supplier<T> brokerCall) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= config.getMaxRetries(); attempt++) {
            try {
                T result = brokerCall.get();
                onSuccess();
                return result;
            } catch (BrokerHttpException httpEx) {
                lastException = httpEx;
                int code = httpEx.getHttpCode();

                // 4xx (except 429): don't retry
                if (code >= 400 && code < 500 && code != 429) {
                    log.warn("Circuit breaker: broker returned {} — not retrying", code);
                    onFailure();
                    throw httpEx;
                }

                // 429: increment daily counter, extra backoff
                if (code == 429) {
                    int count = daily429Count.incrementAndGet();
                    log.warn("Circuit breaker: 429 rate limited — daily count: {}/{}",
                            count, config.getMax429PerDay());
                }

                log.warn("Circuit breaker: attempt {}/{} failed — code={}, retrying",
                        attempt, config.getMaxRetries(), code);
                backoff(attempt);

            } catch (Exception e) {
                lastException = e;
                log.warn("Circuit breaker: attempt {}/{} failed — {}, retrying",
                        attempt, config.getMaxRetries(), e.getMessage());
                backoff(attempt);
            }
        }

        // All retries exhausted
        onFailure();
        throw new BrokerApiException("All " + config.getMaxRetries() + " attempts failed",
                lastException);
    }

    // ── Success / failure tracking ──────────────────────────────────────────

    private void onSuccess() {
        consecutiveFailures.set(0);
        if (anomalyDetector != null) {
            anomalyDetector.recordBrokerSuccess();
        }
    }

    private void onFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime = Instant.now();

        if (anomalyDetector != null) {
            anomalyDetector.recordBrokerError();
        }

        // Trip to OPEN if threshold reached
        if (failures >= config.getFailureThreshold()) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                openedAt = Instant.now();
                log.error("Circuit breaker: CLOSED → OPEN — {} consecutive failures (threshold: {})",
                        failures, config.getFailureThreshold());
            }
        }
    }

    private void onProbeSuccess() {
        int successes = consecutiveSuccesses.incrementAndGet();
        consecutiveFailures.set(0);

        if (anomalyDetector != null) {
            anomalyDetector.recordBrokerSuccess();
        }

        if (successes >= config.getSuccessThreshold()) {
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                log.info("Circuit breaker: HALF_OPEN → CLOSED — {} consecutive probe successes",
                        successes);
                consecutiveSuccesses.set(0);
            }
        }
    }

    private void onProbeFailure(Exception e) {
        if (anomalyDetector != null) {
            anomalyDetector.recordBrokerError();
        }

        if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            openedAt = Instant.now();
            log.warn("Circuit breaker: HALF_OPEN → OPEN — probe failed: {}", e.getMessage());
        }
    }

    // ── Backoff ─────────────────────────────────────────────────────────────

    private void backoff(int attempt) {
        long delayMs = config.getBaseBackoffMs() * (1L << (attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(0, MAX_JITTER_MS);
        long totalMs = delayMs + jitter;
        try {
            Thread.sleep(totalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Day reset ───────────────────────────────────────────────────────────

    /** Reset daily counters (called at start of trading day). */
    public void resetDay() {
        daily429Count.set(0);
        log.info("Circuit breaker: daily 429 counter reset");
    }

    /** Force reset to CLOSED state (for manual intervention). */
    public void forceClose() {
        state.set(State.CLOSED);
        consecutiveFailures.set(0);
        consecutiveSuccesses.set(0);
        openedAt = null;
        log.warn("Circuit breaker: force-closed by operator");
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    public State getState() { return state.get(); }
    public int getConsecutiveFailures() { return consecutiveFailures.get(); }
    public int getDaily429Count() { return daily429Count.get(); }
    public Instant getLastFailureTime() { return lastFailureTime; }
    public Instant getOpenedAt() { return openedAt; }

    // ── Helper ──────────────────────────────────────────────────────────────

    private BrokerApiException wrapException(Exception e) {
        if (e instanceof BrokerApiException bae) return bae;
        return new BrokerApiException("Broker call failed: " + e.getMessage(), e);
    }

    // ── Exception types ─────────────────────────────────────────────────────

    /** Thrown when the circuit breaker is OPEN and fast-failing calls. */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) { super(message); }
    }

    /** Thrown for broker HTTP errors with status code tracking. */
    public static class BrokerHttpException extends RuntimeException {
        private final int httpCode;
        public BrokerHttpException(int httpCode, String message) {
            super(message);
            this.httpCode = httpCode;
        }
        public int getHttpCode() { return httpCode; }
    }

    /** General broker API exception (retries exhausted, etc.). */
    public static class BrokerApiException extends RuntimeException {
        public BrokerApiException(String message) { super(message); }
        public BrokerApiException(String message, Throwable cause) { super(message, cause); }
    }
}
