package com.rj.model.dim;

/**
 * Product type lookup — CNC, INTRADAY, MARGIN, CO, BO, MTF.
 * Source: {@code data/dim/product_types.csv}
 */
public record ProductType(String code, String name) {}
