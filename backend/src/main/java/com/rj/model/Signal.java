package com.rj.model;

/** Directional trading signal emitted by analysis and strategy layers. */
public enum Signal {
    BUY,
    SELL,
    HOLD;

    public boolean isDirectional() {
        return this == BUY || this == SELL;
    }

    public Signal opposite() {
        return switch (this) {
            case BUY -> SELL;
            case SELL -> BUY;
            case HOLD -> HOLD;
        };
    }
}
