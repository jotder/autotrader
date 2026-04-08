package com.rj.engine.options;

import com.rj.model.OptionChainResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of an option chain for one underlying, captured at a point in time.
 *
 * <p>PCR (Put/Call Ratio) = totalPutOI / totalCallOI. Values > 1 suggest bearish sentiment.
 */
public record OptionChainSnapshot(
        String underlying,
        OptionChainResult data,
        Instant fetchedAt
) {
    public OptionChainSnapshot {
        Objects.requireNonNull(data, "data must not be null");
    }

    /**
     * Returns true if more than {@code threshold} time has passed since this snapshot was fetched.
     */
    public boolean isStale(Duration threshold) {
        return Duration.between(fetchedAt, Instant.now()).compareTo(threshold) > 0;
    }

    /**
     * Combined PCR across all expiries using the aggregate callOi/putOi from the result.
     * Returns 0.0 if callOi is zero.
     */
    public double pcr() {
        return data.callOi == 0 ? 0.0 : (double) data.putOi / data.callOi;
    }

    /**
     * Best-effort PCR for a specific expiry date. Note: Fyers option symbols do not embed the
     * expiry date string directly, so this method currently always returns 0.0.
     * For per-expiry analysis, use {@code data.expiryData} and {@code data.optionsChain} directly.
     * Use {@link #pcr()} (aggregate) for primary production use.
     */
    public double pcr(String expiryDate) {
        if (data.optionsChain == null || data.optionsChain.isEmpty()) return 0.0;
        long callOi = data.optionsChain.stream()
                .filter(e -> expiryDate.equals(expiryDateOf(e.symbol)) && "CE".equals(e.optionType))
                .mapToLong(e -> e.oi).sum();
        long putOi = data.optionsChain.stream()
                .filter(e -> expiryDate.equals(expiryDateOf(e.symbol)) && "PE".equals(e.optionType))
                .mapToLong(e -> e.oi).sum();
        return callOi == 0 ? 0.0 : (double) putOi / callOi;
    }

    /**
     * Extracts expiry date from a Fyers option symbol string.
     * For filtering per-expiry PCR — implementation note: Fyers expiry date in
     * {@code OptionChainResult.Expiry.date} is the canonical source; this helper
     * falls back gracefully if the format varies.
     */
    private static String expiryDateOf(String symbol) {
        // Symbol format: "NSE:NIFTY26APR22000CE" — expiry date not directly embedded.
        // Per-expiry PCR is best effort; use aggregate pcr() for primary use cases.
        return symbol; // caller should use expiryData list for date lookup
    }
}
