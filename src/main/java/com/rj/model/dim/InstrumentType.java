package com.rj.model.dim;

/**
 * Instrument type lookup — maps Fyers instrument code to name and segment.
 * Source: {@code data/dim/instruments.csv}
 */
public record InstrumentType(int code, String name, String segmentShortName) {}
