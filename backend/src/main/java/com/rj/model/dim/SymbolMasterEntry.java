package com.rj.model.dim;

/**
 * One row from a symbol master CSV (e.g. {@code data/symbol_master/NSE_CM.csv}).
 * <p>
 * Field order matches the Fyers symbol master schema (21 columns).
 * Reserved columns are kept as raw strings for forward-compatibility.
 */
public record SymbolMasterEntry(
        String fyToken,
        String symbolDetails,
        int exInstType,
        int minLotSize,
        double tickSize,
        String isin,
        String tradingSession,
        String lastUpdateDate,
        String expiryDate,
        String symbolTicker,
        int exchange,
        int segment,
        int scripCode,
        String underlyingSymbol,
        String underlyingScripCode,
        double strikePrice,
        String optionType,
        String underlyingFyToken,
        String reserved1,
        String reserved2,
        String reserved3
) {}
