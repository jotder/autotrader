package com.rj.model;

import java.util.List;

/**
 * Model for symbol naming patterns based on instrument type.
 * Source: {@code config/symbol_format.yaml}
 */
public record SymbologyFormat(
    String instrumentName,
    String formatPattern,
    List<String> examples
) {}
