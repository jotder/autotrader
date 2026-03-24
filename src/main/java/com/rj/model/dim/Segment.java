package com.rj.model.dim;

/**
 * Market segment lookup — maps Fyers segment code to name.
 * Source: {@code data/dim/segments.csv}
 */
public record Segment(int code, String name) {}
