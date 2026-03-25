package com.rj.model;

/**
 * OMS order lifecycle states.
 *
 * <pre>
 * CREATED → SUBMITTED → ACCEPTED → FILLED
 *                     → REJECTED
 *                     → EXPIRED
 *           ACCEPTED  → PARTIALLY_FILLED → FILLED / CANCELLED
 *           ACCEPTED  → CANCELLED
 * </pre>
 */
public enum OrderState {
    CREATED,
    SUBMITTED,
    ACCEPTED,
    FILLED,
    PARTIALLY_FILLED,
    REJECTED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == FILLED || this == REJECTED
                || this == CANCELLED || this == EXPIRED;
    }
}
