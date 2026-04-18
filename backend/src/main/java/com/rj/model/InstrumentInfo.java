package com.rj.model;

/**
 * Metadata about a traded instrument — carries symbol type, product type,
 * lot size, tick size, and segment information needed for correct order
 * placement and risk sizing.
 *
 * <h3>Product type mapping (Fyers)</h3>
 * <ul>
 *   <li>Equity (CM segment): INTRADAY or CNC</li>
 *   <li>Futures/Options (FO/CD/COM segments): MARGIN</li>
 * </ul>
 *
 * <h3>Lot size</h3>
 * <ul>
 *   <li>Equity: 1 (share-level granularity)</li>
 *   <li>Derivatives: exchange-defined lot size (e.g., NIFTY = 25, BANKNIFTY = 15)</li>
 * </ul>
 */
public record InstrumentInfo(
        SymbolType symbolType,
        String productType,
        int lotSize,
        double tickSize,
        String segment
) {
    /** Default for equity instruments. */
    public static final InstrumentInfo EQUITY_DEFAULT =
            new InstrumentInfo(SymbolType.EQUITY, "INTRADAY", 1, 0.05, "CM");

    /**
     * Create instrument info for a derivative with specified lot size.
     */
    public static InstrumentInfo derivative(SymbolType type, int lotSize, String segment) {
        return new InstrumentInfo(type, "MARGIN", lotSize, 0.05, segment);
    }

    /**
     * Create instrument info from a symbol type with a given lot size.
     * Automatically maps product type based on whether the symbol is a derivative.
     */
    public static InstrumentInfo fromType(SymbolType type, int lotSize) {
        if (type == null || type == SymbolType.EQUITY) {
            return EQUITY_DEFAULT;
        }
        String segment = segmentFor(type);
        return new InstrumentInfo(type, "MARGIN", Math.max(lotSize, 1), 0.05, segment);
    }

    /** Whether this instrument is a derivative (futures or options). */
    public boolean isDerivative() {
        return symbolType != null && symbolType.isDerivative();
    }

    /** Whether this instrument is an option. */
    public boolean isOption() {
        return symbolType != null && symbolType.isOption();
    }

    /** Whether this instrument is a future. */
    public boolean isFuture() {
        return symbolType != null && symbolType.isFuture();
    }

    private static String segmentFor(SymbolType type) {
        return switch (type) {
            case EQUITY -> "CM";
            case EQUITY_FUTURE, EQUITY_OPTION_MONTHLY, EQUITY_OPTION_WEEKLY -> "FO";
            case CURRENCY_FUTURE, CURRENCY_OPTION_MONTHLY, CURRENCY_OPTION_WEEKLY -> "CD";
            case COMMODITY_FUTURE, COMMODITY_OPTION_MONTHLY -> "COM";
        };
    }
}
