package com.rj.fyers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenRefreshSchedulerTest {

    @Test
    void computeAppHashProducesDeterministicSha256() {
        String hash1 = TokenRefreshScheduler.computeAppHash("myAppId", "mySecret");
        String hash2 = TokenRefreshScheduler.computeAppHash("myAppId", "mySecret");
        assertNotNull(hash1);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 hex = 64 chars
    }

    @Test
    void computeAppHashDiffersForDifferentInputs() {
        String hash1 = TokenRefreshScheduler.computeAppHash("app1", "secret1");
        String hash2 = TokenRefreshScheduler.computeAppHash("app2", "secret2");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computeAppHashReturnsNullForNullInputs() {
        assertNull(TokenRefreshScheduler.computeAppHash(null, "secret"));
        assertNull(TokenRefreshScheduler.computeAppHash("app", null));
    }

    @Test
    void computeAppHashMatchesExpectedFormat() {
        String hash = TokenRefreshScheduler.computeAppHash("testApp", "testSecret");
        assertNotNull(hash);
        // SHA-256 hex string: only lowercase hex chars
        assertTrue(hash.matches("^[0-9a-f]{64}$"));
    }
}
