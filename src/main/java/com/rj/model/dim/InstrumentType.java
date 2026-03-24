package com.rj.model.dim;

/**
 * Instrument type lookup — maps Fyers instrument code to name and segment.
 * Source: {@code data/dim/instrument_types.csv}
 */
public record InstrumentType(int code, String name, String segment) {}
