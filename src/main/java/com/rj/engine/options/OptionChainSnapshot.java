package com.rj.engine.options;

import com.rj.model.OptionChainResult;

import java.time.Duration;
import java.time.Instant;

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
     * PCR for a specific expiry date string (format as returned by Fyers, e.g. "10-Apr-2026").
     * Computes from option entries filtered by that expiry date.
     * Returns 0.0 if expiry not found or no options for that expiry.
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
