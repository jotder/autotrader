package com.rj.model.dim;

/**
 * Order side lookup — 1=Buy, -1=Sell.
 * Source: {@code data/dim/order_sides.csv}
 */
public record OrderSide(int code, String name) {}
