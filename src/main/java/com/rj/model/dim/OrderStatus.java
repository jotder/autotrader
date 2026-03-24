package com.rj.model.dim;

/**
 * Order status lookup — maps Fyers status code to name.
 * Source: {@code data/dim/order_status.csv}
 */
public record OrderStatus(int code, String name) {}
