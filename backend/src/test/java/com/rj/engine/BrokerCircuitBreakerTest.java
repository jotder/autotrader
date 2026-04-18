package com.rj.engine;

import com.rj.config.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BrokerCircuitBreakerTest {

    private BrokerCircuitBreaker cb;

    @BeforeEach
    void setup() {
        // Fast config for tests: 3 failures to open, 1 success to close, 1s timeout, 2 retries, 1ms backoff
        var config = new CircuitBreakerConfig(3, 1, 1, 2, 1L, 2);
        cb = new BrokerCircuitBreaker(config, null); // no anomaly detector in tests
    }

    @Test
    void successfulCallPassesThrough() {
        String result = cb.execute(() -> "ok", false);
        assertEquals("ok", result);
        assertEquals(BrokerCircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getConsecutiveFailures());
    }

    @Test
    void failuresTripsToOpen() {
        // Each execute() call increments consecutiveFailures once after retries exhausted
        // Threshold is 3, so need 3 failing calls to trip
        for (int i = 0; i < 3; i++) {
            assertThrows(BrokerCircuitBreaker.BrokerApiException.class, () ->
                    cb.execute(() -> { throw new RuntimeException("fail"); }, false));
        }
        assertEquals(BrokerCircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void openStateFailsFast() {
        // Trip the circuit
        tripCircuit();

        assertEquals(BrokerCircuitBreaker.State.OPEN, cb.getState());

        // Next call should fast-fail with CircuitBreakerOpenException
        assertThrows(BrokerCircuitBreaker.CircuitBreakerOpenException.class, () ->
                cb.execute(() -> "should not run", false));
    }

    @Test
    void openTransitionsToHalfOpenAfterTimeout() throws InterruptedException {
        tripCircuit();
        assertEquals(BrokerCircuitBreaker.State.OPEN, cb.getState());

        // Wait for timeout (1 second)
        Thread.sleep(1200);

        // Next call should trigger HALF_OPEN → probe
        String result = cb.execute(() -> "probe-ok", false);
        assertEquals("probe-ok", result);
        // With successThreshold=1, one success → CLOSED
        assertEquals(BrokerCircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void halfOpenFailureGoesBackToOpen() throws InterruptedException {
        tripCircuit();

        Thread.sleep(1200);

        // Probe call fails → back to OPEN
        assertThrows(BrokerCircuitBreaker.BrokerApiException.class, () ->
                cb.execute(() -> { throw new RuntimeException("probe-fail"); }, false));
        assertEquals(BrokerCircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void httpErrorNotRetried() {
        // 4xx errors (except 429) should not be retried
        AtomicInteger callCount = new AtomicInteger(0);
        assertThrows(BrokerCircuitBreaker.BrokerHttpException.class, () ->
                cb.execute(() -> {
                    callCount.incrementAndGet();
                    throw new BrokerCircuitBreaker.BrokerHttpException(403, "Forbidden");
                }, false));
        // Should only be called once (no retry for 4xx)
        assertEquals(1, callCount.get());
    }

    @Test
    void http429IsRetried() {
        AtomicInteger callCount = new AtomicInteger(0);
        assertThrows(BrokerCircuitBreaker.BrokerApiException.class, () ->
                cb.execute(() -> {
                    callCount.incrementAndGet();
                    throw new BrokerCircuitBreaker.BrokerHttpException(429, "Rate limited");
                }, false));
        // Should retry (2 attempts per config)
        assertEquals(2, callCount.get());
    }

    @Test
    void daily429Tracking() {
        assertEquals(0, cb.getDaily429Count());

        // Trigger 429 responses
        try {
            cb.execute(() -> {
                throw new BrokerCircuitBreaker.BrokerHttpException(429, "Rate limited");
            }, false);
        } catch (Exception ignored) {}

        // Each retry attempt with 429 increments counter: 2 retries = 2 increments
        assertTrue(cb.getDaily429Count() > 0);
    }

    @Test
    void nonCriticalBlockedAt429Limit() {
        // Manually set up 429 limit (max429PerDay=2 in test config)
        // Trigger enough 429s to reach limit
        for (int i = 0; i < 3; i++) {
            try {
                cb.execute(() -> {
                    throw new BrokerCircuitBreaker.BrokerHttpException(429, "Rate limited");
                }, true); // critical → not blocked by 429 limit
            } catch (Exception ignored) {}
        }

        // Non-critical call should be blocked now
        assertThrows(BrokerCircuitBreaker.BrokerApiException.class, () ->
                cb.execute(() -> "should be blocked", false));
    }

    @Test
    void criticalCallsNotBlockedBy429Limit() {
        // Exceed 429 limit
        for (int i = 0; i < 3; i++) {
            try {
                cb.execute(() -> {
                    throw new BrokerCircuitBreaker.BrokerHttpException(429, "Rate limited");
                }, true);
            } catch (Exception ignored) {}
        }

        // Critical call should still attempt (if circuit is closed)
        // Force close first since failures may have tripped it
        cb.forceClose();
        String result = cb.execute(() -> "critical-ok", true);
        assertEquals("critical-ok", result);
    }

    @Test
    void forceCloseResetsState() {
        tripCircuit();
        assertEquals(BrokerCircuitBreaker.State.OPEN, cb.getState());

        cb.forceClose();
        assertEquals(BrokerCircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getConsecutiveFailures());
    }

    @Test
    void resetDayClears429Counter() {
        try {
            cb.execute(() -> {
                throw new BrokerCircuitBreaker.BrokerHttpException(429, "Rate limited");
            }, true);
        } catch (Exception ignored) {}

        assertTrue(cb.getDaily429Count() > 0);

        cb.resetDay();
        assertEquals(0, cb.getDaily429Count());
    }

    @Test
    void retrySucceedsOnSecondAttempt() {
        AtomicInteger callCount = new AtomicInteger(0);
        String result = cb.execute(() -> {
            if (callCount.incrementAndGet() == 1) {
                throw new RuntimeException("first attempt fails");
            }
            return "second attempt ok";
        }, false);

        assertEquals("second attempt ok", result);
        assertEquals(2, callCount.get());
        assertEquals(BrokerCircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void anomalyDetectorWired() {
        // Create CB with a mock anomaly detector
        var config = new CircuitBreakerConfig(3, 1, 1, 1, 1L, 2);
        var mockDetector = new MockAnomalyDetector();
        var cbWithDetector = new BrokerCircuitBreaker(config, mockDetector);

        // Success call
        cbWithDetector.execute(() -> "ok", false);
        assertEquals(1, mockDetector.successCount.get());
        assertEquals(0, mockDetector.errorCount.get());

        // Failure call
        try {
            cbWithDetector.execute(() -> { throw new RuntimeException("fail"); }, false);
        } catch (Exception ignored) {}
        assertEquals(1, mockDetector.errorCount.get());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void tripCircuit() {
        for (int i = 0; i < 5; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("fail"); }, false);
            } catch (Exception ignored) {}
        }
    }

    /** Minimal stand-in for AnomalyDetector that just counts calls. */
    private static class MockAnomalyDetector extends AnomalyDetector {
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);

        MockAnomalyDetector() {
            super();
        }

        @Override
        public void recordBrokerSuccess() {
            successCount.incrementAndGet();
        }

        @Override
        public void recordBrokerError() {
            errorCount.incrementAndGet();
        }
    }
}
