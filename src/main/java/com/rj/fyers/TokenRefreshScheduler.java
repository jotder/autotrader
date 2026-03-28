package com.rj.fyers;

import com.rj.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Background scheduler that refreshes the Fyers access token before it expires.
 * <p>
 * Uses the refresh token (stored in {@code .env}) to obtain a new access token
 * via {@link TokenGenerator#generateTokenFromRefreshToken}. After refresh,
 * updates {@code .env} and forces {@link FyersClientFactory} to re-read the
 * new token on the next API call.
 * <p>
 * Default schedule: first refresh at 6 hours after start, then every 6 hours.
 * Configurable via {@code FYERS_TOKEN_REFRESH_INTERVAL_MINUTES} in .env.
 * <p>
 * Retry policy: 3 attempts with exponential backoff (30s, 60s, 120s).
 */
public class TokenRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshScheduler.class);

    private static final int DEFAULT_REFRESH_INTERVAL_MINUTES = 360; // 6 hours
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {30_000, 60_000, 120_000};

    private final ConfigManager config;
    private final TokenGenerator tokenGenerator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastRefreshTime = new AtomicReference<>();
    private final AtomicReference<String> lastRefreshStatus = new AtomicReference<>("never");

    private ScheduledExecutorService scheduler;

    public TokenRefreshScheduler(ConfigManager config) {
        this.config = config;
        this.tokenGenerator = new TokenGenerator();
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    public void start() {
        if (!running.compareAndSet(false, true)) return;

        int intervalMinutes = getRefreshInterval();
        boolean autoRefresh = !"false".equalsIgnoreCase(config.getProperty("FYERS_TOKEN_AUTO_REFRESH"));

        if (!autoRefresh) {
            log.info("[TokenRefresh] Auto-refresh disabled (FYERS_TOKEN_AUTO_REFRESH=false)");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("token-refresh").factory());

        scheduler.scheduleAtFixedRate(this::refreshCycle,
                intervalMinutes, intervalMinutes, TimeUnit.MINUTES);

        log.info("[TokenRefresh] Scheduled every {} minutes (first refresh at {}m after start)",
                intervalMinutes, intervalMinutes);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (scheduler != null) {
            scheduler.shutdownNow();
            log.info("[TokenRefresh] Stopped");
        }
    }

    // ── Refresh logic ───────────────────────────────────────────────────────

    /**
     * Execute one refresh cycle with retry.
     * Can be called manually (e.g., from REST endpoint) or by the scheduler.
     *
     * @return true if refresh succeeded
     */
    public boolean refreshNow() {
        String refreshToken = config.getProperty("REFRESH_TOKEN");
        String pin = config.getProperty("FYERS_PIN");
        String appId = config.getProperty("FYERS_APP_ID");
        String secretKey = config.getProperty("FYERS_SECRET_KEY");

        if (refreshToken == null || refreshToken.isBlank()) {
            log.error("[TokenRefresh] No REFRESH_TOKEN in .env — cannot auto-refresh");
            lastRefreshStatus.set("failed: no refresh token");
            return false;
        }
        if (pin == null || pin.isBlank()) {
            log.error("[TokenRefresh] No FYERS_PIN in .env — cannot auto-refresh");
            lastRefreshStatus.set("failed: no pin");
            return false;
        }

        String appHashId = computeAppHash(appId, secretKey);
        if (appHashId == null) {
            lastRefreshStatus.set("failed: cannot compute app hash");
            return false;
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("[TokenRefresh] Attempt {}/{} ...", attempt, MAX_RETRIES);
                String newToken = tokenGenerator.generateTokenFromRefreshToken(appHashId, refreshToken, pin);

                if (newToken != null && !newToken.isBlank()) {
                    // Force FyersClientFactory to pick up new token
                    FyersClientFactory.refreshToken(newToken);

                    lastRefreshTime.set(Instant.now());
                    lastRefreshStatus.set("success");
                    log.info("[TokenRefresh] Token refreshed successfully (attempt {})", attempt);
                    return true;
                }

                log.warn("[TokenRefresh] Attempt {}/{} returned null token", attempt, MAX_RETRIES);

            } catch (Exception e) {
                log.warn("[TokenRefresh] Attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
            }

            // Backoff before retry (skip on last attempt)
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAYS_MS[attempt - 1]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        lastRefreshStatus.set("failed: all retries exhausted");
        log.error("[TokenRefresh] All {} attempts failed — token NOT refreshed. " +
                "Manual intervention required.", MAX_RETRIES);
        return false;
    }

    // ── Status ──────────────────────────────────────────────────────────────

    public boolean isRunning() { return running.get(); }
    public Instant getLastRefreshTime() { return lastRefreshTime.get(); }
    public String getLastRefreshStatus() { return lastRefreshStatus.get(); }

    // ── Internal ────────────────────────────────────────────────────────────

    private void refreshCycle() {
        try {
            refreshNow();
        } catch (Exception e) {
            log.error("[TokenRefresh] Unexpected error in refresh cycle", e);
        }
    }

    private int getRefreshInterval() {
        String intervalStr = config.getProperty("FYERS_TOKEN_REFRESH_INTERVAL_MINUTES");
        if (intervalStr != null && !intervalStr.isBlank()) {
            try {
                int val = Integer.parseInt(intervalStr.trim());
                if (val > 0) return val;
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_REFRESH_INTERVAL_MINUTES;
    }

    /**
     * Compute SHA-256 hash of appId:secretKey (Fyers auth requirement).
     */
    static String computeAppHash(String appId, String secretKey) {
        if (appId == null || secretKey == null) {
            log.error("[TokenRefresh] FYERS_APP_ID or FYERS_SECRET_KEY not set");
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((appId + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("[TokenRefresh] Failed to compute app hash", e);
            return null;
        }
    }
}
