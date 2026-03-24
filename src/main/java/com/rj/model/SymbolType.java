package com.rj.model;

/**
 * Classification of Fyers symbol formats.
 * Derived from {@code config/symbol_format.yaml}.
 */
public enum SymbolType {
    EQUITY,
    EQUITY_FUTURE,
    EQUITY_OPTION_MONTHLY,
    EQUITY_OPTION_WEEKLY,
    CURRENCY_FUTURE,
    CURRENCY_OPTION_MONTHLY,
    CURRENCY_OPTION_WEEKLY,
    COMMODITY_FUTURE,
    COMMODITY_OPTION_MONTHLY;

    public boolean isDerivative() {
        return this != EQUITY;
    }

    public boolean isOption() {
        return switch (this) {
            case EQUITY_OPTION_MONTHLY, EQUITY_OPTION_WEEKLY,
                 CURRENCY_OPTION_MONTHLY, CURRENCY_OPTION_WEEKLY,
                 COMMODITY_OPTION_MONTHLY -> true;
            default -> false;
        };
    }

    public boolean isFuture() {
        return switch (this) {
            case EQUITY_FUTURE, CURRENCY_FUTURE, COMMODITY_FUTURE -> true;
            default -> false;
        };
    }

    public boolean isWeekly() {
        return this == EQUITY_OPTION_WEEKLY || this == CURRENCY_OPTION_WEEKLY;
    }
}
