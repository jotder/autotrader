package com.rj.model.dim;

/**
 * Holding type lookup — T1 (pending settlement), HLD (delivered).
 * Source: {@code data/dim/holding_types.csv}
 */
public record HoldingType(String code, String name) {}
