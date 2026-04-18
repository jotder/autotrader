package com.rj.model.dim;

/**
 * Order source lookup — channel from which the order originated.
 * Source: {@code data/dim/order_sources.csv}
 */
public record OrderSource(String code, String name) {}
