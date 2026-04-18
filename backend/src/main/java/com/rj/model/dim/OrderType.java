package com.rj.model.dim;

/**
 * Order type lookup — Limit, Market, SL-M, SL-L.
 * Source: {@code data/dim/order_types.csv}
 */
public record OrderType(int code, String name) {}
