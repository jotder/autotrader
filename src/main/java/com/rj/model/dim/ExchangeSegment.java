package com.rj.model.dim;

/**
 * Valid exchange–segment combination.
 * Source: {@code data/dim/exchange_segment.csv}
 */
public record ExchangeSegment(String exchange, String segment, int exchangeCode, int segmentCode) {}
