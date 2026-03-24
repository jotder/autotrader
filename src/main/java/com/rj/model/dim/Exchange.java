package com.rj.model.dim;

/**
 * Exchange lookup — maps Fyers exchange code to name.
 * Source: {@code data/dim/exchanges.csv}
 */
public record Exchange(int code, String name) {}
