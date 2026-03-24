package com.rj.model;

import java.time.Month;

/**
 * Decomposed Fyers symbol — the result of parsing a symbol string like
 * {@code NSE:NIFTY26MAR11000CE} into structured parts.
 *
 * @param rawSymbol     original symbol string
 * @param type          classified symbol type
 * @param exchange      exchange code (NSE, BSE, MCX)
 * @param underlying    underlying symbol (SBIN, NIFTY, USDINR, CRUDEOIL, etc.)
 * @param series        series code for equity only (EQ, BE, A, T, etc.); null for derivatives
 * @param year          2-digit year expanded to 4 digits (e.g. 2026); null for equity
 * @param month         expiry month for monthly/futures; null for weekly
 * @param monthCode     single-char month code for weekly expiry (1-9, O, N, D); null otherwise
 * @param dayOfMonth    expiry day for weekly expiry; null otherwise
 * @param strikePrice   strike price for options; null for equity/futures
 * @param optionType    CE or PE for options; null otherwise
 */
public record ParsedSymbol(
        String rawSymbol,
        SymbolType type,
        String exchange,
        String underlying,
        String series,
        Integer year,
        Month month,
        Character monthCode,
        Integer dayOfMonth,
        Double strikePrice,
        String optionType
) {
    public boolean isDerivative() { return type.isDerivative(); }
    public boolean isOption() { return type.isOption(); }
    public boolean isFuture() { return type.isFuture(); }
    public boolean isWeekly() { return type.isWeekly(); }

    /**
     * Human-readable expiry label, e.g. "26MAR", "26O08", "26DEC".
     * Returns null for equity.
     */
    public String expiryLabel() {
        if (year == null) return null;
        int yy = year % 100;
        // Weekly options: use single-char month code + day
        if (monthCode != null && dayOfMonth != null) {
            return String.format("%02d%c%02d", yy, monthCode, dayOfMonth);
        }
        // Monthly/futures: use 3-letter month abbreviation
        if (month != null) {
            return String.format("%02d%s", yy, month.name().substring(0, 3));
        }
        return null;
    }
}
